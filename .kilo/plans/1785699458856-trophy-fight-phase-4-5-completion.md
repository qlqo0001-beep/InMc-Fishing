# Trophy Fight Phase 4-5 Completion Plan

## Goal

Complete the Trophy Fight system integration by fixing critical gaps that prevent the Fight from functioning, finishing remaining Phase 4 tasks (FishingListener wiring, PDC storage, renaming), and implementing Phase 5 (test command + final verification).

## Core Design Principle

**SRP**: `TrophyFightManager` owns ALL Fight logic — rod lookup, stats initialization, input tracking, success/failure resolution, reward delivery. `FishingMiniGame` changes by exactly one line. `FishingListener` checks `isInFight()` for bite blocking and input delegation. No new config structures. No custom service interfaces.

---

## Critical Gaps (Fight Does Not Actually Work)

| # | Gap | Impact |
|---|-----|--------|
| 1 | `startFight()` never calls `initStats()` — all stats start at 0.0 | Fight succeeds instantly (distance ≤ 0 && stamina ≤ 0) |
| 2 | Rod stats not available to `startFight()` — Rod not retrievable | Fight uses 0 for reelPower/lineStrength/reelDurability → no tension, no stamina decrease, no reel damage |
| 3 | Player input never connected — `isReeling` always false | Player can't reel; Fight is passive → always fails by timeout |
| 4 | `FightConfig` never loaded by TrophyFightManager | config.yml trophy-fight settings ignored |
| 5 | `FightState` starts in WAITING, never transitions to ACTIVE | Tick loop processes WAITING sessions with no ACTIVE transition |
| 6 | No max-time countdown or pause-when-stamina-0 | config.yml `max-time-seconds` unused; no timeout |
| 7 | No reward delivery on Fight end | Fight success doesn't give the fish to player |
| 8 | `RewardEntry` not passed to `startFight()` | No reward to deliver on success |

---

## Design Decisions

### 1. startFight Signature: `startFight(Player player, RewardEntry reward)`

- `RewardEntry` contains `fish`, `grade`, `isTrophy`, `isRareTrophy`, `size` — everything needed except rod stats.
- `FishingMiniGame.stop()` changes from `startFight(player, reward.getFish(), reward.getGrade())` to `startFight(player, reward)` — **one line**.
- `TrophyFightManager` extracts `fish` and `grade` internally via `reward.getFish()` / `reward.getGrade()`.

### 2. Rod Lookup via `Function<Player, Rod>` (Built-in Interface)

- No custom `RodProvider` interface. Uses `java.util.function.Function<Player, Rod>`.
- `FishingListener` exposes its existing `lookupRod()` logic via a public method `getRodForFight(Player)`.
- In `InMcFishing.onEnable()`, inject via method reference: `trophyFightManager.setRodLookup(fishingListener::getRodForFight)`.
- `TrophyFightManager.startFight()` calls `rodLookup.apply(player)` to get rod stats at Fight start.
- Falls back to zero-stat Rod if lookup returns null (player not holding a rod).

### 3. RewardService Direct Injection (No Callback)

- Inject `RewardService` into `TrophyFightManager` via constructor.
- On SUCCESS: `rewardService.giveReward(player, session.getReward())` — reuses net→inventory fallback, FishCatchEvent, messages, commands.
- On FAILED/CANCELLED: discard reward, show fail message.
- Eliminates `FightEndCallback` interface entirely.

### 4. ConfigManager Reference (Not SetFightConfig)

- `TrophyFightManager` holds a `ConfigManager` reference, calls `configManager.getFightConfig()` at `startFight()` time.
- Better reload support — on `/fishing reload`, `ConfigManager.load()` refreshes config, and next `startFight()` gets the new `FightConfig`.
- `FightConfig` is already implemented (AiConfig, HudConfig, SoundConfig, StatsConfig, GeneralConfig) — just not wired.

### 5. Existing Config Structure (No New Config Sections)

- Uses existing `trophy-fight` config.yml section as-is.
- `trophy-fight.stats.grade-difficulty.<grade>` multipliers (f:0.5, e:0.7, ... s:2.5) applied to base stats.
- `trophy-fight.stats.default-stamina/power/resistance/distance` used as base values.
- `trophy-fight.max-time-seconds` used for timeout.
- `trophy-fight.particle-interval` and `trophy-fight.sound.interval` used for render intervals.
- Rare Trophy: apply 1.5x difficulty multiplier on top of base (hardcoded in startFight, not config).

