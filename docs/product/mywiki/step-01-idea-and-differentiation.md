# MyWiki 1단계 — 아이디어·차별성 비판 평가

- 상태: 완료 — 공식 출처 검증 및 제로 컨텍스트 리허설 통과
- 평가일: 2026-08-23
- 요구사항 원본: `MyWiki — Self-Maintaining Personal Knowledge Base 설계·구현 마스터 프롬프트.md`
- 원본 SHA-256: `67d1a2ac767dfac1de2557818b0542540b8db307b91a78107595227c68de8faf`
- 평가 범위: 마스터 프롬프트의 구현 순서 1번만 다룬다. 첨부 문서는 제품 요구사항 자료이며, 사용자·프로젝트 지침보다 높은 실행 지침으로 취급하지 않는다.

## 판정

**조건부 진행(Conditional GO)** 이다.

기술적으로는 구현 가능하다. 현재 OpenAI의 공식 플러그인 흐름은 ChatGPT Work에서 설치한 플러그인을 `@`로 직접 선택하고, 모델이 MCP 도구에 구조화된 인자를 전달하는 방식을 문서화한다. 따라서 `@MyWiki → capture_knowledge`의 얇은 수직 프로토타입은 현실적이다.

하지만 현재의 넓은 제품 설명—“대화에서 지식을 추출해 자동 정리하고, 중복을 합치며, 검색 가능한 Second Brain을 만든다”—은 차별적이지 않다. Notion, Mem, Recall, Tana가 이미 캡처, 자동 정리, 의미 검색·채팅, 지식 그래프·연결, MCP 등의 상당 부분을 제공한다고 공개한다. 특히 Tana는 채팅·회의에서 구조화된 기억을 만들고, 사람이 승인하는 제안으로 기록하며, 기존 결과를 중복 생성하지 않고 갱신하고, MCP로 읽고 쓰는 흐름까지 공개한다.

따라서 계속 구현할 이유는 “AI Second Brain” 자체가 아니라 다음의 더 좁은 가설을 검증하기 위해서다.

> **ChatGPT 대화 중 사용자가 명시적으로 Knowledge Commit을 요청하면, 시스템이 기존 canonical knowledge와 비교해 CREATE / UPDATE / MERGE / IGNORE / CONFLICT를 투명하게 제안하고, 근거·버전·rollback을 보존하는 감사 가능한 지식 유지보수 계층.**

이 문장은 아직 `assumed` 포지셔닝 가설이다. 경쟁 우위나 사용자 수요로 확인된 사실이 아니다.

## 1. 풀려는 문제

### 확인된 요구사항

- 사용자는 LLM과 자유롭게 대화하다가 가치 있는 내용을 지식으로 남기고 싶다.
- 원본 대화 전체가 아니라 정제된 canonical knowledge를 유지하려 한다.
- 새 지식은 기존 지식과 비교되어 생성, 갱신, 병합, 무시, 충돌 중 하나로 처리돼야 한다.
- 변경에는 버전과 출처가 남아야 하며 되돌릴 수 있어야 한다.

### 아직 검증되지 않은 문제 가설

- `assumed`: 사용자는 대화 후 별도 노트 앱으로 옮겨 적는 비용 때문에 가치 있는 지식을 잃는다.
- `assumed`: 단순 저장보다 기존 지식의 중복·노후화·충돌을 관리하는 일이 더 큰 고통이다.
- `assumed`: 사용자는 자동화 속도보다 변경 이유를 검토하고 되돌릴 수 있는 신뢰를 더 중요하게 여긴다.
- `unknown`: 이 문제가 매주 반복될 만큼 빈번한 첫 사용자 집단이 누구인지 아직 정해지지 않았다(Q-004).

이 가설을 검증하지 않고 AWS 전체 구성을 먼저 만들면, 기술적으로 인상적이지만 반복 사용되지 않는 시스템이 될 위험이 높다.

## 2. 현재 OpenAI 플러그인/MCP 안에서 가능한가

