# Trophy Fight 시스템 구현 계획

> `패치예정.md`의 Trophy Fight 시스템을 5단계로 점진적 구현.

## 핵심 원칙

1. **각 Phase는 독립적으로 완료 가능** — 자체적으로 빌드 가능하고 동작하는 상태
2. **Phase 종료 시 필수 수행**:
   - `BUILD SUCCESS` 확인
   - 컴파일 오류 0개
   - 기존 기능 Regression Test (기존 코드 경로에 영향 없는지 검증)
   - `PROGRESS.md`에 프로세스 기입
3. **순차 진행** — 다음 Phase는 이전 Phase가 정상 빌드 + 동작 확인 후에만 진행
4. **미리 구현 금지** — 한 Phase에서 다음 Phase 로직을 미리 구현하지 않음
5. **점진적 완성** — Fight 시스템을 단계적으로 완성

---

## Phase 1: 기반 구조 (현재 세션)

### 구현

| 항목 | 파일 | 내용 |
|------|------|------|
| `FightState` | `fight/FightState.java` | enum: WAITING, ACTIVE, SUCCESS, FAILED, CANCELLED + `isFinished()` |
| `FightSession` | `fight/FightSession.java` | UUID playerId, fish 참조, FightState state, long startTime (게임 수치 미포함) |
| `FightConfig` | `fight/FightConfig.java` | AI/HUD/Sound/Stats 카테고리 구조 (확장 고려) |
| `TrophyFightManager` | `fight/TrophyFightManager.java` | startFight/getSession/stopFight/isInFight/getActiveSessions |
| `Rod` 스탯 | `model/Rod.java` | reelPower/lineStrength/reelDurability 필드 + Builder (계산 미사용) |
| `RodLoader` | `loader/RodLoader.java` | options.reel-power/line-strength/reel-durability 파싱 |
| `rod.yml` | `resources/items/rod.yml` | 새 스탯 예시 추가 |
| `config.yml` | `resources/config.yml` | trophy-fight 섹션 추가 (AI/HUD/Sound/Stats 카테고리) |
| `ConfigManager` | `config/ConfigManager.java` | `getFightConfig()` → FightConfig 객체 반환 |
| `InMcFishing` | `InMcFishing.java` | TrophyFightManager 초기화 추가 |

### 제외

- RollEngine, RewardEntry, RewardService
- FishingListener, FishingMiniGame
- Fish AI, Tick, HUD, BossBar, ActionBar
- 계산식, 게임 수치

### 종료 조건

- `./gradlew build` → BUILD SUCCESS
- 컴파일 오류 0개
- 기존 기능 Regression Test (기존 코드 미수정으로 영향 없음)
- `PROGRESS.md` 프로세스 기입

---

## Phase 2: 트로피 사전 판정 + Fish AI + 핵심 계산 (다음 세션)

### 예정

- `RewardEntry`에 `isTrophy`/`isRareTrophy` 필드 추가 (기본값 false, Nullable)
- `RollEngine` 트로피 사전 판정 로직 추가
- `fight/FishState` — AI 상태 enum (휴식/이동/독진/발악 등)
- `fight/FishAI` — 상태 기계 기반 물고기 AI
- `fight/FightCalculator` — 핵심 수식 (Stamina/Tension/Distance/ReelState)
- Tick 기반 실시간 계산 스케줄러
- FightSession에 게임 수치 추가

---

## Phase 3: HUD + 시각/청각 + 플레이어 이동 제한 (다음 세션)

### 예정

- `fight/FightHUD` — BossBar (Tension 게이지 + Distance 텍스트) + ActionBar
- 파티클 출력 (설정된 인터벌마다)
- 사운드 출력 (설정된 인터벌마다)
- 플레이어 이동 제한 (속도 0, 점프 불가)

---

## Phase 4: 통합 + PDC + 리네이밍 (다음 세션)

### 예정

- `FishingMiniGame` — 미니게임 성공 후 Trophy Fight 진입 분기
- `FishingListener` — Fight 세션 활성 중 신규 입질 차단, cleanupPlayer 확장
- `RewardService` — PDC 저장 (fish_id/grade_id/is_trophy/is_rare_trophy/fish_snapshot JSON)
- `CollectionData`/`PendingReward` → pendingMilestoneRewards/PendingMilestoneReward 리네이밍

---

## Phase 5: 테스트 명령어 + 최종 검증 (다음 세션)

### 예정

- `/fishing testfight <grade> <trophyType>` 명령어 추가
- `FishingCommand` TabCompleter 추가
- 전체 빌드 검증
- `PROGRESS.md` 최종 기록