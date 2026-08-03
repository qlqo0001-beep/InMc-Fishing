# Trophy Fight Phase 4-5 완전성 강화 (Revision Plan)

## 목표

사용자가 지적한 4대 문제점을 수정하여 Trophy Fight을 "클릭 게임"이 아닌 "실시간 힘겨루기 시뮬레이션"으로 전환한다.

## 문제점 요약

| No | 문제 | 현재 현상 |
|----|------|-----------|
| 1 | 입력 처리 | 좌/우클릭이 동일하게 처리, `isReeling`이 한번 설정되면 영구 유지 |
| 2 | 스태미너 감소 | `reelPower * reelEfficiency` — 100배 과도, 2틱 내에 지침 |
| 3 | Distance | 클릭 즉시 감소, fish state 무시 |
| 4 | Reel State | idle 시 `+1.0/tick` 회복 — 입력 없이 Also 증가 |

## 수정 방향

### 1. FightCalculator 수식 수정 (핵심)

**파일**: `src/main/java/me/ninesik/fishing/fight/FightCalculator.java`

#### calculateStaminaDecrease
```java
// BEFORE: return reelPower * reelEfficiency;
// AFTER:  return reelPower * 0.01 * reelEfficiency;
```
- 100배 축소: reelPower=30 → 0.3/tick (100 stamina → ~333틱/16.7초)

#### calculateDistanceChange (시그니처 변경)
```java
// BEFORE: calculateDistanceChange(reelPower, fishPower, fishResistance)
// AFTER:  calculateDistanceChange(reelPower, fishPower, fishResistance, staminaRatio, isReeling)
```
새 로직:
```java
double fishEscape = fishPower * 0.03;
if (!isReeling) return fishEscape; // 릴 안 잡으면 물고기 도망감
double exhaustionFactor = 1.0 - staminaRatio; // 0(활기) ~ 1(지침)
double reelAmount = reelPower * 0.3 * exhaustionFactor * (1.0 - fishResistance / 100.0);
return fishEscape - reelAmount;
```
- Stamina 50% 기준 토글: exhaustionFactor < (fishEscape / reelPower / 0.3 / (1-resistance/100))이면 distance 증가

#### calculateReelStateChange
```java
// BEFORE: !isReling → return 1.0 (회복)
// AFTER:  !isReeling → return 0.0 (유지)
// BEFORE: -(fishPower + fishResistance) * 0.01 / durability
// AFTER:  -(fishPower + fishResistance) * 0.001 / durability (10배 완화)
```

#### calculateTensionChange
수정 불필요 — 기존 수식(`fishPower * 0.05 * reelFactor - tensionDecay`)이 적절함.

---

### 2. Tick 루프 순서 재정렬 + isReeling 가드 제거

**파일**: `src/main/java/me/ninesik/fishing/fight/TrophyFightManager.java` (`tick()` 메서드)

```java
// 현재 순서 (문제점: Stamina/Distance/Tension이 isReeling 가드됨)
1. Fish AI             → 3. Power/Resistance
4. Player Input (isReeling)
5. Stamina             [if(isReeling)만]
6. Distance            [if(isReeling)만]  ← 문제: not reeling 시 distance 변화 없음
7. Tension
8. Reel State          [idle 시 회복]    ← 문제: idle 시 reelState 증가

// 수정 후 순서 (모든 계산을 항상 수행, isReeling는 수식 내부에서 처리)
1. Fish AI (staminaRatio 기반 상태 전이)
2. Power 계산
3. Resistance 계산
4. Player Input (isReeling 상태만 반영)
5. Reel State 계산  → reelState가 낮으면 reelEfficiency 감소
6. Stamina 계산     → reelEfficiency로 인해 자연스럽게 느려짐
7. Distance 계산    → staminaRatio + isReeling에 따라 증가/감소
8. Tension 계산     → isReeling에 따라 증가/감소 (항상)
```

