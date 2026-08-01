# InMc-Fishing 개발 리뷰

> 작성 기준: 2026-08-01  
> 참고 문서: `PROGRESS.md`, `PROGRESS_ARCHIVE.md`, `FISHING_PLUGIN_PLAN.md`, `패치예정.md`

---

## 1. 프로젝트 개요

**InMc-Fishing**은 Java 25 / Paper 26.2 API 기반의 Minecraft 낚시 플러그인이다.  
등급별 물고기 확률, 타이밍 기반 미니게임, 도감 시스템, 랭킹, 대회, 트로피/사이즈 시스템을 포함한다.

### 기술 스택
- 언어/런타임: Java 25
- 빌드 도구: Gradle Kotlin DSL 9.5.1
- API: Paper 1.21 (api-version `1.21`)
- 의존성: MMOItems, PlaceholderAPI, Vault, WorldGuard, ProtocolLib (softdepend)
- 패키지: `me.ninesik.fishing` (세션 9에서 `me.NineSik.fishing` → 소문자 일괄 변경)

---

## 2. 전체 세션 타임라인

| 세션 | 날짜 | 주요 내용 |
|------|------|-----------|
| 3 | 2026-07-26 | 대어 승급, GradeRegistry 대소문자, Rod 인식 로직(lookupRod/RodLookupResult), Modifier 6종 |
| 4 | 2026-07-27 | 보상 지급 파이프라인 연결 (FishingListener → MiniGame → RewardService) |
| 4b | 2026-07-27 | 세션 정리 이벤트 3종, Rod 복너스 적용, timeoutTask 제거 |
| 5 | 2026-07-27 | Grade 객체 → gradeId 리팩터, /fishing reload/debug/info 명령어 |
| 6 | 2026-07-27 | onEnable NPE 수정, permissions, TabCompleter, /fishing simulate |
| 7 | 2026-07-27 | 유령 클릭 방지 (lastRightClickTick) |
| 8 | 2026-07-28 | 한글 주석, UI config화, TimeBarMiniGame 전면 개편, 결과 타이틀 |
| 9 | 2026-07-28 | 패키지명 소문자 일괄 변경, onEnable 전면 재작성 |
| 10 | 2026-07-28 | UTF-8 BOM 사고 복구 후 재검증, 컴파일 버그 수정 |
| 11 | 2026-07-29 | 낚싯대 내구도 삭제, api-version 변경, 표시명 fallback 수정 |
| 12~15 | 2026-07-31~08-01 | 도감 시스템 설계/구현, GUI, 랭킹 시스템 |
| 16 | 2026-08-01 | FishCatchEvent 연동, /fishing collection, rank 명령어 |
| 17~18 | 2026-08-01 | 대회 시스템, 사이즈 시스템, 물고기 70종 확장 |
| 19 | 2026-08-01 | 트로피 + 사이즈 랭킹 |
| 20 | 2026-08-01 | 대회 자동 스케줄러, 참가비, 보상, TournamentGui |
| 21 | 2026-08-01 | 코드 품질 개선, CommandRunner, infishing.user 권한 |
| 22-1 | 2026-08-01 | 도감 등록 버그 수정, 등급 색상, 트로피 Lore |
| 22-2~22-8 | 2026-08-01 | 패치예정.md 전체 처리: 대회 버그, 안정성, 사이즈 랭킹 개편, 어망, 대회 HUD, 명령어 개선, 도감 진행도, 대회 우승 랭킹 |

---

## 3. Phase별 개발 과정

### Phase 1: CONFIG (기초 설계)
- `grades.yml`, `items/*.yml`, `rod.yml` 정의
- `Grade`, `Fish`, `Rod` 모델 및 `GradeLoader`, `FishLoader`, `RodLoader`
- `RegistryManager` + Immutable Map + `AtomicReference` 스왑
- Validator로 설정 오류 사전 차단

