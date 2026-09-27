# InMc-Fishing 릴리즈 노트

> 이 문서는 플러그인 버전별 릴리즈 노트를 정리한다. (최신 → 과거)
>
> **버전 규칙**: `1.0.0` = 최초 전체 기능 완성 기준선.
> 이후 **메이저(큰 기능) 패치**는 `1.x.0`으로 마이너를 올리고,
> 그 아래 **일반(작은) 패치**는 `1.x.y` 순서로 이어 붙인다
> (예: 1.4.0 → 1.4.1 · 1.4.2 … → 다음 메이저 1.5.0 → 1.5.1 …).
>
> 전체 작업 이력은 `PROGRESS.md`, 아카이브는 `PROGRESS_ARCHIVE.md` 참고.

## 요약 (버전 이력)

| 버전 | 날짜 | 구분 | 한 줄 요약 |
|---|---|---|---|
| **1.6.1** | 2026-08-22 | 🛠 패치 | 실패 3종에 유예 시간(회복 창) 추가 + 배포 기본값을 실서버 튜닝값으로 교체 |
| **1.6.0** | 2026-08-22 | ✨ 메이저 | 트로피 파이트 그래픽 HUD 신설 (BetterHud) — 위험 경고 3종 + 좌/우 콤보 |
| **1.5.1** | 2026-08-09 | 🛠 패치 | 가공 취소·give bait/fillet·GUI 개편·실패 메시지 버그 수정 |
| **1.5.0** | 2026-08-08 | ✨ 메이저 | SQLite DB·미끼(Bait)·생선 살(Fillet) 시스템 신설 |
| **1.4.9** | 2026-08-07 | 🛠 패치 | STUNNED(기절) 상태 Distance 회수 속도 개선 |
| **1.4.8** | 2026-08-05 | 🛠 패치 | 낚싯대 로어 포맷 개편 + item-format.yml 설정 신설 |
| **1.4.7** | 2026-08-05 | 🛠 패치 | 대회 시작 메시지 설정화 + 트로피 물고기 도감 왕복 소실 수정 |
| **1.4.6** | 2026-08-05 | 🛠 패치 | 리로드 시 신규 아이템(물고기·낚싯대) 미인식 버그 수정 |
| **1.4.5** | 2026-08-05 | 🛠 패치 | 릴 내구도(릴 HP) 좌클릭 소모 계수 하향 |
| **1.4.4** | 2026-08-05 | 🛠 패치 | 좌클릭 거리 회수 — 낚싯대 Reel Power 보너스 ×0.1 반영 |
| **1.4.3** | 2026-08-05 | 🛠 패치 | 접속/퇴장 시 파일 IO 비동기화 (핑·트래픽 개선) |
| **1.4.2** | 2026-08-05 | 🛠 패치 | Trophy Fight 심층 리뷰 반영 (이동 제한 복원 등) |
| **1.4.1** | 2026-08-05 | 🛠 패치 | Action Power 결함 수정 |
| **1.4.0** | 2026-08-05 | ✨ 메이저 | 행동력(Action Power) 시스템 신설 |
| **1.3.0** | 2026-08-04 | ✨ 메이저 | Trophy Fight 상태 확장 + 상태별 사운드 + 문서 |
| **1.2.2** | 2026-08-04 | 🛠 패치 | 설정 파일 역할 분리 + `/fishing reload` 강화 |
| **1.2.1** | 2026-08-04 | 🛠 패치 | 설정 명칭 정정 + GUI 미니게임 ON/OFF |
| **1.2.0** | 2026-08-03 | ✨ 메이저 | Trophy Fight 인트로/카운트다운 연출 |
| **1.1.0** | 2026-08-03 | ✨ 메이저 | 메인 GUI 신설 |
| **1.0.0** | 2026-08-03 | 🏷 기준 | 최초 전체 기능 완성 |

---

---

## [1.6.1] - 2026-08-22 — 🛠 패치

**실패 3종에 유예 시간(회복 창) 추가** — 경고를 보고 반응하면 만회할 수 있게.

### ⏱️ 실패 유예 (회복 창)
1.6.0 에서 위험 경고 3종을 붙였지만, 파이트 수치는 **1틱(0.05초)마다** 계산되어
한계에 닿는 그 틱에 즉시 실패했다. 경고를 띄워놓고 반응할 틈은 안 주는 셈이었다.

