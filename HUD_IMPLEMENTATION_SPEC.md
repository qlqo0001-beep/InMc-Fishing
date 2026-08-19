# 낚시 미니게임 HUD 구현 명세

## 결정

이 HUD는 **ItemsAdder 단독**으로 구현한다. BetterHud는 의존성에 추가하지 않는다.

ItemsAdder의 Custom HUD와 Java API로 상태 카드, 카운트다운, 콤보, 게이지를 갱신한다.

- 플러그인 의존성: ItemsAdder
- BetterHud: 사용하지 않음
- 리소스팩: ItemsAdder가 생성·배포
- HUD 표시: 낚시 입질 후 미니게임 세션 시작 시
- HUD 해제: 성공, 실패, 시간 초과, 낚싯대 교체, 사망, 접속 종료, 월드 이동, 플러그인 비활성화

ItemsAdder가 설치되지 않았거나 리소스팩을 받지 않은 플레이어에게는 미니게임을 시작하지 않고 안내 메시지를 표시한다. action bar로 대체하지 않는다.

## 화면 배치

기준 화면은 960 × 540이며, HUD 스케일이 달라도 화면 중앙을 기준으로 유지한다.

| 요소 | 기준점 | 배치 |
| --- | --- | --- |
| 거리 트래커 | 상단 중앙 | 화면 가운데, 상단에서 22 px |
| 콤보 | 화면 중앙의 왼쪽 | 상태 카드 왼쪽 18 px 바깥, 세로는 카드 제목과 맞춤 |
| 물고기 상태 카드 | 정중앙 | 카드의 가로 중심과 화면의 가로 중심을 일치 |
| 체력·텐션·릴 게이지 | 하단 중앙 | 핫바 위 86 px |

중요한 변경점은 상태 카드를 중앙 오른쪽으로 밀지 않는다는 점이다. 카드 자체가 정확히 중앙에 오고, 콤보만 카드의 왼쪽에 독립적으로 표시된다.

## 표시 요소와 게임 데이터

| HUD 레이어 | 데이터 | 갱신 |
| --- | --- | --- |
| 거리 트래커 | 물고기 거리, 최대 거리, 위험 구간, 물고기 마커 위치 | 2 tick |
| 상태 카드 | 상태 ID, 남은 시간, 행동 안내, 입력 아이콘 | 상태 변경 즉시 / 2 tick |
| 콤보 | 연속 성공 수, 콤보 활성 여부 | 올바른 입력 즉시 |
| 물고기 체력 | 현재·최대 체력 | 2 tick |
| 텐션 | 현재 텐션, 안전/주의/위험 구간 | 매 tick |
| 릴 감김 | 현재 릴 진행도 | 2 tick |

상태 카드는 강한 저항, 잠수, 좌·우 회피, 줄 감기·풀기, 텐션 위험, 기력 저하, 막판 저항, 물고기 지침, 포획 직전 등 상태별로 이름·강조색·안내 문구·기대 입력을 분리해 설정한다.

## ItemsAdder 구성

ItemsAdder 콘텐츠 네임스페이스는 fishing_hud를 사용한다.

    plugins/ItemsAdder/contents/fishing_hud/
      configs/fishing_hud.yml
      resourcepack/assets/fishing_hud/textures/font/hud/distance/
      resourcepack/assets/fishing_hud/textures/font/hud/state/
      resourcepack/assets/fishing_hud/textures/font/hud/combo/
      resourcepack/assets/fishing_hud/textures/font/hud/gauges/
      resourcepack/assets/fishing_hud/textures/font/hud/icons/

정적 배경·아이콘과 동적 막대는 분리한다. 텐션, 물고기 체력, 릴 감김은 픽셀 단위로 분할된 바 이미지와 Custom HUD를 조합해 렌더링한다. 상태 카드의 타이머는 숫자 글리프 조합으로 표시하므로 상태 수 × 시간 값만큼 PNG를 만들 필요가 없다.

낚시 플러그인에는 FishingHudController를 둔다. 컨트롤러는 show, update, setState, addCombo, hide 작업만 담당하고, 보상·입력 판정은 기존 FishingSession에 남긴다.

## 리소스 제작 기준

- 모든 패널은 투명 배경 PNG와 1–2 px 외곽선으로 제작한다.
- 기본 해상도는 1× 픽셀 아트 기준으로 제작한다.
- 물고기, 마우스 우클릭, 낚시꾼 아이콘은 별도 PNG로 둔다.
- 텐션은 초록 → 노랑 → 빨강 구간과 흰색 바늘을 사용한다.
- 텐션이 80% 이상이면 카드 테두리와 경고 문구만 점멸한다.
- HUD는 낚시 미니게임 세션 중에만 표시한다.

## 검증 항목

1. ItemsAdder가 없는 서버에서는 FishingMiniGame이 로드되지 않는다.
2. 리소스팩 적용 후 입질 때만 모든 HUD 레이어가 표시된다.
3. 상태 카드는 화면 정중앙이고, 콤보는 카드의 왼쪽에 있다.
4. 텐션 80% 이상에서 경고만 점멸하며 수치·바늘은 계속 갱신된다.
5. 모든 세션 종료 경로에서 HUD가 즉시 사라진다.
6. 16:9, 21:9, GUI 스케일 1–4에서 중앙 카드가 가로 중앙을 유지한다.

## 참고 문서

- [ItemsAdder Custom HUD](https://wiki.itemsadder.com/adding-content/font-images/huds/creating-huds/custom-hud-advanced/)
- [ItemsAdder HUD Java API](https://wiki.itemsadder.com/developers/java-api/huds-guis-images-and-more/)
