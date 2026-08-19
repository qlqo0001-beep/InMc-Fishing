# 기존 코드 연동 지점

패키지: `me.ninesik.fishing.hud`. 아래 6곳만 손대면 됩니다.
기존 `fight/FightHUD` 는 그대로 둬도 되고(폴백으로 계속 사용), 지우고
`LegacyHudRenderer` 로 대체해도 됩니다.

---

## 1. 플러그인 본체 (`InMcFishing`)

```java
private FishingHudController fishingHud;

@Override
public void onEnable() {
    // ... 기존 초기화
    this.fishingHud = new FishingHudController(this);   // hud.yml 저장 + 리스너 등록까지 처리
    trophyFightManager.setHud(fishingHud);
    fishingService.getFishingMiniGame().setHud(fishingHud);
}

@Override
public void onDisable() {
    if (fishingHud != null) {
        fishingHud.shutdown();      // 남아 있는 HUD 전부 제거
    }
}

/** /fishing reload 처리부 */
public void reloadHud() {
    fishingHud.reload();
}
```

---

## 2. `TrophyFightManager.startFight(...)`

```java
if (!hud.showFight(player)) {
    fightHUD.show(player);      // hud.yml 의 fight.mode 가 vanilla 인 경우
}
```

인트로 연출(3·2·1 카운트다운)이 끝나고 실제 파이트가 시작되는 지점에서 호출합니다.

---

## 3. `TrophyFightManager.tick(...)` — 기존 9단계 "HUD 갱신" 자리

```java
FishState state = session.getFishAI().getCurrentState();
hud.fightData(player).set(
        session.getDistance(), session.getMaxDistance(),
        session.getStamina(),  session.getMaxStamina(),
        session.getTension(),  session.getMaxTension(),      // = 줄 강도(LineStrength)
        session.getReelState(), 100.0,
        state.name(),
        session.getStateRemainingTicks() / 20.0,             // 남은 시간(초)
        session.getStateRemainingTicks() / (double) session.getStateDurationTicks(),
        session.getReleaseCombo(), 'R');                     // 콤보 횟수 + 콤보 종류
hud.updateFight(player);
```

**콤보 종류** — 마지막 인자는 `'L'`(좌클릭 = 릴 감기 콤보) 또는 `'R'`(우클릭 = 릴 풀기 콤보)이고,
카드 오른쪽에 `4 / 좌클릭 콤보` 처럼 표시됩니다. 현재 `FightSession.releaseCombo` 는 우클릭 콤보라
`'R'` 을 넘기면 되고, 좌클릭 연타 콤보를 따로 세게 되면 그 값과 `'L'` 을 넘기면 됩니다.

```java
// 좌·우 콤보를 둘 다 세는 경우
boolean reeling = session.getReelCombo() > 0;
int combo = reeling ? session.getReelCombo() : session.getReleaseCombo();
char comboType = reeling ? 'L' : 'R';
```

- `updateFight` 는 `hud.yml` 의 `update-interval-ticks`(기본 2틱) 주기로만 실제 전송합니다.
  매 틱 호출해도 됩니다.
- 상태가 바뀌는 순간처럼 즉시 반영이 필요하면 `hud.flush(player)` 를 부릅니다.
- 남은 시간·지속시간 게터 이름이 다르면 그 자리만 바꿔주세요(값의 의미만 맞으면 됩니다).

---

## 4. `TrophyFightManager.stopFight(...)`

```java
hud.hide(player);
```

승리·패배(줄 끊김/거리 초과/릴 파손/시간 초과)·CANCELLED 등 **모든 종료 경로**가
이 메서드를 지나므로 여기 한 줄이면 충분합니다. 사망·월드이동·로그아웃은
컨트롤러 내부 리스너가 한 번 더 막아줍니다.

---

## 5. `FishingMiniGame` (L/R 클릭 미니게임)

```java
// 입질 → 시퀀스 생성 직후
if (!hud.showMinigame(player)) {
    // hud.yml 의 minigame.mode 가 vanilla 이면 여기로 온다 → 기존 액션바/타이틀 표시 그대로
    showLegacyMinigameDisplay(player);
    return;
}

// 시퀀스 표시 / 남은 시간 갱신 틱
hud.minigameData(player).set(sequence, currentIndex, remainingTicks / (double) totalTicks,
        MinigameHudData.Phase.PLAYING);
hud.updateMinigame(player);

// 클릭이 들어와 인덱스가 바뀐 순간
hud.flush(player);

// 성공/실패 연출
hud.minigameData(player).set(sequence, currentIndex, 0, MinigameHudData.Phase.SUCCESS);
hud.flush(player);

// stop() 마무리 — 파이트로 이어지면 showFight 가 이어받는다
hud.hide(player);
```

`sequence` 는 `char[]` 이고 `'L'`/`'R'` 만 봅니다(대소문자 무관).
클릭 수가 3회(F)든 9회(S)든 패널 폭이 자동으로 늘어나므로 개수 제한은 없습니다.

**바닐라로 되돌리기** — `hud.yml` 에서

```yml
minigame:
  mode: vanilla      # glyph | fallback | vanilla
```

로 두면 `showMinigame`/`updateMinigame` 이 아무것도 그리지 않고 `showMinigame` 이 false 를
돌려줍니다. 파이트만 글리프 HUD 로 쓰고 L/R 미니게임은 기존 방식 그대로 두고 싶을 때 씁니다.
(`hud.handles(player, HudMode.MINIGAME)` 로 미리 확인할 수도 있습니다.)

---

## 6. `plugin.yml`

```yml
softdepend: [ItemsAdder]
```

`depend` 가 아니라 `softdepend` 입니다 — ItemsAdder 가 없어도 플러그인은 정상 동작하고
HUD 만 폴백으로 내려갑니다.

---

## 기존 FightHUD 를 폴백으로 쓰고 싶다면

`LegacyHudRenderer` 를 지우고 `HudRenderer` 를 구현하는 얇은 어댑터를 만들어
기존 `FightHUD` 의 `show/updateBossBar/updateActionBar/updateStateTitle/hide` 를
그대로 호출하면 됩니다. `FishingHudController.renderer(Player)` 가 폴백을 고르는
유일한 자리이므로 그 반환값만 바꾸면 됩니다.