- ✨ **실패 조건에 도달해도 유예(기본 0.3초) 안에 수치를 되돌리면 실패하지 않는다**
- 🔁 조건에서 벗어나면 타이머가 초기화되고, 다시 도달하면 처음부터 다시 센다
- 🧭 실패 원인별로 **독립된 타이머** — 셋이 동시에 돌 수 있고, 먼저 만료된 쪽이 실패 원인이 된다
- 🏆 승리 판정이 여전히 우선 — 거리 유예가 도는 중에 역전해서 이기면 승리로 끝난다
- ⚠️ 유예 중에도 수치는 계속 움직인다. **판정을 미룰 뿐 수치를 얼려주지 않으므로**,
  실제로 조건 아래로 되돌려야 살아남는다

| 실패 조건 | 판정 | 유예 중 회복 수단 |
|---|---|---|
| 줄 끊김 | `Tension ≥ LineStrength` | 우클릭(릴 풀기)으로 장력 감소 |
| 물고기 도망 | `Distance ≥ MaxDistance` | 좌클릭(릴 감기)으로 거리 감소 |
| 릴 파손 | `ReelState ≤ 0` | 클릭을 멈추면 자연회복, 우클릭이면 더 빠르게 |

제한 시간 초과(`TIMEOUT`)는 유예 대상이 아니다 — 그대로 즉시 종료된다.

### ⚙️ 새 설정 키 (`fight.yml`)
```yaml
trophy-fight:
  fail-grace:
    line-snapped-millis: 300       # 장력이 줄 강도에 도달
    distance-exceeded-millis: 300  # 거리가 최대 거리에 도달
    reel-broken-millis: 300        # 릴 내구도 0
```
셋 다 **0 으로 두면 1.6.0 이전처럼 닿는 즉시 실패**한다 (유예 기능 끄기).

### 🖥️ HUD — 유예 전용 최종 경고
- ✨ 위험도 플레이스홀더에 **`3` = 유예 중(곧 실패)** 단계 추가
  (`fight_danger` / `fight_distance_danger` / `fight_reel_danger`)
- 3으로 올라가면 기존 `== 2` 조건이 저절로 꺼져서, 일반 경고 → 최종 경고 전환이
  HUD 요소마다 조건 하나로 배타 처리된다
- 🔴 유예 동안 해당 게이지만 **점멸이 3틱 → 1틱으로 빨라지고** 최종 경고문으로 바뀐다
  (`!!! 줄 끊어짐 !!!` / `!!! 줄 길이 한계 !!!` / `!!! 릴 파손 !!!`)
- 🖼️ **새 이미지는 만들지 않았다** — 기존 알림 PNG 를 프레임 간격만 줄여 재사용한다.
  유예가 0.3초(6틱)라 글자를 읽을 시간이 없고, 그 순간 눈에 들어오는 건 점멸 속도 변화다

### ⚖️ 배포 기본값을 실서버 튜닝값으로 교체
`fight.yml` 의 기본값 **76줄**을 운영 서버에서 실제로 굴려보며 맞춘 값으로 갈아끼웠다.
지금까지 배포본은 Phase 6 이관 당시의 코드 기본값 그대로였고, 실제로 쓰이는 값과 크게 벌어져 있었다.

- ⚖️ 파이트 전반이 **길고 완만해진다** — 거리 회수·장력 계수를 대체로 낮췄다
  (`bonus-reel-coefficient` 0.105 → 0.025, `release-tension-per-combo` 2.0 → 0.5,
  `fish-escape-coefficient` 0.004 → 0.002)
- 🎣 상태별 `reel-distance-multiplier` / `release-distance-multiplier` 재조정 (10종)
- ⌨️ 입력 유예 4종 250ms → **300ms**
- 💬 `state-guide` 안내 문구 12개 교체
- 🔁 좌클릭 콤보 계수를 0 → `reel-distance-per-combo: 0.001` / `reel-stamina-per-combo: 0.01`
  로 켜서 배포한다 (1.6.0에서는 표시만 되고 효과가 없었다)

### ⚠️ 업그레이드 시 주의
- 🖥️ **`hud.mode` 기본값이 `vanilla` → `betterhud` 로 바뀐다.** BetterHud 가 없는 환경에서는
  파이트 HUD 가 아예 보이지 않으므로(보스바·액션바도 함께 억제됨) `vanilla` 로 되돌려야 한다