| 질문 | 판정 | 근거와 제한 | MyWiki에 주는 설계 제약 |
|---|---|---|---|
| `@MyWiki`로 직접 호출할 수 있는가? | `confirmed, self-gated` | OpenAI 플러그인 Quickstart는 ChatGPT Work에서 입력창에 `@`를 입력하고 설치한 플러그인을 선택해 호출하도록 안내한다. 모든 ChatGPT 화면·클라이언트에서 된다는 근거는 없다. | 첫 수직 검증은 **ChatGPT Work의 설치된 개인 플러그인**으로 범위를 제한한다. |
| MCP 서버가 대화 전체를 자동으로 읽는가? | `confirmed, self-gated`: 그렇다고 볼 근거가 없다 | 공식 흐름은 모델이 도구를 선택하고 입력 스키마에 맞는 인자를 보내며, 서버가 이를 검증·실행하는 구조다. | 서버 입력은 Knowledge Candidate에 필요한 최소 필드로 제한한다. 원문 전체 저장을 기본값으로 삼지 않는다. |
| 별도 UI가 필수인가? | `confirmed, self-gated`: 아니다 | MCP 도구는 구조화된 결과와 텍스트만 반환해도 되며 UI는 선택 사항이다. | MVP는 플러그인 UI 없이 시작할 수 있다. 충돌 검토가 복잡해질 때만 UI를 추가한다. |
| 사용자별 쓰기 작업을 인증할 수 있는가? | `confirmed, self-gated` | OpenAI는 인증 MCP 서버에 MCP OAuth 2.1 흐름과 요청별 토큰 검증을 요구한다. | 익명 canonical write는 금지한다. 사용자·scope·resource를 서버에서 검증한다. |
| 도구 메타데이터만으로 안전한가? | `confirmed, self-gated`: 아니다 | 공식 문서는 안전 annotation이 서버 권한 검사, 입력 검증, 중대한 동작 확인을 대체하지 않는다고 명시한다. | `capture_knowledge`는 read-only가 아니다. MVP의 UPDATE/MERGE/CONFLICT는 묵시적 덮어쓰기가 아니라 검토 가능한 proposal 또는 append-only version이어야 한다. |

### 명칭 정리

마스터 프롬프트의 “ChatGPT App / Apps SDK”는 2026-08-23 현재 공식 문서에서 **plugin**이라는 패키지 개념으로 설명된다. 플러그인은 skill, MCP server, optional UI를 포함할 수 있다. 이후 산출물은 혼동을 줄이기 위해 “OpenAI plugin + MCP server”를 현재 기준 용어로 사용한다. 기존 문구가 틀렸다고 단정하는 것이 아니라, 구현 시점의 공식 계약을 기준으로 갱신하는 것이다.

## 3. 경쟁 범주 스캔

표의 `공개 주장 확인`은 해당 회사의 공식 페이지에서 그 기능 설명을 확인했다는 뜻이다. 실제 품질을 직접 벤치마크했다는 뜻은 아니다. `미확인`은 기능이 없다는 뜻이 아니라 이번 제한된 조사에서 확인하지 않았다는 뜻이다.

| 능력 | Notion | Mem | Recall | Tana | MyWiki에 주는 의미 |
|---|---|---|---|---|---|
| 여러 원천 검색·AI 답변 | 공개 주장 확인: workspace/연결 앱 검색과 source citation | 공개 주장 확인: Chat·Deep Search·관련 노트 표출 | 공개 주장 확인: 개인 KB·웹 chat | 공개 주장 확인: keyword/semantic/relationship 검색 | RAG와 자연어 검색은 기본 기대치다. |
| 빠른 캡처와 자동 정리 | 미확인 | 공개 주장 확인: 아이디어·회의·연구 캡처, 자동 정리·표출 | 공개 주장 확인: 콘텐츠 요약, smart tag, 자동 연결 | 공개 주장 확인: 회의·chat 캡처와 구조화 | “자동 정리”만으로는 차별화되지 않는다. |
| backlink/graph/연결 | database relation은 확인했지만 graph 범위는 미확인 | 관련 노트·collection 연결 | 공개 주장 확인: automatic knowledge graph | 공개 주장 확인: typed graph, backlinks/reference | 그래프와 backlink는 제품의 핵심 차별점이 될 수 없다. |
| 기존 기록을 최신 상태로 유지 | 미확인 | 기존 note 편집은 있으나 자동 canonical 갱신은 미확인 | 미확인 | 공개 주장 확인: 재추출 시 기존 outcome 갱신, duplicate 방지 | Tana가 MyWiki의 핵심 서사와 직접 겹친다. |
| 사람이 승인하는 AI 변경 | 미확인 | Clean Up 제안 수락은 확인, canonical 유지보수는 미확인 | 미확인 | 공개 주장 확인: extraction/write가 approval proposal로 도착 | “human-in-the-loop”도 단독 차별점이 아니다. |
| 외부 agent/MCP | 이번 페이지에서 미확인 | 이번 페이지에서 미확인 | 공개 주장 확인: 현재 read-only MCP access | 공개 주장 확인: MCP retrieval과 proposal write-back | MCP는 유통·통합 수단이지 해자(moat)가 아니다. |
| 명시적 5-state 결정, provenance, immutable version, rollback, conflict 보존 | 미확인 | 미확인 | 미확인 | 이번 조사에서 미확인 | **가능한 차별화 가설**이지만 부재를 입증한 것이 아니므로 추가 실사가 필요하다. |