### Phase 2: CORE (핵심 낚시 루프)
- `DependencyManager`: Vault/MMOItems/PAPI/WorldGuard/ProtocolLib 연동 추상화
- `RollEngine`: 등급 확률 → 물고기 확률 → 사이즈(가우시안) → 더블/대어
- `WeightCalculator`: 월드/바이옴/날씨/시간/권한/낚싯대 복너스 적용
- `FishingSessionManager`: 세션 생명주기 관리
- `FishingMiniGame`: 타이밍 기반 클릭 미니게임 (TimeBar)
- `RewardService`: 아이템/명령어 보상 지급, Lore, CustomModelData, MMOItems 연동

### Phase 3: FEATURES (도감·대회·GUI)
- **도감**: CollectionEntry/CollectionData, Soft Delete(INACTIVE), 자동/대기 보상, 전체수령 GUI
- **랭킹**: 등록 슬롯 기반 점수, 사이즈/트로피 랭킹
- **대회**: COUNT/GRADE/SIZE 타입, 자동 스케줄러, 참가비, 보상, ActionBar HUD
- **어망**: PDC 기반 `fishId` 저장, 사용 시 도감 등록

---

## 4. 주요 난항과 해결 과정

### 4.1 🔴 onEnable()이 아무것도 하지 않음
**문제**: 초기 `InMcFishing.onEnable()`은 설정 파일만 복사하고 실제 서비스를 초기화하지 않아 플러그인이 로드되어도 동작하지 않음.

**해결**: `FishLoader`, `RodLoader` 신규 작성 후 `onEnable()`을 전면 재작성.  
순서: `saveDefaultResources()` → `DependencyManager` → `RegistryManager` → `FishingService` → `CollectionManager` → `RankingManager` → `TournamentManager` → `GuiListener` → 명령어 등록.

### 4.2 🔴 낚싯대 인식 로직이 29.1 스펙과 정반대
**문제**: 이름 없는 바닐라 낚싯대를 차단해야 하는데, 오히려 등록된 낚싯대만 허용하는 식으로 구현됨.

**해결**: `lookupRod()` + sealed interface `RodLookupResult` 도입.  
결과: `UnregisteredVanilla`, `RegisteredVanilla`, `MMOItemsRod`, `UnregisteredMMOItems`로 명확히 구분.

### 4.3 🔴 유령 클릭 버그
**문제**: 우클릭 한 번에 `RIGHT_CLICK` + `LEFT_CLICK_AIR/BLOCK`이 같은 틱에 함께 발생해 미니게임이 한 틱에 두 번 진행됨.

**해결**: `PlayerAnimationEvent` 제거, `Map<UUID, Integer> lastRightClickTick` 도입.  
같은 틱에 RIGHT_CLICK 직후 LEFT_CLICK은 무시.

### 4.4 🔴 UTF-8 인코딩 손실 사고 (가장 큰 난항)
**문제**: `Remove-Item -Recurse me\NineSik` 실행 시 PowerShell이 `me` 디렉토리까지 비워버려 `me\ninesik`의 Java 파일 48개가 전부 삭제됨. 백업 복구 시 `Set-Content` 기본 인코딩 문제로 한글 주석이 깨지고, BOM이 포함되어 `javac`가 `illegal character: '\ufeff'`로 컴파일 실패.

**해결**: Python 스크립트로 BOM 제거, `[System.IO.File]::ReadAllText/WriteAllText(..., Encoding.UTF8)` 명시, 패키지명 일괄 치환.

**교훈**:
1. 대량 삭제 전 `Test-Path`로 정확한 경로 확인
2. PowerShell 파일 복사 시 반드시 UTF8 인코딩 명시
3. 백업 zip 구조 확인 후 `src/main`으로 옮기기

### 4.5 🔴 plugin.yml main 클래스 경로 불일치
**문제**: 백업 복구 후 `main: me.NineSik.fishing.InMcFishing`으로 남아 ClassNotFoundException 우려.

**해결**: `main: me.ninesik.fishing.InMcFishing`으로 수정.