### 6. State Machine: WAITING → ACTIVE Immediately

- `FightSession` constructor sets `state = WAITING` (keep enum value).
- `startFight()` immediately calls `session.transitionTo(FightState.ACTIVE)`.
- No behavior change to tick loop — it already processes `!isFinished()` sessions.

### 7. timerPaused Field

- `FightSession` gets `boolean timerPaused` field.
- 패치예정.md §36/§103 specifies: "Stamina 0 이후에는 제한 시간 카운트다운도 함께 정지".
- Set `timerPaused = true` when `stamina <= 0`. Timer resumes only if stamina recovers (won't happen in Fight — stamina only decreases).
- `startTime` (already in constructor) used for elapsed time calculation.

---

## Data Flow

```
Player catches Trophy/Rare Trophy fish:
  FishingMiniGame.stop(SUCCESS)
    └─ trophyFightManager.startFight(player, reward)
         │  1. rodLookup.apply(player) → Rod (from FishingListener)
         │  2. Extract reelPower, lineStrength, reelDurability from Rod
         │  3. Create FightSession(uuid, snapshot, reward, startTime)
         │  4. session.transitionTo(ACTIVE)
         │  5. session.initStats(configDefaults × gradeDifficulty × trophyMultiplier)
         │  6. hud.showBossBar(player, session)
         │  7. restrictMovement(player)
         │
  Tick (TrophyFightManager.tick, 20TPS scheduler):
         │  For each ACTIVE session:
         │    1. FishAI.tick(staminaRatio)
         │    2. session.setPower/ai.getCurrentPower()
         │    3. session.setResistance/ai.getCurrentResistance()
         │    4. isReeling = session.isReeling() ← set by FishingListener
         │    5. Stamina decrease (if reeling, via FightCalculator)
         │    6. Distance change (if reeling, via FightCalculator)
         │    7. Tension change (FightCalculator)
         │    8. Reel State change (FightCalculator)
         │    9. Check timeout (max-time, paused when stamina=0)
         │   10. Check SUCCESS: distance ≤ 0 && stamina ≤ 0
         │   11. Check FAIL: tension ≥ lineStrength || reelState ≤ 0 || timeout
         │   12. HUD update (every tick)
         │   13. Particles/Sounds (every config.sound.interval / particle-interval)
         │   14. On SUCCESS → rewardService.giveReward(player, reward)
         │   15. On FAIL/CANCEL → discard reward, show fail message
         │   16. On end → remove session, hide HUD, release movement

Player input (left-click during Fight):
  FishingListener.onPlayerInteract(LEFT_CLICK)
    └─ if trophyFightManager.isInFight(player):
         session.setReeling(true)
         event.setCancelled(true)
```

---

## Class Responsibilities (SRP)

| Class | Responsibility |
|-------|---------------|
| `TrophyFightManager` | Owns entire Fight lifecycle: rod lookup (via Function), stats init, tick processing, input state, success/fail detection, reward delivery (RewardService), timeout/pause, HUD calls, movement lock |
| `FightSession` | Pure data model: UUID, FishSnapshot, FightState, RewardEntry, all numeric stats, reel stats, timerPaused, reeling flag. Clamped setters only. |
| `FishingMiniGame` | **Zero change** except calling `startFight(player, reward)` instead of `startFight(player, fish, grade)` |
| `FishingListener` | Event router: bite blocking during Fight, Fight input delegation, Fight cleanup on disconnect/death. Provides rod lookup method reference. |
| `RewardService` | Reward delivery (called by TrophyFightManager on success) |
| `FightConfig` | Config accessor for trophy-fight.yml section |
| `FishAI` | AI state machine (hardcoded values — functional) |
| `FightCalculator` | Pure math (hardcoded formulas — functional) |

---

## Task List

### Task A: TrophyFightManager — Core Rewiring

**Files**: `fight/TrophyFightManager.java`, `fight/FightSession.java`

1. **Add fields**: `ConfigManager configManager`, `RewardService rewardService`, `java.util.function.Function<Player, Rod> rodLookup`
2. **Change constructor** to accept `ConfigManager` and `RewardService` (keep `InMcFishing plugin`)
3. **Add setters**: `setRodLookup(Function<Player, Rod>)` (FishingMiniGame already has `setTrophyFightManager`)
4. **Change `startFight()`** to `startFight(Player player, RewardEntry reward)`:
   - Look up rod: `Rod rod = rodLookup != null ? rodLookup.apply(player) : null`
   - Extract stats: `reelPower = rod != null ? rod.getReelPower() : 0.0`, etc.
   - Create FightSession with state=WAITING
   - `session.transitionTo(FightState.ACTIVE)` 
   - Load `FightConfig` from `configManager.getFightConfig()`
   - Call `session.initStats(...)` with:
     - `stamina = config.stats().defaultStamina × gradeMultiplier × (isRareTrophy ? 1.5 : 1.0)`
     - `distance = config.stats().defaultDistance`
     - `tension = 0.0`
     - `reelState = config.stats().defaultReelState`
     - `reelPower/lineStrength/reelDurability` from rod
   - `session.setReward(reward)`
   - Show HUD, restrict movement
5. **Add `reward` field + getter** to `FightSession`
6. **Add `timerPaused` field** to `FightSession` + getter/setter

### Task B: Tick Loop — Reward Delivery, Timeout, Config Intervals

**Files**: `fight/TrophyFightManager.java`

1. **Load FightConfig** in tick() (or at startFight — cache is fine since reload recreates sessions)
2. **Replace** hardcoded `tickCount % 2 == 0` with `tickCount % fightConfig.general().particleInterval == 0`
3. **Add timeout logic**:
   ```java
   // After success/fail checks, add:
   if (!session.isTimerPaused() && fightConfig.general().maxTimeSeconds > 0) {
       long elapsedSec = (System.currentTimeMillis() - session.getStartTime()) / 1000;
       if (elapsedSec >= fightConfig.general().maxTimeSeconds) {
           session.transitionTo(FightState.FAILED);
           endFight(player, session, FightState.FAILED);
       }
   }
   ```
4. **Pause timer when stamina ≤ 0**:
   ```java
   if (session.getStamina() <= 0 && !session.isTimerPaused()) {
       session.setTimerPaused(true);
   }
   ```
5. **Extract `endFight()` method**:
   ```java
   private void endFight(Player player, FightSession session, FightState state) {
       sessions.remove(player.getUniqueId());
       hud.hideBossBar(player);
       releaseMovement(player);
       RewardEntry reward = session.getReward();
       if (state == FightState.SUCCESS && reward != null) {
           rewardService.giveReward(player, reward);
           // Fight success message
           player.sendMessage(configManager.getMessage("trophy-fight-success"));
       } else if (state == FightState.FAILED) {
           // Fight fail message
           player.sendMessage(configManager.getMessage("fail"));
       }
   }
   ```
6. **Update `stopFight()`**: When called with CANCELLED (from cleanup), use `endFight()` with reward discard

### Task C: FishingListener Wiring

**Files**: `listener/FishingListener.java`, `service/FishingService.java`, `InMcFishing.java`

1. **FishingListener**: Add `private TrophyFightManager trophyFightManager` field + `setTrophyFightManager()` setter
2. **FishingListener**: Add `public Rod getRodForFight(Player player)`:
   ```java
   public Rod getRodForFight(Player player) {
       RodLookupResult result = lookupRod(player);
       if (result instanceof RodLookupResult.Matched matched) {
           return matched.rod();
       }
       return UNREGISTERED_VANILLA_ROD;
   }
   ```
3. **FishingListener.onPlayerFish()**: Add Fight-active bite blocking (before mini-game check):
   ```java
   if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
       event.setCancelled(true);
       return;
   }
   ```
4. **FishingListener.onPlayerInteract()**: If `trophyFightManager.isInFight(player)`, delegate Fight input:
   - LEFT_CLICK → `session.setReeling(true)`, `event.setCancelled(true)`
   - RIGHT_CLICK → `session.setReeling(false)`, `event.setCancelled(true)`
5. **FishingListener.cleanupPlayer()**: Add Fight termination:
   ```java
   if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
       trophyFightManager.stopFight(player, FightState.CANCELLED);
   }
   ```
6. **FishingListener.gameEndListener**: Guard resumeCastStatusIfWaiting against Fight:
   ```java
   if (trophyFightManager != null && trophyFightManager.isInFight(player)) {
       return;
   }
   ```
7. **FishingService**: Add `getFishingListener()` getter
8. **InMcFishing.onEnable()**: After `trophyFightManager` creation, wire everything:
   ```java
   trophyFightManager.setRodLookup(fishingService.getFishingListener()::getRodForFight);
   fishingService.getFishingListener().setTrophyFightManager(trophyFightManager);
   // FishingMiniGame.setTrophyFightManager already at line 139
   ```

### Task D: FishingMiniGame One-Line Change

**Files**: `minigame/FishingMiniGame.java`

- Change line 279: `trophyFightManager.startFight(player, reward.getFish(), reward.getGrade())` → `trophyFightManager.startFight(player, reward)`

### Task E: RewardService PDC Storage

**Files**: `service/RewardService.java`

패치예정.md §1246-1257. Namespace `inmcfishing`, keys: `fish_id` (STRING), `grade_id` (STRING), `is_trophy` (BYTE), `is_rare_trophy` (BYTE), `fish_snapshot` (STRING/JSON).

1. **Add `applyPdc()`** method:
   ```java
   private void applyPdc(ItemMeta meta, Fish fish, Grade grade, boolean isTrophy, boolean isRareTrophy) {
       NamespacedKey fishIdKey = new NamespacedKey(plugin, "fish_id");
       NamespacedKey gradeIdKey = new NamespacedKey(plugin, "grade_id");
       NamespacedKey trophyKey = new NamespacedKey(plugin, "is_trophy");
       NamespacedKey rareKey = new NamespacedKey(plugin, "is_rare_trophy");
       NamespacedKey snapshotKey = new NamespacedKey(plugin, "fish_snapshot");
       
       meta.getPersistentDataContainer().set(fishIdKey, PersistentDataType.STRING, fish.getId());
       meta.getPersistentDataContainer().set(gradeIdKey, PersistentDataType.STRING, grade.getId());
       meta.getPersistentDataContainer().set(trophyKey, PersistentDataType.BYTE, (byte)(isTrophy ? 1 : 0));
       meta.getPersistentDataContainer().set(rareKey, PersistentDataType.BYTE, (byte)(isRareTrophy ? 1 : 0));
       meta.getPersistentDataContainer().set(snapshotKey, PersistentDataType.STRING, buildSnapshotJson(fish, grade));
   }
   ```
2. **Call `applyPdc()`** in both `createItemStack` overloads after meta is finalized
3. **For 3-arg `createItemStack(fish, amount, size)`**: Use `evaluateTrophyType()` result for isTrophy/isRareTrophy
4. **For 4-arg**: Use passed-in `isTrophy`/`isRareTrophy` values
5. **Build `fish_snapshot` JSON string** from Fish fields (id, name, gradeId, sizes, useType, doubleEnabled, customModelData)

### Task F: CollectionData/PendingReward Renaming

**Files**: `collection/PendingReward.java`, `collection/CollectionData.java`, `collection/CollectionStorage.java`, `collection/CollectionRewardService.java`, `collection/CollectionGui.java`

Rename: `PendingReward` → `PendingMilestoneReward`, `pendingRewards` → `pendingMilestoneRewards`.

**CRITICAL**: Keep YAML key `"pending-rewards"` unchanged — no data migration (패치예정.md §17, §21).

1. Create `PendingMilestoneReward.java` (copy of PendingReward, renamed class). Delete old `PendingReward.java`.
2. Rename field in `CollectionData`: `pendingRewards` → `pendingMilestoneRewards`, getter → `getPendingMilestoneRewards()`
3. Rename methods in `CollectionRewardService`: `addPendingReward` → `addPendingMilestoneReward`, `isPendingExpired` → `isPendingMilestoneExpired`
4. Update all type references and method calls in CollectionStorage, CollectionRewardService, CollectionGui
5. Do NOT touch `FishingMiniGame.pendingRewards` — different concept, kept per spec

### Task G: testfight Command

**Files**: `command/FishingCommand.java`

```
/fishing testfight <grade> <trophyType>
  grade: f/e/d/c/b/a/s
  trophyType: trophy/rare
```

1. Add `testfight` case to `onCommand()` switch (admin-only: `infishing.admin`)
2. `handleTestFight(sender, args)`:
   - Validate Player sender
   - Parse grade: `gradeRegistry.getById(args[1].toLowerCase())`
   - Parse trophyType: "trophy" → `isTrophy=true`, "rare" → `isRareTrophy=true`
   - Find first fish of that grade with size
   - Create `RewardEntry.builder().fish(fish).grade(grade).originalGrade(grade).size(avgSize).isTrophy(true).isRareTrophy(isRare).build()`
   - Call `plugin.getTrophyFightManager().startFight(player, reward)`
   - Send confirmation message
3. Add tab completion: args[1] = grade IDs, args[2] = "trophy"/"rare"

### Task H: Build & PROGRESS.md

1. `./gradlew build` (or `./gradlew.bat build` on Windows)
2. Fix any compilation errors
3. Update `PROGRESS.md`: mark session 29 complete, record changes

---

## Implementation Order

| Step | Tasks | Rationale |
|------|-------|-----------|
| 1 | A + B | Core Fight functionality. startFight signature, stats init, tick loop with reward delivery, timeout. Must be done together. |
| 2 | C | FishingListener wiring (bite blocking, input, cleanup, rod lookup injection). Depends on A's startFight signature. |
| 3 | D | One-line FishingMiniGame change. Depends on A. |
| 4 | E | PDC storage. Independent — additive to RewardService. |
| 5 | F | PendingReward renaming. Independent — no Fight dependencies. |
| 6 | G | testfight command. Depends on A (startFight signature). |
| 7 | H | Build + PROGRESS.md. Final step. |

---

## Pre-Implementation Verification Results

| Item | Status | Details |
|------|--------|---------|
| 1. config.yml message keys | VERIFIED | `fail` message exists at line 277. No `trophy-fight-success` key needed — `RewardService.giveReward()` sends `caught` message automatically on reward delivery. `fail` message reused for Fight failure. No config changes needed. |
| 2. Rare Trophy 1.5x multiplier | DECISION-NEEDED | 패치예정.md says "Rare Trophy가 더 어려움" / "확연히 높아야 한다" but does NOT specify a numeric multiplier. 1.5x is an implementation choice — document in code comment + PROGRESS.md. |
| 3. startFight call sites | VERIFIED | Only ONE call site: `FishingMiniGame.java:279`. Change to `startFight(player, reward)`. |
| 4. RewardEntry in FightSession | VERIFIED SAFE | RewardEntry is immutable (final fields, Builder pattern). Contains Fish/Grade registry refs valid for Fight duration (max 120s). Used only at Fight end for `rewardService.giveReward()`. No lifecycle issues. |
| 5. timerPaused field | VERIFIED | 패치예정.md §36/§103/§803 explicitly states "Stamina ≤ 0 조건이 충족되면 카운트다운을 멈춘다" (timer pause when stamina=0). `timerPaused` boolean is valid implementation. Stamina only decreases in Fight, so pause is permanent once set. |

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `initStats()` values unbalanced → Fight too easy/hard | Use config grade-difficulty multipliers (f:0.5 to s:2.5). Rare Trophy gets 1.5x modifier (DECISION-NEEDED — not in spec). Test with `/fishing testfight`. |
| Player logs out during Fight → session leak | Task C: cleanupPlayer calls stopFight(CANCELLED). TrophyFightManager.shutdown() also cleans up. |
| rodLookup null (FishingListener not wired yet) | startFight handles null rod → zero stats. Fight will fail fast. Acceptable until wiring is complete. |
| RewardService.giveReward() called in tick (main thread) | Bukkit scheduler tick task runs on main thread. Safe. |
| FishingMiniGame.gameEndListener resumes cast status during Fight | Task C: guard with `isInFight(player)` check in gameEndListener callback. |
| FishCatchEvent not fired on Fight success | `RewardService.giveReward()` already fires FishCatchEvent. Fight success reuses the same pipeline. |
| Fight success during mini-game stop() → gameEndListener fires → resumeCastStatusIfWaiting | Task C guard prevents this. Cast status should NOT resume during Fight. |