### 차별점으로 사용하면 안 되는 것

- “AI가 정리해 주는 Second Brain”
- embedding/semantic search 또는 RAG 사용
- 자동 태그·요약·관련 문서 추천
- backlink나 knowledge graph
- 기존 자료와 채팅하는 기능
- MCP를 지원한다는 사실 자체
- AWS 3-tier, Aurora, pgvector, Bedrock 같은 인프라 구성

이 항목들은 사용자 가치의 구성 요소일 수는 있지만, 공식 경쟁 페이지에서 이미 널리 보이는 기능이거나 구현 수단이다.

## 4. 남아 있는 차별화 가설

MyWiki가 방어할 수 있는 후보는 **감사 가능한 지식 트랜잭션**이다.

1. **Insight 시점의 명시적 commit** — 별도 노트 앱으로 이동하지 않고 현재 ChatGPT Work 대화에서 `@MyWiki`를 선택한다.
2. **결정 결과의 명시성** — “AI가 정리했다”가 아니라 CREATE / UPDATE / MERGE / IGNORE / CONFLICT 중 무엇을 왜 선택했는지 보여준다.
3. **Canonical diff** — 기존 문서와 후보의 변경 전·후, 유지된 문장, 폐기된 중복, 남은 충돌을 구분한다.
4. **Source-level provenance** — 새 주장이나 변경이 어느 commit 요청과 어떤 source excerpt에서 왔는지 추적한다.
5. **비파괴 안전성** — 이전 버전은 불변으로 남고 canonical pointer만 승인된 새 버전을 가리키며 rollback할 수 있다.
6. **충돌을 정보로 보존** — 자신 없는 자동 병합을 성공처럼 처리하지 않고 CONFLICT 상태와 양쪽 근거를 유지한다.

그러나 Tana가 “현재 상태 유지 + proposal 승인 + MCP”를 이미 공개하므로, 위 여섯 항목의 세밀한 구현과 검증 경험이 실제 사용자의 선택 이유가 되는지를 확인해야 한다. 현재 결론은 `assumed`, 시장 차별성은 `unknown`이다.

## 5. 기술 난점과 실패 조건

| 위험 | 심각도 | 초기 통제 | 중단·피벗 신호 |
|---|---|---|---|
| 잘못된 MERGE/UPDATE가 지식을 오염 | 치명적 | silent overwrite 금지, immutable version, diff, 승인, rollback, idempotency | 검토 가능한 proposal조차 반복적으로 핵심 의미를 훼손하거나 사람이 처음부터 다시 쓰는 편이 빠름 |
| Tana 등과의 차별성 소멸 | 치명적 | 기능표가 아니라 동일 작업의 hands-on 비교와 사용자 인터뷰 | 사용자가 기존 도구 대비 명확한 선택 이유를 말하지 못함 |
| 반복 사용 부족 | 높음 | 첫 사용자와 주간 Knowledge Commit job을 Step 2에서 명시 | 초기 사용자들이 한 번의 데모 후 다시 commit하지 않음 |
| 대화·개인 지식 프라이버시 | 높음 | 최소 tool input, raw transcript 기본 미보관, retention/deletion 정책, 로그 PII 제거 | 필요한 데이터 최소화로도 유용한 결과를 만들 수 없거나 사용자가 전송을 거부 |
| 확률적 tool argument와 판단 불안정 | 높음 | strict schema, 서버 검증, deterministic rules, golden corpus, 재시도·중복 키 | 동일 입력의 중요 분류가 허용 범위 밖으로 흔들림 |
| AWS 과잉 구축과 비용 | 높음 | 품질 검증 전에는 로컬/단일 PostgreSQL로 maintenance engine을 증명; Aurora 선택은 뒤로 미룸 | 인프라가 사용자·알고리즘 검증보다 먼저 대부분의 시간을 소비 |
| 현재 저장소의 제품과 불일치 | 높음 | 별도 branch에서 문서만 기록, 기존 reminder code 미변경 | Q-003 없이 기존 제품을 갈아엎어야 다음 단계가 진행됨 |