구체적 변경:
```java
// 5. Reel State (순서 변경: Stamina보다 먼저)
double reelStateChange = calculator.calculateReelStateChange(
        session.getPower(), session.getResistance(), isReeling, session.getReelDurability());
session.changeReelState(reelStateChange);

// 6. Stamina (if(isReeling) 가드 유지 — 릴을 감을 때만 감소)
double staminaDecrease = calculator.calculateStaminaDecrease(
        session.getReelPower(), session.getReelState() / 100.0);
if (isReeling) {
    session.decreaseStamina(staminaDecrease);
}

// 7. Distance (if(isReeling) 가드 제거 — 항상 계산)
// staminaRatio를 재계산 (stamina이 방금 감소했으므로)
double newStaminaRatio = session.getStamina() / 100.0;
double distanceChange = calculator.calculateDistanceChange(
        session.getReelPower(), session.getPower(), session.getResistance(),
        newStaminaRatio, isReeling);
session.changeDistance(distanceChange);

// 8. Tension (if(isReeling) 가드 제거 — 항상 계산)
double tensionChange = calculator.calculateTensionChange(session.getPower(), isReeling);
session.changeTension(tensionChange);
```

---

### 3. FightSession: Reel State Clamping

**파일**: `src/main/java/me/ninesik/fishing/fight/FightSession.java`

- `maxReelState` 필드 추가 (초기값 = `defaultReelState`)
- `changeReelState()`에서 상한 clamp: `Math.min(maxReelState, Math.max(0, reelState + amount))`
- `initStats()`에서 `maxReelState` 설정

---

### 4. 입력 처리 개선 (FishingListener)

**파일**: `src/main/java/me/ninesik/fishing/listener/FishingListener.java`

현재: L=setReeling(true), R=setReeling(false) — 상태가 영구 유지됨

개선: **토글 방식**으로 변경
```java
// 현재: L → setReeling(true), R → setReeling(false)
// 변경: L → setReeling(true), R → setReeling(false) (유지)
// BUT: tick loop에서 isReeling을 매 tick 초기화하지 않음 (상태 유지)
```

실제로는 현재 구현이 이미 상태 기반:
- L 클릭 → `isReeling = true` (릴을 감는 상태)
- R 클릭 → `isReeling = false` (릴을 멈춤)
- 상태는 tick loop에서 읽히고, 매 tick 유지됨 (자동리셋 없음)

**문제**: `isReeling`이 `true`로 설정되면 영구 `true` — R 클릭으로만 `false` 전환 가능.
**해결**: 이는 의도된 동작 (hold L = reel, release = R). 하지만 **tick loop에서 자동으로 false로 리셋하지 않음**을 명시.

---

### 5. config.yml 값 검증

**파일**: `src/main/resources/config.yml` (`trophy-fight` 섹션)

| 항목 | 현재값 | 검증 |
|------|--------|------|
| `max-time-seconds` | 120 | 적절 (S 등급 레어 트로피도 충분한 시간) |
| `default-stamina` | 100.0 | 적절 (D grade: 100×0.1% = 0.3/tick → ~333틱) |
| `default-power` | 50.0 | FishAI가 override — 사용 안 함 |
| `default-distance` | 100.0 | 적절 (exhausted 상태에서 ~167틱으로 감소) |
| `default-reel-state` | 100.0 | 적절 (clamping 상한) |
| `particle-interval` | 2 | 적절 |
| `sound.interval` | 2 | 적절 |
| `grade-difficulty` | f:0.5 ~ s:2.5 | 적절 |

**상수 검증** (reelPower=30 테스트용 로드):
- D grade stamina: 100 × 1.0 = 100 → 100/0.3 = ~333틱 (16.7초)
- S grade stamina: 100 × 2.5 = 250 → 250/0.3 = ~833틱 (41.7초)
- S Rare Trophy: 100 × 2.5 × 1.5 = 375 → 375/0.3 = ~1250틱 (62.5초)
- max-time 120초 → S Rare Trophy도 완료 가능

---

### 6. 구현된 파일 목록 및 필요 변경

| 파일 | 변경 필요 | 상세 |
|------|----------|------|
| `FightCalculator.java` | ✅ | 4개 수식 수정 (stamina 100x 축소, distance 시그니처 변경, reel idle 0회복) |
| `TrophyFightManager.java` | ✅ | tick() 순서 재정렬, isReeling 가드 제거, staminaRatio 재계산 |
| `FightSession.java` | ✅ | maxReelState 필드 + clamping |
| `config.yml` | ❌ | 값 변경 불필요 (검증 완료) |
| 기타 | ❌ | FishingListener 입력 처리는 이미 상태 기반 (재검증 완료) |

---

## 실행 순서

1. `FightCalculator.java` — 수식 수정 (3개 메서드 변경)
2. `FightSession.java` — maxReelState 필드 + clamping
3. `TrophyFightManager.java` — tick() 재정렬 + isReeling 가드 제거
4. `./gradlew clean build` — 컴파일 검증
