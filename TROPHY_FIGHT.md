# Trophy Fight (트로피 힘겨루기) 내부 가이드

> 이 문서는 InMc-Fishing 플러그인의 **Trophy Fight** 시스템 동작을 코드 기준으로 정리한
> 개발/운영 참고 문서입니다. 실제 구현은 `src/main/java/me/ninesik/fishing/fight/` 패키지에 있고,
> 설정은 `fight.yml`(구 config.yml의 `trophy-fight:` 섹션)에 있습니다.
>
> 상태 · 수식 · 밸런스 · UI 구성 방식을 다룹니다.

---

## 1. 개요 & 발동 조건

- Trophy Fight는 일반 L/R 클릭 미니게임과 **별개의 추가 단계**입니다.
- RollEngine이 물고기를 **트로피 / 레어 트로피**로 판정하면, 미니게임 성공 후
  `TrophyFightManager.startFight()`로 진입합니다.
- **난이도 산출**: `등급 난이도 배수(fight.yml stats.grade-difficulty) × 레어 트로피 1.5배`
  → `difficulty`로 세션에 저장되어 물고기 Power/Resistance에 매 틱 곱해집니다.
- **도입 연출**: "대물의 기운…!" announce → 3·2·1 카운트다운(`step-seconds`) → **START!!**
  → 실제 Fight 시작 (`fight.yml intro.*`). 연출은 메인 스레드(sync)에서만 타이틀/사운드 재생.

---

## 2. 물고기 AI — 상태 기계 (FishAI)

물고기는 11개 상태를 돌며 행동합니다. 상태는 **"지금 어느 클릭을 써야 하는가"**라는 행동 축으로
나뉩니다 (좌클릭 위주 / 우클릭 위주 / 좌우 밸런스 / 대기 / 페널티). 상태마다
Power / Resistance / 지속시간이 다릅니다 (현재 하드코딩 기본값).

| 상태 | 행동 축 | Power | Resistance | 지속시간(틱) |
|------|---------|-------|-----------|--------------|
| `REST` | 좌클릭 | 10 | 10 | 2~4초 |
| `SLOW_MOVE` | 좌클릭 | 20 | 20 | 1.5~3초 |
| `NORMAL_MOVE` | 밸런스 | 40 | 40 | 1~2.5초 |
| `TURN` | 좌우 밸런스 | 50 | 50 | 0.75~1.75초 |
| `CIRCLE` | 좌우 밸런스(소강) | 40 | 60 | 2~3초 |
| `CHARGE` | 우클릭 | 80 | 70 | 0.5~1.25초 |
| `DIVE` | 우클릭(탠션 폭증) | 60 | 100 | 0.4~1.0초 |
| `FINAL_STRUGGLE` | 우클릭 | 100 | 90 | 0.25~0.75초 |
| `EXHAUSTED` | 좌클릭(몰아치기 보상) | 5 | 5 | 2~3초 |
| `JUMP` | 대기(관망) | 30 | 30 | 0.5~0.8초 |
| `LINE_TANGLE` | 우클릭 탈출(페널티) | 50 | 80 | 1.5~2.5초 |

**전이 규칙** (매 틱 `FishAI.tick()`, 상태 지속시간 종료 시 `transition()`):

- **FINAL_STRUGGLE(발악) 뒤에는 무조건 EXHAUSTED(탈진)로** — 발악으로 스태미너를 깎아낸
  보상을 "좌클릭 몰아치기 타이밍"(Power·Resistance 최저)으로 이어준다
  → "몰아치기 → 위기 → 다시 몰아치기" 리듬.
- **EXHAUSTED(탈진) 후**: 물고기가 회복하며 다시 위기 상태(SLOW/NORMAL/CHARGE)로 올라선다.
- 그 외에는 물고기 **스태미너 비율**에 따라 가중 랜덤 전이
  (CIRCLE 소강 / JUMP 관망 / DIVE 돌진계 포함).
- **LINE_TANGLE(줄엉킴)**: config `line-tangle.enabled`(기본 false)일 때만 상태 전이 확률로
  발동. 발생 중 좌클릭 효율이 크게 떨어지고 우클릭으로만 관리해야 한다.
- **핵심 의미**: 물고기가 지칠수록 플레이어에게 유리(좌클릭 몰아치기), 초반엔 사납게 덤빕니다.

> 참고: `FightConfig.AiConfig`의 `stateDurations`/`transitionProbabilities`(yml)는 현재 **빈 맵** —
> 실제 판정은 위 하드코딩 값을 사용합니다.

---

## 3. 핵심 스탯 & 수식 (FightCalculator)