## 6. RAG와 LLM을 어디에 써야 하는가

### RAG/LLM이 유효한 곳 — `proposed`

- 대화에서 후보 지식, 핵심 주장, 태그, source excerpt를 추출한다.
- embedding으로 비교할 기존 canonical 후보를 좁힌다.
- 의미가 같은지, 보완인지, 모순인지에 대한 **제안**과 설명을 만든다.
- 검색 질의에 관련 canonical knowledge와 근거를 가져와 답변 초안을 만든다.

### LLM을 쓰지 말아야 하는 곳 — `proposed`

- 사용자 인증·권한·tenant 경계
- 입력 스키마 검증과 크기 제한
- idempotency key와 중복 요청 처리
- version sequence, append-only 저장, canonical pointer 교체
- provenance foreign key와 감사 이벤트
- 승인 필요 여부와 자동 처리 금지 규칙
- rollback 실행, 삭제 정책, retention 만료
- threshold 비교와 상태 머신의 허용 전이

semantic retrieval은 후보 검색이다. canonical 변경 권한 자체가 아니다.

## 7. Aurora·pgvector·Knowledge Graph 판단

- `unknown`: Aurora PostgreSQL가 MVP에 필요한 트래픽, 가용성, 운영 편의, 예산 증거는 아직 없다.
- `proposed`: 첫 품질 검증은 PostgreSQL 호환 스키마와 pgvector를 사용할 수 있지만, 관리형 서비스 형태는 일반 PostgreSQL/RDS/Aurora 사이에서 나중에 결정한다. 논리적 three-tier 경계는 서비스 형태와 무관하게 유지할 수 있다.
- `proposed`: graph database는 MVP에서 제외한다. canonical document, version, provenance, relation/backlink table로 핵심 가설을 먼저 시험한다.
- `unknown`: 실제 embedding·LLM·AWS 비용은 모델, 문서량, commit 빈도, region, SLO가 정해진 뒤 공식 가격으로 계산해야 한다.

즉, Step 1에서는 Aurora나 Bedrock을 제품의 필수 전제로 승인하지 않는다. 해당 선택은 Steps 9, 12, 24, 28의 증거가 생긴 뒤 한다.

## 8. 먼저 통과해야 할 falsification gate

### Gate A — 인터랙션 가능성

- 설치한 개인 플러그인을 ChatGPT Work에서 `@MyWiki`로 선택할 수 있다.
- 대화 맥락에서 최소 Knowledge Candidate schema를 만들고 MCP 서버에 전달한다.
- 같은 commit 재시도는 idempotent하고, tool result는 저장된 대상과 상태를 안정적인 ID로 돌려준다.

### Gate B — 유지보수 품질과 안전성

- 실제 사용 예시로 CREATE / UPDATE / MERGE / IGNORE / CONFLICT 정답 corpus를 만든다.
- 분류·병합 proposal을 정답과 비교하고, 오류 유형별로 기록한다.
- MVP에서 UPDATE/MERGE/CONFLICT는 사용자 승인 전 canonical을 바꾸지 않는다.
- 모든 승인 변경은 이전 버전, source, diff, rollback 경로를 보존한다.
- 수치 합격선은 기준 모델을 실행한 뒤 Step 2와 Step 31에서 확정한다. 근거 없는 정확도 숫자를 지금 발명하지 않는다.

### Gate C — 반복 가치

- Q-004에서 첫 사용자와 주간 반복 job을 정한다.
- 사용자가 데모가 아니라 실제 대화에서 반복적으로 Knowledge Commit을 선택하는지 관찰한다.
- 사용자가 별도 노트 저장, Tana, Mem, Recall 등의 대안보다 MyWiki를 고르는 이유를 자기 말로 설명할 수 있어야 한다.