- 🔧 자바 하드코딩 fallback 은 이번에 맞추지 않았다. `fight.yml` 에서 **키를 지우면**
  그 항목만 옛 값으로 조용히 되돌아간다 (예: `release-tension-per-combo` 를 지우면 0.5가 아니라 2.0).
  값을 바꾸고 싶으면 키를 지우지 말고 숫자를 고칠 것
- 🗑️ **ItemsAdder 글리프 HUD 코드는 이 빌드에 없다.** 1.5.1 이후 저장소에 따로 올라갔던
  ItemsAdder HUD(`hud/` 패키지, `hud.yml`, `hud_sprites.yml`)는 BetterHud 방식으로 대체되어 제거했다
- 📐 `distance-danger-percent` 는 80 이다. HUD 거리 트래커 그림의 빨간 구간은 85% 지점에서
  시작하므로, 경고가 마커보다 살짝 먼저 뜬다 (의도된 조기 경고)

#### 파일
- **수정 7개**: `FightConfig.java`(GeneralConfig 유예 3키 + `failGraceMillis()`),
  `FightSession.java`(`tickFailGrace` / `isInFailGrace` 래치), `TrophyFightManager.java`(판정 3곳),
  `FishingPlaceholderExpansion.java`(위험도 3단계), `fight.yml`(유예 키 + 기본값 76줄 교체), `plugin.yml`, `build.gradle.kts`(버전 1.6.1)
- **삭제**: ItemsAdder 글리프 HUD — `hud/` 패키지 13개, `hud.yml`, `hud_sprites.yml`
  (연결 코드가 있던 `InMcFishing`, `FishingMiniGame`, `FishingService` 도 함께 정리)
- 🖼️ HUD 리소스(`images/` `layouts/`)는 서버의 `plugins/BetterHud/` 에 있어 이 저장소 밖이다
- ✅ 빌드 `BUILD SUCCESSFUL` · 유예 로직은 기존 밸런스 수식·조건식을 건드리지 않는다 (판정 타이밍만 늦춤)

---

## [1.6.0] - 2026-08-22 — ✨ 메이저

**트로피 파이트 그래픽 HUD 신설 (BetterHud 연동)** — 보스바·액션바·타이틀 텍스트를 전용 그래픽 HUD로 대체하고, 실패 조건 3가지에 각각 위험 경고를 붙였다.

### 🖥️ 그래픽 HUD 신설
- ✨ **파이트 전용 HUD 6블록** — 거리 트래커 · 물고기 상태 카드 · 콤보 · 물고기 체력 · 낚싯줄 장력 · 릴 내구도
- ✨ `trophy-fight.hud.mode: betterhud` 로 전환. `vanilla` 로 되돌리면 **즉시 기존 텍스트 HUD로 롤백**된다
  (리소스팩을 못 받은 유저가 많거나 문제가 생겼을 때의 탈출구)
- 🔌 **연동 창구는 PlaceholderAPI 하나뿐** — 플러그인은 BetterHud 에 컴파일·런타임 의존을 갖지 않는다.
  BetterHud 가 `%inmcfishing_fight_*%` 를 틱 단위로 읽어 스스로 렌더링한다
- 🎬 시작 인트로(`!!!` / 3 / 2 / 1 / `START!!`) 연출은 HUD 모드와 무관하게 유지

### 🚨 위험 경고 3종
파이트 실패 조건은 셋인데 그동안 장력에만 경고가 있었다. 나머지 둘을 채우고 임계값을 전부 설정으로 뺐다.

| 실패 조건 | 경고 시점 | 연출 |
|---|---|---|
| 줄 끊김 (`Tension ≥ LineStrength`) | **75%** (기존 80%) | 장력 게이지 테두리 점멸 + `!! 줄이 끊어지기 직전 !!` |
| 물고기 도망 (`Distance ≥ MaxDistance`) | **85%** ✨신설 | 거리 트래커 테두리 점멸 + `!! 줄이 끊어질 거리 !!` |
| 릴 파손 (`ReelState ≤ 0`) | **30% 미만** ✨신설 | 릴 게이지 테두리 점멸 + `!! 릴이 부서지기 직전 !!` |

- 🔧 임계값 4개를 `trophy-fight.hud.*` 로 이관 — 이전엔 `FishingPlaceholderExpansion` 과 `FightHUD` 두 곳에
  `0.8` / `0.5` 가 따로 하드코딩돼 있어 한쪽만 고치면 어긋났다