### 4.6 🔴 결과 타이틀이 절대 안 뜸
**문제**: `stop()`에서 `timeBars.remove(uuid)` 후 `showResultTitle()`이 `timeBars.get()`으로 조회 → 항상 null.

**해결**: `showResultTitle(player, result, session, timeBar)`로 timeBar 객체를 직접 전달.

### 4.7 🟠 색상 코드 처리 버그
**문제**: `&f`를 MiniMessage 형식으로 변환하면 `<color:#aaaaaa>f`처럼 색상 코드 문자가 그대로 출력됨.

**해결**: `LegacyComponentSerializer.legacySection().deserialize()` 사용.

### 4.8 🟠 도감 등록 버그
**문제**: 사이즈 Lore가 displayName 비교에 포함되어 어망/실제 낚은 아이템이 서로 다르게 인식됨.

**해결**: `isSameFishItem()`을 Material + stripColor(displayName) + customModelData 기반으로 개선. 사이즈 Lore는 비교에서 제외.

---

## 5. 중요 기술적 결정

| 주제 | 결정 | 근거 |
|------|------|------|
| DB 사용 | YAML 기반 유지 | 단일 서버, 7개 등급 고정, DB 도입 시 복잡도만 증가 |
| 도감 삭제 처리 | Soft Delete (`INACTIVE`) | 플레이어 기록 보존, GUI에서 빨간색 표시 |
| 보상 수령 | 자동 + 대기열 | 인벤토리 꽉 차면 `PendingReward` 대기 + 전체수령 버튼 |
| 랭킹 점수 | 등록 슬롯 + 퍼펙트/트로피 가중치 | 확정은 필요하나 현재 공식으로 운영 |
| 참조 방식 | UUID 기반 | 리로드 시 Player 객체 참조 끊김 방지 |
| 명령어 실행 | `CommandRunner` 유틸리티 | `{player}`, `{uuid}` 치환 + 로깅 일원화 |
| 별칭 | `/if`, `/낚시` | plugin.yml aliases로 `/fishing` 대체 |

---

## 6. 개발 방식과 회고

### 6.1 세션 단위 개발
- 한 세션에 하나의 모듈(또는 밀접한 몇 개의 버그)만 처리
- 매 세션 종료 시 `./gradlew build`와 `git commit` 수행
- 문제 발생 시 해당 세션 시작 시점으로 되돌릴 수 있어 복구 비용 절감

### 6.2 문서 기반 의사결정
- `FISHING_PLUGIN_PLAN.md` 29장을 먼저 확인
- 모호한 사양은 `// DECISION-NEEDED:` 주석 + `PROGRESS.md` 기록
- 플랜 문서는 설계 변경이 실제 합의된 경우가 아니면 수정하지 않음

### 6.3 디버깅 기법
- **jar 대조**: 구버전 jar와 신버전 jar를 압축 해제하여 클래스 바이트코드와 리소스를 직접 비교
- **정규식 대조**: 정의된 public 메서드 vs 실제 호출되는 메서드 전수 검색
- **BOM/인코딩 스캔**: Python으로 전체 Java 파일 인코딩 확인

### 6.4 아쉬운 점
- Phase 4(서버 테스트)는 아직 진행되지 않음
- `WorldGuardHook.isFishingEnabled()`는 TODO 상태로 남음
- `LoggerService`는 계획서에 있으나 미구현 (현재 `plugin.getLogger()` 사용)
- 랭킹/대회 점수 산정 공식은 아직 확정되지 않음

---

## 7. 최종 상태

- **빌드**: `BUILD SUCCESSFUL` (경고 1개: deprecated `Biome.name()`)
- **커밋**: 총 8개의 세션 커밋 (세션 22-1 ~ 22-8)
- **출력**: `build/libs/InMc-Fishing-1.0.0.jar`
- **진행**: Phase 3 FEATURES 완료, Phase 4 TEST 진행 예정
