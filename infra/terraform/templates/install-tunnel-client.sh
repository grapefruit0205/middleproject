#!/usr/bin/env bash
set -euo pipefail

archive_url="${1:?archive URL is required}"
expected_sha256="${2:?archive SHA-256 is required}"
destination="${3:-/usr/local/bin/tunnel-client}"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
archive_path="$work_dir/tunnel-client.zip"
staged_binary="$work_dir/tunnel-client"

curl_args=(--fail --location --silent --show-error)
case "$archive_url" in
  https://*) curl_args+=(--proto '=https' --tlsv1.2) ;;
  file://*) ;;
  *)
    echo "ERROR: tunnel-client archive URL must use HTTPS" >&2
    exit 1
    ;;
esac

curl "${curl_args[@]}" --output "$archive_path" "$archive_url"
printf '%s  %s\n' "$expected_sha256" "$archive_path" | sha256sum --check --strict
unzip -p "$archive_path" tunnel-client >"$staged_binary"
test -s "$staged_binary"
install -o root -g root -m 0755 "$staged_binary" "$destination" 2>/dev/null || \
  install -m 0755 "$staged_binary" "$destination"