- 📐 거리 기본값 85% 는 HUD 거리 트래커 그림의 빨간 위험 구간이 시작되는 지점과 맞춘 값이다
- ✨ 신규 플레이스홀더 `%inmcfishing_fight_distance_danger%` · `%inmcfishing_fight_reel_danger%` (0=안전, 2=위험)

### 🔁 좌클릭(릴 감기) 콤보 신설
- ✨ 우클릭 콤보와 **완전 대칭**으로 좌클릭 연타 콤보 추가. 반대쪽 클릭이 들어오면 서로를 리셋한다
- 🖥️ 동시에 쌓일 수 없으므로 HUD 는 **같은 자리에서 교대 표시** — 우클릭 `릴 풀기 콤보`(청록) / 좌클릭 `릴 감기 콤보`(초록)
- ⚖️ 효과 2종(추가 거리 감소 · 추가 물고기 체력 감소)을 넣되 **계수 기본값은 0** —
  켜기 전까지 기존 밸런스와 수치가 완전히 동일하다. 어드민이 `fight.yml` 에서 조금씩 올려 쓰는 방식
- ✨ 신규 플레이스홀더 `%inmcfishing_fight_reel_combo%`

### ⚙️ 새 설정 키 (`fight.yml`)
```yaml
trophy-fight:
  hud:
    tension-danger-ratio: 0.75     # 장력 위험(빨강·점멸) 시작 비율
    tension-warning-ratio: 0.5     # 장력 주의(노랑) 시작 비율
    distance-danger-percent: 85    # 거리 경고 시작 % (HUD 빨간 구간과 일치)
    reel-danger-percent: 30        # 릴 내구도 경고 시작 % (미만일 때)
  input:
    reel-combo-window-millis: 250  # 좌클릭 연타가 콤보로 이어지는 간격
    max-reel-combo: 10             # 좌클릭 콤보 상한
  calc:
    reel-distance-per-combo: 0.0   # 좌클릭 콤보 1당 추가 거리 감소 (0 = 표시만)
    reel-stamina-per-combo: 0.0    # 좌클릭 콤보 1당 추가 물고기 체력 감소 (0 = 표시만)
```

### ⚠️ 업그레이드 시 주의
- 🗑️ **`plugins/InMc-Fishing-1.5.1-all.jar` 를 반드시 삭제**하고 1.6.0 jar 을 넣을 것.
  파일명이 달라 두 개가 함께 로드된다
- 🖼️ **HUD 리소스(BetterHud 설정·이미지)는 이 저장소에 없다.** 서버의 `plugins/BetterHud/`
  (`huds/` `layouts/` `images/` `texts/` `assets/inmc_fight/`) 에 따로 있고, 플러그인이 설치하지 않는다
- 📌 HUD 이미지는 한 장이 **가로·세로 256px 를 넘으면 안 된다** — 마인크래프트 글리프 아틀라스
  (`FontTexture` 256×256) 에 못 들어가 그 이미지만 조용히 사라진다 (로그도 안 남는다)

#### 파일
- **수정 7개**: `FishingPlaceholderExpansion.java`, `FightConfig.java`, `FightSession.java`,
  `FightCalculator.java`, `TrophyFightManager.java`, `FightHUD.java`, `fight.yml`
- ✅ 빌드 `BUILD SUCCESSFUL` · 기존 밸런스 수식은 한 줄도 건드리지 않았다 (신규 계수 기본 0)

#### 이 릴리즈에 함께 포함된 것
1.5.1 이후 HUD 작업과 별개로 진행된 **대규모 안정화·설정화 작업(Phase 0~8)** 도 이 빌드에 들어 있다 —
DB 안전성, 아이템 손실 차단, 메인 스레드 블로킹 제거, 파이트 밸런스 수식 `fight.yml` 이관,
하드코딩 문구 전면 이관, PlaceholderAPI·WorldGuard 실동작화 등. 자세한 내용은 `REVIEW-2026-08.md` 참고.

---

## [1.5.1] - 2026-08-09 — 🛠 패치

**생선 살 가공 개선 · GUI 개편 · 명령어 확장 · 버그 수정** (세션 45~51)