Fight는 매 틱(1/20초) 계산되는 "실시간 힘겨루기 시뮬레이션"입니다. 움직이는 스탯:

- **Fish Stamina** — 물고기 체력. 0이 되면 지칠 때까지 끌어올 수 있음
- **Distance** — 줄 거리. 0이 되면(물고기 지침 상태에서) 승리
- **Tension** — 줄 장력. 줄 강도(Line Strength) 이상 오르면 **줄 끊김**
- **Reel State** — 릴 HP. 0이 되면 **릴 파손**
- **Power / Resistance** — 물고기 힘/저항 (상태별 × difficulty)

### 3-1. 좌클릭 = 릴 감기 (제압)

- **Stamina 감소**:
  `ReelPower × 0.01 × 릴효율(reelState/100) × 상태배수`
  - 상태배수: `CHARGE ×1.5`, `FINAL_STRUGGLE ×0.4`, 그 외 ×1.0 (발악 땐 스태미너가 잘 안 깎임)
- **Distance 감소 (거리 회수)**:
  `fishEscape − (baseReel + bonusReel)`
  - `fishEscape = Power × 0.004` (물고기 도망)
  - `baseReel = 30 × 0.036 × 저항계수 × (SLOW_MOVE면 1.5)` — 릴만 감으면 항상 최소 회수
  - `bonusReel = 30 × 0.105 × 지침계수 × 저항계수` — 물고기가 지칠수록 커짐
  - `저항계수 = 100/(100+Resistance)` — Power/Resistance 클수록 회수 어려움
  - ⚠️ **밸런스 포인트**: Distance 계산엔 낚싯대 실제 ReelPower가 아니라 **고정 기본값 30**을 사용
    (피드백: "릴 파워가 거리에 영향 안 주게"). 대신 **Stamina 감소 속도**에는 실제 ReelPower가 그대로 반영.
  - **SLOW_MOVE 상태**에서 릴을 감으면 거리 회수를 1.5배 — "천천히 가는 물고기를 당길 때" 전략적 보너스
- **Tension 상승**: `Power × 0.05 × 상태배수` — `TURN ×1.5`, `CHARGE/FINAL_STRUGGLE ×2.0`
- **Reel State 감소**: `−(Power+Resistance) × 0.01 × 내구계수`, `내구계수=100/(100+reelDurability)`

### 3-2. 우클릭 = 릴 풀기 (회복/완화)

- **Distance 증가**: `fishEscape + 0.13 × 상태배수` — 상태배수 `REST 0.5 ~ FINAL_STRUGGLE 6.0`
- **Tension 감소**: 콤보 기반 `−2.0 × 콤보` (연타할수록 크게 완화)
- **Reel State 회복**: 상한의 `0.8%` (모든 상태 동일)

### 3-3. 아무것도 안 하면 (idle)

- **Stamina 회복**: `상한 × 상태비율` (REST 0.15% → CHARGE/발악 0%) — "쉬게 두면 물고기 힘 회복" 리스크
- **Distance 증가**: 물고기만 도망가며 늘어남
- **Tension 감소**: `−2.0/틱` (자연 감소)
- **Reel State 회복**: 상한의 `0.15%`

### 3-4. 하드캡 (밸런스)

- **최소 거리 하한**: 스태미너가 남아있는 동안 Distance는 등급별
  `min-distance-with-stamina`(f:50 ~ s:120) 밑으로 안 내려감 → 아직 안 지친 물고기는
  일정 거리 이상엔 못 끌어당김.
- **Distance 상한**: `max-distance(150) + 낚싯대 line-strength 보너스` — 넘으면 줄 끊김.

---

## 4. 매 틱 업데이트 순서 (TrophyFightManager.tick)

1. Fish AI 갱신 + 상태 타이틀 갱신
2. Power/Resistance = 상태 기준값 × difficulty
3. 입력 반영 (L/R/idle)
4. Stamina 계산 (감기=감소 / idle=회복)
5. Distance 계산 (우클릭=증가 / 그 외=회수·도망) + 최소거리 하한 적용
6. Tension 계산
7. Reel State 계산
8. **Stamina 0 → 타이머 일시정지** (물고기 지치면 제한시간 카운트 멈춤)
9. HUD 갱신
10. 파티클/사운드 (인터벌)
11. **종료 조건 확인**

---

## 5. 승리/패배 조건 (`checkEndConditions`)

- **승리**: `Distance ≤ 0 && Stamina ≤ 0` — 지친 물고기를 완전히 끌어올 때
- **패배** (원인별 메시지 `messages.yml fail.*`, `FightFailReason`):
  1. **줄 끊김** — `Tension ≥ LineStrength`
  2. **거리 초과** — `Distance ≥ MaxDistance`
  3. **릴 파손** — `ReelState ≤ 0`
  4. **시간 초과** — `MaxTimeSeconds` 초과 (스태미너 0으로 타이머가 일시정지된 동안엔 안 셈)
