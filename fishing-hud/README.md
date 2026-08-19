# 낚시 미니게임 / 트로피 파이트 HUD — ItemsAdder 연동 패키지

`hud-mockup_1.html`, `hud-mockup_2.html` 시안과 `HUD_IMPLEMENTATION_SPEC.md` 를 기준으로
**리소스팩(ItemsAdder 콘텐츠) + 플러그인 연동 코드**를 한 세트로 만든 결과물입니다.

- 상태 카드는 화면 **정중앙**, 콤보는 카드 **오른쪽**(좌클릭/우클릭 콤보 구분), 거리 트래커는 **위**, 게이지는 **아래**
- 텐션 80% 이상이면 **경고 문구만 점멸**하고 수치·바늘은 계속 갱신
- L/R 미니게임 키 카드는 28×36 크기이고 클릭 수(3~9회)에 맞춰 **패널 폭이 자동 확장**
- 화면별로 `glyph / fallback / vanilla` 를 골라 쓸 수 있어 **L/R 미니게임만 예전 방식으로** 되돌릴 수 있음
- ItemsAdder 가 없거나 리소스팩을 못 받은 플레이어는 **기존 보스바/액션바로 자동 폴백**

---

## 1. 폴더 구조

```
fishing-hud/
├─ itemsadder/contents/fishing_hud/          ← ItemsAdder 에 그대로 복사
│  ├─ configs/fishing_hud.yml                   글리프(font_images) 168개 정의
│  └─ resourcepack/assets/fishing_hud/textures/font/hud/*.png
├─ plugin/src/main/java/me/ninesik/fishing/hud/ ← 플러그인에 그대로 복사
│  ├─ FishingHudController.java                 진입점 (show / update / hide)
│  ├─ GlyphHudRenderer.java                     글리프 HUD 렌더러
│  ├─ LegacyHudRenderer.java                    보스바/액션바 폴백
│  ├─ ItemsAdderSupport.java                    설치·리소스팩 적용 감지
│  ├─ ItemsAdderReloadListener.java             IA 콘텐츠 리로드 시 캐시 무효화
│  ├─ HudLayout.java / HudMode.java             hud.yml 로딩, 모드
│  ├─ FightHudData.java / MinigameHudData.java  한 프레임 값 묶음
│  └─ glyph/ GlyphLine · GlyphTable · ItemsAdderGlyphs · HudColors
├─ plugin/src/main/resources/
│  ├─ hud.yml                                   좌표·색·상태 매핑 (운영자 수정용)
│  └─ hud_sprites.yml                           글리프 폭 표 (자동 생성, 수정 금지)
├─ tools/                                       텍스처 생성·미리보기 스크립트
│  ├─ hud_spec.py                               좌표·팔레트·문구 원본
│  ├─ build_hud_assets.py                       PNG + fishing_hud.yml + hud_sprites.yml 생성
│  └─ render_preview.py                         합성 결과 미리보기 렌더
├─ docs/preview.html                            미리보기 (인게임 합성 결과)
└─ INTEGRATION.md                               기존 코드 수정 지점
```

---

## 2. 설치

### 2-1. ItemsAdder 콘텐츠 넣기

```
plugins/ItemsAdder/contents/fishing_hud/   ← itemsadder/contents/fishing_hud 통째로 복사
```

서버에서:

```
/iareload
/iazip          (리소스팩 재생성 · 배포 설정에 따라 자동일 수 있음)
```

`/iadebug fontimages` 로 `fishing_hud:card_bg` 같은 글리프가 잡히는지 확인합니다.

### 2-2. 플러그인 파일 넣기

- `plugin/src/main/java/me/ninesik/fishing/hud/**` → 플러그인 소스에 복사
- `plugin/src/main/resources/hud.yml`, `hud_sprites.yml` → 리소스에 복사
- `plugin.yml` 에 소프트 의존 추가

```yml
softdepend: [ItemsAdder]
```

- 빌드 스크립트에 ItemsAdder API 를 **compileOnly** 로 추가

```kotlin
repositories { maven("https://repo.devs.beer/") }
dependencies { compileOnly("dev.lone:api-itemsadder:4.0.10") }   // 서버에 설치된 버전에 맞추기
```

### 2-3. 코드 연결

`INTEGRATION.md` 의 5개 지점(플러그인 onEnable/onDisable, TrophyFightManager 3곳,
FishingMiniGame 3곳)에 호출을 넣으면 끝입니다. 기존 `FightHUD` 는 지우지 않아도 됩니다
(폴백으로 계속 쓰거나, `LegacyHudRenderer` 대신 감싸도 됩니다).

---

## 3. 어떻게 그려지는가 (설계 결정)

### 액션바 한 줄에 HUD 전체를 합성한다

ItemsAdder 의 기본 HUD 타입(`STATUS` / `FRAMES`)은 **값마다 PNG 가 한 장씩** 필요하고
위치도 핫바 근처로 제한됩니다. `type: CUSTOM` 은 ItemsAdder 내부 HUD 구현을 자바로
직접 붙여야 해서 IA 버전에 강하게 묶입니다.

그래서 이 패키지는 IA 를 **글리프(font_images) 공급자**로 쓰고, HUD 한 프레임을
**액션바 문자열 하나**로 합성합니다.