### 🐟 생선 살 가공 개선
- ✨ **가공 취소**: 가공 중 슬롯 `시프트 우클릭` → 원본 물고기 반환 (완료 슬롯은 수령만 가능)
- ✨ **물고기 등록**: 인벤토리 물고기 클릭 → 빈 슬롯 자동 등록 (어망/도감과 동일 패턴)
- 🗑️ 생선 살 `수량` 로어 제거 — 스택 분할 시 로어 불일치 문제 해소
- 📐 **가공 GUI 확장**: 3행 → **6행(54칸 최대)**, 가공 슬롯 10 → **35개**, 잠긴 배리어 → 확장 힌트 1개

### 🖥️ GUI 개편
- ✨ 메인 GUI: 5행 → **3행** 기능별 그룹화, 스탯 아이콘 → **플레이어 머리**
- ✨ 메인 GUI ↔ 가공 GUI 상호 이동 (가공 진입 슬롯 / "메인 GUI로" 버튼)

### ⌨️ 명령어
- ✨ `/fishfillet` → **`/fishing fillet`** 통합 (별칭: 필렛, 가공)
- ✨ `/fishing give bait <플레이어> <baitId> [개수]` — 미끼 지급
- ✨ `/fishing give fillet <플레이어> <등급> <normal|trophy|rare> [개수]` — 생선 살 지급
- ✨ 탭 자동완성 지원 (baitId / 등급 / 타입)

### 🗄️ DB 전면 전환
- ⚙️ 플레이어 데이터 완전 SQLite화 — YML 폴더(`collections/`, `net/`, `preferences/`)·마이그레이션 코드 제거 (신 버전 전용)

### 🐛 버그 수정
- 🐛 낚시 실패 시 `MemorySection[path='fail'...]` 출력 → 정상 메시지 + ConfigManager 섹션 가드

#### 파일
- **수정**: `FilletGui`, `FilletManager`, `FilletStorage`, `MainGui`, `FishingCommand`, `DatabaseManager`, `CollectionStorage`, `NetStorage`, `FatigueManager`, `PlayerPreferenceManager`, `RewardService`, `ConfigManager`, `GuiListener`, `InMcFishing`, `plugin.yml`
- **삭제**: `FilletCommand.java`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 45~51)

## [1.5.0] - 2026-08-08 — ✨ 메이저

**SQLite DB 시스템 · 미끼(Bait) · 생선 살(Fillet) 시스템 신설** (4개 세션 통합)

### 🗄️ SQLite DB 시스템 구축
- ✨ **단일 SQLite DB(`playerdata.db`)** 로 플레이어 데이터 통합 — WAL 모드, 8개 테이블
- ✨ 기존 YML(`collections/`, `net/`, `preferences/`) → SQLite **자동 마이그레이션** (완료된 파일은 `.migrated/` 백업)
- ✨ 테이블: `player_data`(설정·피로도), `collection_entries/sizes/rewards`, `pending_rewards`, `net_entries`, `fillet_slots`, `fillet_active`

### 🎣 미끼(Bait) 시스템
- ✨ **11종 기본 미끼** — 사이즈 증가 4종(+5%~+20%), 등급 조정 3종(D~S), 복합형 3종
- ✨ 왼손(오프핸드) 장착 → 입질 시 1개 **자동 소모**, 효과 적용
- ✨ 등급 확률 보정: 낚싯대와 동일한 **덧셈 방식** (`finalWeight += baitBonus`)
- ✨ 사이즈 보정: **% 적용 후 고정값**, 트로피 판정 전 적용, **어드민 최대크기 초과 허용**
- ✨ 바닐라/MMOItems 연동 지원 (`lookupRod`와 동일한 `lookupBait` 패턴)

### 🐟 생선 살(Fish Fillet) 시스템 (1차)
- ✨ **21종 생선 살** (7등급 F~S × 일반/트로피/레어트로피)
- ✨ `/fishfillet` 명령어 (별칭: `/필렛`, `/ff`) — **3슬롯 가공 GUI**
- ✨ PDC 연동 — 물고기 아이템의 `isTrophy`/`isRareTrophy` 자동 인식
- ⏱️ 가공 시간: 등급(F10→S180초) + 트로피 배율(×1.5/×2.0) + 희귀도 계수 + 수량
- 📦 산출량: `max(1, round(3 × (등급최대weight / fishWeight)))` — 희귀할수록 많이
- 💾 서버 재시작 후에도 가공 상태 복원 (SQLite 기반)

### 🛠 기타
- ✨ 낚싯대 로어 등급 확률 **F→S 순서**로 고정 표기