- **결과 처리**: 성공 → `RewardService.giveReward()`로 최종 보상, 실패 → `handleFail(원인)`로
  메시지+사운드 후 보상 폐기.
- 로그아웃/사망/월드이동 시 `stopFight(CANCELLED)`.

---

## 6. UI 구성 방식

### 6-1. 실시간 HUD (FightHUD)

- **BossBar (상단)**: Tension 비율을 게이지로 표시 — `tension / lineStrength`
  - 위험도 색: <50% 초록 → <80% 노랑 → ≥80% 빨강 (`hud.bar-color-*`)
  - 이름: `"거리: {distance} / {max_distance} M"` (`hud.bossbar-title-format`)
- **ActionBar (하단)**: 물고기 체력·릴 상태·거리 표시
  `"물고기 채력 {stamina}% | 릴 상태 {reel}% | 거리 {distance}/{max_distance}M"`
  (`hud.actionbar-format`, `{power}/{resistance}` 도 사용 가능)
- **상태 타이틀 (중앙) + 권장 행동 서브타이틀**:
  - 상태별 가이드 `hud.state-guide.<state>.(title|subtitle)` 를 우선 사용 (권장 행동 힌트)
  - 설정이 없으면 `state-title-format`/`state-subtitle-format`(상태명 + 남은시간)으로 폴백
  - 예: DIVE → `🐟 잠수!` / `우클릭 연타로 버티세요! 탠션이 폭증합니다.`
  - EXHAUSTED → `🐟 탈진!` / `좌클릭 연타로 승부를 보세요! 저항이 없습니다.`

→ 즉, **Tension(BossBar) + 자원(ActionBar) + 상태·할 행동(타이틀/서브타이틀)** 을 동시에 보여주어
"지금 무엇을 해야 하는가"를 먼저 전달합니다.

### 6-2. 도입부 연출 (Intro)

- announce 타이틀("!!!"/"대물의 기운…") → 효과음 → 3·2·1 카운트다운(각 `step-seconds`) → START!!
- 타이틀/사운드 재생은 메인 스레드(sync)에서만 처리.

### 6-3. 메인 GUI 진입 (MainGui)

- 시프트+F → 5줄 메뉴:
  - 스탯(릴 파워/줄 강도/릴 내구성 = 기본값+낚싯대 보너스, 피로도, 등급별 출현확률)
  - 어망 / 도감 / 랭킹 / 대회
  - **미니게임 ON/OFF** 토글, **트로피 파이트 연습모드** 토글(보상 없이 연습),
    **트로피 파이트 설명** 버튼
- `/fishing testfightmode`(tfm): 관리자가 특정 플레이어의 물고기를 무조건 Fight로 진입시켜 테스트.

---

## 7. 밸런스 요약 (피드백 반영 핵심)

- **거리 회수는 낚싯대와 무관(고정 30)**, 대신 **스태미너 감소에만** 낚싯대 ReelPower 반영
  → 낚싯대가 좋아도 "무조건 빨리 끌어올리기"는 불가.
- 물고기가 **+돌진/발악일수록** 안 잡아주고, **지칠수록** 잡기 쉬움
  → "릴 잘 감고(스태미너 소모) → 발악 때 안 당기고(장력/거리 관리) → 지치면 회수"가 정석 플레이.
- 우클릭(릴 풀기)은 장력·릴 HP를 회복하지만 거리를 잃는 **트레이드오프**
  (발악 때 우클릭하면 거리 매우 증가 ×6.0).
- 자연회복(Stamina/ReelState) 유지 → "멍 때리면 역전당함".

---

## 8. 관련 설정 파일 위치

| 항목 | 파일 / 섹션 |
|------|-------------|
| 상태/파티클/사운드/스탯/난이도 등 | `fight.yml` → `trophy-fight:` |
| 실패 원인별 메시지 | `messages.yml` → `fail.*` |
| HUD·상태 표시 텍스트/색상·상태별 가이드 | `fight.yml` → `trophy-fight.hud.*` (state-guide, state-color) |
| 도입 연출 텍스트/사운드 | `fight.yml` → `trophy-fight.intro.*` |
| 등급별 최소거리/난이도 배수 | `fight.yml` → `trophy-fight.stats.*` |
| 줄 엉킴(LINE_TANGLE) 페널티 이벤트 | `fight.yml` → `trophy-fight.line-tangle` (기본 비활성) |
| 연습모드 / 테스트명령 | GUI · `/fishing testfightmode` |