| 축 | 방법 |
| --- | --- |
| 세로 | 글리프의 `y_position`(= PNG 아래쪽 투명 패딩). 스프라이트마다 화면 높이가 고정 |
| 가로 | ItemsAdder 오프셋 글리프 `:offset_±N:` 로 커서를 이동 |
| 중앙 정렬 | 라인의 **전체 advance 를 0** 으로 맞춤 → 마인크래프트가 폭 0 문자열을 화면 중앙에 놓으므로 원점이 항상 화면 정중앙 |
| 막대 | 1·2·4·8·16·32·64 px 채움 조각을 이어붙여 임의 폭 (값마다 PNG 불필요). 텐션은 구간별로 색을 바꿔 이어 그려서 **채워진 만큼만** 초록/노랑/빨강이 보인다 |
| 숫자 | 5×7 픽셀 숫자 글리프 조합 |
| 색 | 흰색 스프라이트 + 색코드 틴트 (물고기 체력=초록, 릴=파랑, 상태별 색 등) |

덕분에

- 타이틀/서브타이틀/보스바를 쓰지 않아 **다른 연출과 겹치지 않고**, 타이틀 4배 확대 문제도 없습니다.
- GUI 스케일 1~4, 16:9·21:9 어디서도 카드가 가로 중앙에서 벗어나지 않습니다.
- 상태 12종 × 시간값 만큼 PNG 를 만들 필요가 없습니다(총 스프라이트 168장).
- 미니게임 패널처럼 폭이 변하는 UI 도 좌/우 마감 + 가운데 타일 조합으로 처리합니다.

### 한글 문구는 왜 PNG 인가

글리프가 아닌 일반 텍스트는 액션바 **베이스라인에만** 그려집니다. 상태 카드는 화면
중앙에 있어야 하므로 상태명·가이드·라벨을 **문구 스프라이트**로 굽습니다.
문구를 바꾸려면 `tools/hud_spec.py` 의 `STATES` / `LABELS` 를 고치고

```bash
python3 tools/build_hud_assets.py     # PNG + fishing_hud.yml + hud_sprites.yml 재생성
python3 tools/render_preview.py       # 미리보기 확인
```

카드를 넘치는 문구가 있으면 빌드가 경고로 알려줍니다(가이드 ≤140px, 상태명 ≤106px).

---

## 4. 좌표 수정

| 바꾸고 싶은 것 | 고칠 곳 |
| --- | --- |
| 화면별 표시 방식(글리프/폴백/바닐라) | `hud.yml` 의 `fight.mode`, `minigame.mode` |
| 콤보 위치·색 | `hud.yml` 의 `layout.combo.x`, `combo.left-color`, `combo.right-color` |
| 미니게임 키 간격·패널 여백 | `hud.yml` 의 `layout.minigame.*` |
| 가로 위치, 막대 폭, 색, 상태별 아이콘/색/클릭 안내 | `hud.yml` (서버 재시작 없이 리로드) |
| 세로 위치(레이어 높이) | `tools/hud_spec.py` 의 `Y` → 빌드 스크립트 재실행 → 리소스팩 재배포 |
| 문구 | `tools/hud_spec.py` 의 `STATES` / `LABELS` → 재빌드 |

세로 위치는 글리프에 구워지기 때문에 리소스팩을 다시 만들어야 합니다. 글리프 하나의
높이 한계는 256px(마인크래프트 제한)이라, 액션바 기준 약 250px 위가 최대입니다.
현재 값은 거리 트래커 200px, 카드 152~96px, 게이지 62~36px 입니다.

---

## 5. 검증 체크리스트

1. ItemsAdder 가 없는 서버 — 파이트가 정상 진행되고 보스바/액션바 폴백이 뜬다.
2. 리소스팩 적용 후 — 입질(미니게임)과 파이트 중에만 HUD 가 뜬다.
3. 상태 카드가 화면 정중앙, 콤보는 카드 오른쪽(좌/우클릭 구분 표기).
4. 텐션 게이지는 채워진 만큼만 초록→노랑→빨강이 보이고, 80% 이상이면 경고 문구만 점멸한다
   (바늘·수치는 계속 갱신).
5. L/R 미니게임에서 클릭 수가 3~9회로 바뀌어도 패널이 키 개수에 맞춰 늘어난다.
   `minigame.mode: vanilla` 로 두면 기존 표시가 그대로 나온다.
6. 성공/실패/시간초과/낚싯대 교체/사망/로그아웃/월드이동/플러그인 비활성화 —
   모든 경로에서 HUD 가 즉시 사라진다(컨트롤러에 사망·월드이동·퇴장 안전망 내장).
7. GUI 스케일 1~4, 16:9·21:9 에서 카드가 가로 중앙을 유지한다.

---

## 6. 트러블슈팅

| 증상 | 원인 / 조치 |
| --- | --- |
| 네모(□)만 보인다 | 리소스팩 미적용 또는 `/iareload` 후 팩 재배포 안 됨 |
| 레이어가 좌우로 어긋난다 | 텍스처를 손으로 고치고 `hud_sprites.yml` 을 다시 만들지 않은 경우 |
| 전체가 왼쪽으로 치우친다 | 서버에 `:offset_-N:` 글리프가 없는 IA 버전 → `hud.yml` 의 `offset-magnitudes` 를 실제 지원 값으로 줄이기 |
| HUD 가 깜빡이며 사라진다 | 다른 플러그인이 액션바를 쓰는 중. 대회 HUD 는 보스바+스코어보드라 충돌하지 않지만, 외부 플러그인(예: 체력 표시류)은 꺼야 합니다 |
| 글자에 그림자가 진다 | 액션바 기본 렌더. 1.21.4+ 라면 컴포넌트 `shadow_color` 를 0 으로 주면 없앨 수 있습니다 |

---

## 7. 참고

- ItemsAdder — [Font Images](https://wiki.itemsadder.com/adding-content/font-images/font-images/) ·
  [HUDs](https://wiki.itemsadder.com/adding-content/font-images/huds/creating-huds/) ·
  [Java API](https://wiki.itemsadder.com/developers/java-api/huds-guis-images-and-more/)