#### 파일
- **신규 11개**: `DatabaseManager.java`, `Bait.java`, `BaitLoader.java`, `BaitRegistry.java`, `FilletConfig.java`, `FilletStorage.java`, `FilletManager.java`, `FilletGui.java`, `FilletCommand.java`, `bait.yml`, `fillet-items.yml`
- **수정 16개**: `build.gradle.kts`, `RegistryManager.java`, `WeightCalculator.java`, `GradeRoller.java`, `RollEngine.java`, `FishingListener.java`, `FishingService.java`, `GuiListener.java`, `RodLoader.java`, `FishingCommand.java`, `InMcFishing.java`, `config.yml`, `plugin.yml`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 42~44)

## [1.4.9] - 2026-08-07 — 🛠 패치

**STUNNED(기절) 상태 Distance 회수 속도 개선** (피드백 반영)

- 🐛 수정: 물고기가 기절(STUNNED)하면 끌려오는 거리가 너무 빨라지던 문제
- 🔧 원인: STUNNED는 Power/Resistance가 동시에 0인 유일한 상태라 Distance 회수 계수(resistance·exhaustion)가 항상 최댓값 → 전체 상태 중 가장 빠르게 Distance가 감소
- ✨ 수정: `calculateDistanceChange()`에 STUNNED 전용 독립 분기 + 신규 상수 `STUNNED_PULL_COEFFICIENT = 0.03`로 별도 제어
  - 릴 감기 시 `reelPower × 0.03`/틱 (기본 낚싯대 기준 초당 ~18)
  - 릴 안 감으면 0.0 (기존과 동일 — 회귀 없음)
  - 예상: 등급 하한 F(50m) 약 **2.8초**, S(120m) 약 **6.7초** (기존 0.6~1.4초)
- 📄 `FightCalculator.java`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (기존 deprecated 경고만) (세션 41)

## [1.4.8] - 2026-08-05 — 🛠 패치

**낚싯대 아이템 로어 포맷 개편 + 전용 설정 파일(item-format.yml) 신설** (피드백 반영)

- ✨ 낚싯대 로어를 **섹션별**로 정리 — 등급 보너스 / 낚시 옵션(더블·대어) / 트로피 파이트 옵션(릴 파워·줄 강도·릴 내구도) / 피로도
- ✨ 등급 보너스는 등급별 한 줄 나열 (`  §8▸ §7F 등급   §c-10`), 양수=등급 색·음수=§c, 0 아닌 등급만(설정으로 전부 표시 가능)
- ✨ 구분선 `&8━━━━━━━━━━━━━━━━━━━━` + 상단/하단 구분선 배치 (표시명 ✦는 관리자가 직접 설정)
- ✨ **신규 `item-format.yml`** — 구분선/섹션 제목/접두사/값 구분자/색상/라벨/0-표시 여부를 관리자가 수정 가능, `/fishing reload` 반영
- 📄 `item-format.yml`(신규), `ItemFormatConfig.java`(신규), `ConfigManager.java`, `FishingCommand.java`, `InMcFishing.java`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (기존 deprecated 경고만) (세션 40)

## [1.4.7] - 2026-08-05 — 🛠 패치

**대회 시작 메시지 설정화 + 트로피 물고기 도감 왕복 소실 수정** (피드백 반영)

- ✨ 대회 시작 공지를 `messages.yml` `tournament.start` / `tournament.start-fee`로 설정화 (`{name}` · `{fee}` · `{prefix}` 플레이스홀더, `&` 색상 코드 지원)
- 🐛 수정: 대회 시작 시 `&e`/`<...>` 색상 코드가 채팅에 그대로 표기되던 문제 → `ConfigManager.formatMessage`로 `&`→`§` 변환 (설정 비면 기존 문구로 폴백)
- 🐛 수정: 트로피 물고기를 도감에 넣었다 다시 빼면 트로피가 사라지던 문제 → 도감 등록 시 사이즈를 PDC에서 읽도록 변경 (`readFishItemData` 우선, 레거시는 로어 폴백) → 인벤토리 등록에도 실제 사이즈 보존, 해제 시 트로피 유지. (로어엔 여전히 사이즈 미표시, 어망 GUI에서만 표시)
- 📄 `messages.yml`, `TournamentManager.java`, `CollectionManager.java`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (기존 deprecated 경고만) (세션 39)

## [1.4.6] - 2026-08-05 — 🛠 패치

**리로드 시 신규 아이템(물고기·낚싯대) 미인식 버그 수정** (피드백 반영)

