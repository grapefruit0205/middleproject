#!/usr/bin/env python3
"""Deterministic plugin-local validator for the trip-copilot prompt evaluation set.

Checks the plan-business-trip SKILL.md, the tool catalog contract, and the backend MCP
adapter source against the evaluation set. It never calls external AI services, the MCP
server, or the network; every case is checked against repository files only, and every
failure is meaningful (a concrete expected contract fact that is absent from the files).

The tool catalog (names, nonblank descriptions, and the four annotation hints) is NOT
duplicated here: it is parsed directly out of backend/.../McpAdapterController.java so a
check can only pass when the Java source actually contains the contract fact. There is no
second independent truth table that could drift from the adapter.

Check kinds supported by the evaluation set:

- `skill-text: <phrase>`      — the phrase must occur in skills/plan-business-trip/SKILL.md
- `mcp-source: <pattern>`     — the regex (case-insensitive) must match the backend
                                McpAdapterController.java source
- `catalog: <name>`           — the named tool must be present in the MCP tool catalog
                                with a nonblank top-level description
- `catalog-annotation: <name> <field>=<value>` — the named tool's annotation field must
                                equal the given boolean value (e.g. `get_reminder
                                readOnlyHint=true`). The annotation values are derived from
                                the READ_ONLY_TOOLS/DESTRUCTIVE_TOOLS/IDEMPOTENT_TOOLS/
                                OPEN_WORLD_TOOLS sets and the idempotentHint expression in
                                McpAdapterController.java, so a wrong Java classification
                                fails the check.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

PLUGIN_ROOT = Path(__file__).resolve().parents[1]
SKILL_MD = PLUGIN_ROOT / "skills" / "plan-business-trip" / "SKILL.md"
EVALUATION_SET = PLUGIN_ROOT / "tests" / "prompt-evaluation" / "evaluation-set.json"
# Backend MCP adapter source: the single source of truth for the tool catalog contract.
MCP_CONTROLLER = PLUGIN_ROOT.parents[1] / "backend" / "src" / "main" / "java" / "com" / "middleproject" / "reminder" / "web" / "McpAdapterController.java"
# Backend main sources: provenance/consent facts live in the domain and application layers,
# not in the thin adapter; mcp-source checks scan this tree so failures stay meaningful.
BACKEND_MAIN = PLUGIN_ROOT.parents[1] / "backend" / "src" / "main" / "java" / "com" / "middleproject" / "reminder"

# MCP standard annotation field names required on every tools/list entry.
ANNOTATION_FIELDS = ("title", "readOnlyHint", "destructiveHint", "idempotentHint", "openWorldHint")

# Regexes that must each appear in the backend adapter source. Keyed so a failure names
# the contract fact that is missing.
SOURCE_CONTRACT_FACTS = {
    "fixed-owner-audit": r"audit\.record\(toolName",
    "demo-owner-reminder-scope": r'reminders\.create\(UUID\.fromString\(a\.get\("eventId"\)',
    "closed-schema-additionalProperties-false": r'additionalProperties",\s*false',
    "idempotency-key-validation": r'idempotencyKey".*minLength',
    "text-and-structured-content-result": r'put\("type",\s*"text"\)',
    "proposal-id-sha256-pattern": r'PROPOSAL_ID_PATTERN',
    # readOnlyHint and idempotentHint must be distinct expressions: idempotentHint is the
    # union of the read-only set and the replayable-write set (see parse_catalog below).
    "idempotent-hint-union-expression": r'idempotentHint",\s*READ_ONLY_TOOLS\.contains\(name\)\s*\|\|\s*IDEMPOTENT_TOOLS\.contains\(name\)',
    "read-only-hint-expression": r'readOnlyHint",\s*READ_ONLY_TOOLS\.contains\(name\)',
    "destructive-hint-expression": r'destructiveHint",\s*DESTRUCTIVE_TOOLS\.contains\(name\)',
    "open-world-hint-expression": r'openWorldHint",\s*OPEN_WORLD_TOOLS\.contains\(name\)',
}


def load_evaluation_set() -> dict:
    payload = json.loads(EVALUATION_SET.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or not isinstance(payload.get("cases"), list):
        raise ValueError("evaluation-set.json must contain a 'cases' list")
    return payload


def checks(expectations: list[str]) -> list[tuple[str, str]]:
    """Materialize one check per expected contract statement."""
    out = []
    for raw in expectations:
        m = re.fullmatch(r"([a-z-]+): (.*)", raw.strip())
        if not m:
            raise ValueError(f"invalid check in evaluation set: {raw!r}")
        out.append((m.group(1), m.group(2)))
    return out


def parse_java_string_list(source: str, name: str) -> set[str]:
    """Parse a Java `Set<String> NAME = Set.of("a", "b", ...)` literal."""
    m = re.search(r'Set<String>\s+' + name + r'\s*=\s*Set\.of\((.*?)\);', source, re.DOTALL)
    if not m:
        raise ValueError(f"could not parse {name} from McpAdapterController.java")
    return set(re.findall(r'"([^"]+)"', m.group(1)))


def parse_java_string_map(source: str, name: str) -> dict[str, str]:
    """Parse a Java `Map<String, String> NAME = Map.ofEntries(Map.entry("tool", "desc"), ...)`."""
    m = re.search(r'Map<String,\s*String>\s+' + name + r'\s*=\s*Map\.ofEntries\((.*?)\);', source, re.DOTALL)
    if not m:
        raise ValueError(f"could not parse {name} from McpAdapterController.java")
    entries = re.findall(r'Map\.entry\("([^"]+)",\s*"([^"]*)"\)', m.group(1))
    return {tool: desc for tool, desc in entries}


def parse_java_ordered_list(source: str, name: str) -> list[str]:
    """Parse a Java `List.of("a", "b", ...)` literal, preserving order."""
    m = re.search(r'List<String>\s+' + name + r'\s*=\s*List\.of\((.*?)\);', source, re.DOTALL)
    if not m:
        raise ValueError(f"could not parse {name} from McpAdapterController.java")
    return re.findall(r'"([^"]+)"', m.group(1))


def parse_catalog(source: str) -> tuple[list[str], dict[str, str], dict[str, tuple[bool, bool, bool, bool]]]:
    """Derive the tool catalog from McpAdapterController.java exactly as tools/list emits it.

    Returns (ordered tool names, name -> top-level description, name -> (readOnlyHint,
    destructiveHint, idempotentHint, openWorldHint)). The idempotent hint is read from the
    exact expression `READ_ONLY_TOOLS.contains(name) || IDEMPOTENT_TOOLS.contains(name)`,
    so a regression that drops read-only tools from the idempotent set fails every
    read-only idempotency check.
    """
    names = parse_java_ordered_list(source, "TOOL_NAMES")
    descriptions = parse_java_string_map(source, "TOOL_DESCRIPTIONS")
    read_only = parse_java_string_list(source, "READ_ONLY_TOOLS")
    destructive = parse_java_string_list(source, "DESTRUCTIVE_TOOLS")
    idempotent_writes = parse_java_string_list(source, "IDEMPOTENT_TOOLS")
    open_world = parse_java_string_list(source, "OPEN_WORLD_TOOLS")

    # The union expression is the contract for idempotentHint: read-only tools plus
    # replayable writes. Verifying the exact expression guards the classification source.
    if not re.search(r'idempotentHint",\s*READ_ONLY_TOOLS\.contains\(name\)\s*\|\|\s*IDEMPOTENT_TOOLS\.contains\(name\)', source):
        raise ValueError("idempotentHint expression in McpAdapterController.java is not READ_ONLY_TOOLS.contains(name) || IDEMPOTENT_TOOLS.contains(name)")

    annotations = {}
    for name in names:
        annotations[name] = (
            name in read_only,
            name in destructive,
            name in read_only or name in idempotent_writes,
            name in open_world,
        )
    return names, descriptions, annotations


def backend_source() -> str:
    """Concatenate all backend main Java sources for deterministic mcp-source checks."""
    chunks = [MCP_CONTROLLER.read_text(encoding="utf-8")]
    for path in sorted(BACKEND_MAIN.rglob("*.java")):
        if path != MCP_CONTROLLER:
            chunks.append(path.read_text(encoding="utf-8"))
    return "\n".join(chunks)


def evaluate(skill: str, mcp_source: str, catalog: tuple[list[str], dict[str, str], dict[str, tuple[bool, bool, bool, bool]]], kind: str, expectation: str) -> bool:
    """Evaluate one check deterministically against repository files."""
    names, descriptions, annotations = catalog
    if kind == "skill-text":
        return expectation.lower() in skill.lower()
    if kind == "mcp-source":
        return re.search(expectation, mcp_source, re.IGNORECASE) is not None
    if kind == "catalog":
        return bool(re.fullmatch(r"([a-z_]+)", expectation)) and expectation in names and bool(descriptions.get(expectation, "").strip())
    if kind == "catalog-annotation":
        m = re.fullmatch(r"([a-z_]+)\s+([a-zA-Z]+)=(\w+)", expectation)
        if not m:
            raise ValueError(f"invalid catalog-annotation check: {expectation!r}")
        tool, field, value = m.group(1), m.group(2), m.group(3)
        if field not in ANNOTATION_FIELDS:
            raise ValueError(f"unknown annotation field {field!r} in check {expectation!r}")
        if value not in ("true", "false"):
            raise ValueError(f"annotation value must be true/false in check {expectation!r}")
        expected = value == "true"
        if tool not in annotations:
            return False
        if field == "title":
            return True  # title is textual; presence is checked by the catalog check
        field_index = {"readOnlyHint": 0, "destructiveHint": 1, "idempotentHint": 2, "openWorldHint": 3}[field]
        return annotations[tool][field_index] == expected
    raise ValueError(f"unknown check kind {kind!r}")


def run() -> int:
    skill = SKILL_MD.read_text(encoding="utf-8")
    mcp_source = backend_source()
    catalog = parse_catalog(MCP_CONTROLLER.read_text(encoding="utf-8"))
    payload = load_evaluation_set()
    failures: list[str] = []
    total_checks = 0

    # The exact source expressions behind every annotation field must be present.
    for fact, pattern in SOURCE_CONTRACT_FACTS.items():
        total_checks += 1
        if re.search(pattern, mcp_source) is None:
            failures.append(f"source-contract: {fact}: pattern {pattern!r} not found in McpAdapterController.java")

    for case in payload["cases"]:
        for kind, expectation in checks(case["checks"]):
            total_checks += 1
            try:
                ok = evaluate(skill, mcp_source, catalog, kind, expectation)
            except ValueError as exc:
                failures.append(f"{case['id']}: {exc}")
                continue
            if not ok:
                failures.append(f"{case['id']}: {kind}: {expectation}")

    if failures:
        print("Prompt evaluation FAILED:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print(f"Prompt evaluation passed: {len(payload['cases'])} cases / {total_checks} checks")
    return 0


if __name__ == "__main__":
    sys.exit(run())