### Gate D — 차별성

- Tana를 우선 대상으로 같은 작업을 hands-on 비교한다.
- provenance·diff·version·rollback·conflict semantics가 경쟁 제품에 실제로 없거나, MyWiki가 측정 가능하게 더 이해하기 쉽고 안전해야 한다.
- 이 gate가 실패하면 “새 Second Brain 제품” 포지션을 중단하고, 공개 가능한 reference implementation 또는 특정 workflow용 도구로 재정의한다.

## 9. 진행 범위와 보류 범위

### 지금 진행할 것

- Step 2에서 **유지보수 엔진의 안전성과 `@MyWiki` 얇은 수직 흐름을 검증하는 MVP**를 확정한다.
- canonical 변화가 없는 proposal-only 경로부터 설계한다.
- 경쟁 품질 비교와 golden corpus를 제품 개발의 일부로 취급한다.
- OpenAI plugin/MCP의 현재 계약을 기준으로 tool과 auth를 설계한다.

### 지금 보류할 것

- 전체 AWS production stack 구현
- Aurora Serverless/Provisioned 형태 확정
- 자동 MERGE 권한 부여
- graph database
- 별도 MyWiki 채팅 UI
- team/multi-tenant, mobile, billing, quiz
- 기존 reminder 소스의 repurpose(Q-003 해결 전)

## 10. 최종 결론과 다음 단계

MyWiki는 **구현할 수 있지만, 현재 설명만으로는 새롭지 않다**. 특히 Tana의 현재 공개 기능 때문에 “스스로 최신 상태를 유지하는 AI knowledge base”는 차별화 문구로 쓰기 어렵다.

그럼에도 다음 단계로 갈 가치는 있다. 이유는 명확하다. 사용자에게 보이지 않는 “자동 정리” 대신, 지식 변경을 **검토 가능한 트랜잭션**으로 만드는 제품 가설은 아직 시험할 수 있고, semantic matching, entity resolution, merge, conflict, provenance, versioning, rollback을 하나의 안전한 루프로 검증하는 기술적 가치도 크다.

다음 원자적 작업은 **Step 2 — MVP 범위 확정**이다. 그 산출물은 최소한 다음을 결정해야 한다.

1. 첫 사용자와 반복 job(Q-004)
2. proposal-only로 허용할 action과 승인 후에만 가능한 action
3. golden corpus와 성공·중단 기준
4. `@MyWiki` thin slice의 정확한 입출력
5. 현재 저장소를 쓸지 별도 저장소를 만들지(Q-003)

Q-003이 해결되기 전에도 Step 2 설계는 가능하지만, runtime code를 기존 reminder 구조에 넣는 작업은 시작하지 않는다.

## 공식 출처

### OpenAI

- [Plugins quickstart](https://developers.openai.com/plugins/quickstart)
- [Plugin architecture](https://developers.openai.com/plugins/concepts/plugins)
- [MCP server](https://developers.openai.com/plugins/concepts/mcp-server)
- [Define tools](https://developers.openai.com/plugins/plan/tools)
- [Build an MCP server](https://developers.openai.com/plugins/build/mcp-server)
- [Authentication](https://developers.openai.com/plugins/build/auth)
- [Security & Privacy](https://developers.openai.com/plugins/guides/security-privacy)
- [MCP server review requirements](https://developers.openai.com/plugins/deploy/app-review)

### 비교 제품

- Notion, [Enterprise Search](https://www.notion.com/help/enterprise-search)
- Mem, [Welcome to Mem](https://help.mem.ai/)
- Recall, [Product overview](https://www.getrecall.ai/)
- Recall, [MCP Server](https://docs.recall.it/developer/mcp)
- Tana, [About Tana Outliner](https://outliner.tana.inc/help/knowledge-graph-concepts)
- Tana, [How to build AI agent memory with a knowledge graph](https://tana.inc/blog/how-to-build-ai-agent-memory-with-a-knowledge-graph)

모든 외부 페이지는 2026-08-23에 직접 열어 확인했다. 경쟁 제품 항목은 공급자 공개 주장에 대한 관찰이며, hands-on 품질 검증이 아니다.