- 🐛 수정: `/fishing reload` 후 새로 추가한 물고기·낚싯대가 인식되지 않던 문제
- 🔧 근본 원인: `reloadRegistries()`가 새 레지스트리로 `RegistryManager`의 AtomicReference만 **스왑**했으나, 낚시 실행 경로 서비스들이 생성 시점에 레지스트리를 `final` 필드로 **캡처** → 옛 레지스트리 사용 중
- ✨ 수정: 레지스트리를 non-final로 바꾸고 setter를 추가, 리로드 시 새 레지스트리를 모든 보유 컴포넌트에 재주입(re-seed)
  - `FishingService.refreshRegistries` → 롤(RollEngine·등급·물고기), 미니게임(등급), 리스너(낚싯대·물고기), 피로도(낚싯대)
  - `InMcFishing.refreshRegistries` → 도감(물고기), 어망(물고기), 명령어(등급·물고기·낚싯대)
- 📄 `FishingService.java`, `InMcFishing.java`, `RollEngine.java`, `GradeRoller.java`, `RewardRoller.java`, `FishingListener.java`, `FishingMiniGame.java`, `PlayerFatigueManager.java`, `FishingCommand.java`, `CollectionManager.java`, `NetManager.java`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (기존 deprecated 경고만) (세션 38)

## [1.4.5] - 2026-08-05 — 🛠 패치

**릴 내구도(릴 HP) 밸런스 — 좌클릭 소모 계수 하향** (피드백 반영)

- ⚙️ `FightCalculator.calculateReelStateChange()` 좌클릭(릴 감기) Reel State 소모 계수 `0.01 → 0.006` 하향
- 🐛 수정: 기본 낚싯대(내구 30) 기준 릴 HP 100→0 이 ~6.5초 → **~10.8초**, S급(난이도 2.5) ~2.6초 → **~4.3초**
- ✅ 회복 수식은 유지 — idle `0.0015` / 우클릭(릴 풀기) `0.008`
- 📄 `FightCalculator.java`, `TROPHY_FIGHT.md`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 36)

## [1.4.4] - 2026-08-05 — 🛠 패치

**좌클릭 거리 회수 — 낚싯대 Reel Power 보너스 반영** (피드백 반영)

- ✨ 거리 회수 유효 Reel Power = `기본 30 + (낚싯대 보너스 × 0.1)` (`ROD_BONUS_DISTANCE_RATIO = 0.1`)
- 🐛 수정: 낚싯대가 좋을수록 거리 회수가 조금 더 빨라짐 — "거리 감소가 너무 안 되는" 문제 완화
- ✅ 기본 낚싯대(보너스 0)/미등록 낚싯대는 기존 30 그대로 → 기존 밸런스 유지
- ✅ 스태미너 감소 속도에는 실제 Reel Power가 그대로 반영
- 📄 `TrophyFightManager.java`, `FightCalculator.java`, `TROPHY_FIGHT.md`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 35)

## [1.4.3] - 2026-08-05 — 🛠 패치

**플레이어 접속/퇴장 시 파일 IO 비동기화** (회귀/성능)

- ⚙️ 접속·퇴장 시 파일 저장 IO를 비동기로 전환 → 플레이어 접속 핑·트래픽 급증 해결
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 34)

## [1.4.2] - 2026-08-05 — 🛠 패치

**Trophy Fight 심층 리뷰 반영**

- 🐛 Fight 종료 시 이동/비행 상태 원래값 복원 (`MovementSnapshot`)
- 🐛 릴 효율 비율을 최대치 기준으로 정정 (`getReelState()/getMaxReelState()`)
- 🧹 죽은 코드 제거 (`rodBonusReelPower`)
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 33)

## [1.4.1] - 2026-08-05 — 🛠 패치

**Action Power 결함 수정** (검토 반영)

- 🧹 죽은 config 제거 — `state-cost`/`state-tension-rate` (소비처 없음) → enum 값이 단일 소스
- 🐛 `FishState.getActionPowerCost()`: REST 비용 `-1(전량 소진) → 0` — 회복 후 곧바로 0으로 소진되던 모순 해결
- 🧹 FishAI 단일 행동력 체계 정리 (중복 AP 상태 제거)
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 32)

## [1.4.0] - 2026-08-05 — ✨ 메이저

**행동력(Action Power) 시스템 신설**

- ✨ 물고기마다 행동력 코인 부여 — 상태별 소모/회복
- ✨ 상태별 행동력 소모량(`getActionPowerCost`): SLOW 1 / TURN 1 / CIRCLE 2 / CHARGE 3 / DIVE 1 / JUMP 2 / FINAL 전량 / REST·LINE_TANGLE 회복
- ✨ 상태별 장력 증가율(`getTensionRate`) + 스태미너 회복 배수 (REST 4배, SLOW 2배)
- ✨ 등급별 최대 행동력(`grade-max`) + Rare 트로피 2배
- ✨ 위험 상황(스태미너 낮음 + 플레이어 릴 감기)에서 전 행동력 소모하는 AI 전이
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` (세션 31)

## [1.3.0] - 2026-08-04 — ✨ 메이저

**Trophy Fight 상태 확장 + 상태별 사운드 + 문서**

- ✨ 신규 상태 추가: `EXHAUSTED(탈진)`, `DIVE(잠수)`, `CIRCLE(원형 유영)`, `JUMP(점프)`, `LINE_TANGLE(줄 엉킴)`
- ✨ 상태별 권장행동 가이드 (HUD — 좌클릭/우클릭 지시)
- ✨ 상태별 사운드 추가, 관리자가 변경 가능 (`sound.state.<상태>` config)
- 📄 `TROPHY_FIGHT.md` — Trophy Fight 내부 동작 가이드(상태/수식/밸런스/UI) 문서 신설
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL`

## [1.2.2] - 2026-08-04 — 🛠 패치

**설정 파일 역할 분리 + reload 강화** (피드백 반영)

- ⚙️ 설정 분리: `messages.yml` / `fight.yml` / `fatigue.yml` / `potions.yml`
- ✨ `/fishing reload` 시 config/modifiers + 도감 + 대회 레지스트리 재로드
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL`

## [1.2.1] - 2026-08-04 — 🛠 패치

**설정 명칭 정정 + GUI 미니게임 ON/OFF** (피드백 반영)

- ⚙️ `max-tension` → `default-line-strength` 리네임 (실제로는 기본 줄 강도) — 기존 키 호환 fallback 유지
- ⚙️ `default-reel-state` 주석 "릴 내구도" → "릴 HP" 정정
- ✨ 메인 GUI "오토낚시 ON/OFF" → "미니게임 ON/OFF" 명칭 변경 (토글 동작과 일치)
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL`

## [1.2.0] - 2026-08-03 — ✨ 메이저

**Trophy Fight 인트로/카운트다운 연출**

- ✨ 파이트 시작 전 인트로 연출 + 카운트다운 추가 (실제 + 연습모드)
- ✨ 인트로 관련 config 지원
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL`

## [1.1.0] - 2026-08-03 — ✨ 메이저

**메인 GUI 신설**

- ✨ 메인 GUI 추가
- ✨ 스탯 표시 / 연습모드 / 도감 일괄등록 / 보상 사유
- 📄 `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL`

## [1.0.0] - 2026-08-03 — 🏷 기준

**최초 전체 기능 완성 (버전 기준선)**

- 🏷 Phase 1~3 (CONFIG / CORE / FEATURES) 계획 기능 전부 구현 완료 (세션 1~28)
- 🎣 낚시 보상: 더블/대어/미니게임, 입질→시작, 성공/실패/타임아웃, Session Lock
- 📖 도감(Collection) 시스템 — 슬롯/퍼펙트/보상/회수
- 🏆 낚시 대회(Tournament) — 참가/점수/우승 랭킹
- 🥅 어망(Net) 시스템 — 보관함/트로피/레어 표시
- 🏅 랭킹(Ranking), 트로피 파이트 기본 프레임워크
- ⚙️ Modifier(World/Biome/Weather/Time/Permission/Rod Bonus) + 설정/레지스트리 구조
- ⌨️ 관리자/플레이어 명령어, TabCompleter, simulate
- 📄 `README.md`, `PROGRESS.md`
- ✅ 빌드 `BUILD SUCCESSFUL` — 이 시점 이후 모든 변경은 위 버전들(1.1.0~)로 이어짐

---

### 릴리즈 완료

최초 전체 기능 기준선 `1.0.0`부터 최신 `1.4.5`까지의 릴리즈 노트입니다.
향후 새로운 세션(패치)이 생기면 이 문서 최상단에 새 버전 섹션을 추가하고, `PROGRESS.md`와 함께 갱신합니다.


