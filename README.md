# 🎣 InMc-Fishing

**InMc-Fishing**은 Paper 서버를 위한 확장형 낚시 플러그인입니다.

기존 Skript 기반 낚시 시스템을 Java 기반으로 재구성하여 안정성과 유지보수성을 높였으며, 설정 파일만으로 낚시 보상과 확률을 자유롭게 커스터마이징할 수 있습니다.

---

## ✨ 주요 기능

- 🎣 좌클릭 / 우클릭 입력 방식의 낚시 미니게임
- 🐟 F ~ S 등급 시스템
- 🎁 등급별 개별 보상 및 가중치 설정
- ⭐ 대어(Big Fish) 시스템
- 💎 더블(Double) 보상 시스템
- 🎣 낚싯대별 확률 보너스
- 📚 도감(Collection) 시스템
- 🏆 낚시 대회(Tournament) 시스템
- 📊 확률 시뮬레이션 기능
- ⚙️ YAML 기반 설정
- 🔌 MMOItems 연동
- 📈 PlaceholderAPI 지원
- 💰 Vault 지원
- 🌍 WorldGuard 지원
- 📡 ProtocolLib 지원

---

# 특징

### YAML 기반 관리

모든 낚시 보상은 YAML 파일에서 관리됩니다.

- 등급별 아이템
- 확률
- 낚싯대
- 대회
- Modifier
- 메시지

코드를 수정하지 않아도 대부분의 기능을 변경할 수 있습니다.

---

### 가중치 시스템

등급별 확률과 아이템별 확률을 모두 개별적으로 설정할 수 있습니다.

```yaml
items:
  cod:
    weight: 70

  salmon:
    weight: 20

  diamond:
    weight: 10
```

---

### MMOItems 지원

MMOItems 아이템을 보상으로 사용할 수 있습니다.

```yaml
use-type: mmoitems
mmoitems-type: ROD
mmoitems-id: legendary_rod
```

Vanilla 아이템과 함께 사용할 수 있습니다.

---

### 미니게임

입질이 오면 좌클릭(L) / 우클릭(R) 입력이 시작됩니다.

예시

```
L → R → R → L → L
```

제한 시간 안에 성공하면 보상을 획득합니다.

---

### 대어 시스템

일정 확률로 더 높은 등급으로 승급됩니다.

```
F → E → D → C → B → A → S
```

---

### 더블 시스템

설정된 아이템은 일정 확률로 보상이 2배 지급됩니다.

---

### 도감 시스템

- 물고기 수집
- 획득 기록
- 등록 보상
- 완료 보상

플레이어의 수집 진행도를 관리할 수 있습니다.

---

### 낚시 대회

서버에서 다양한 낚시 대회를 운영할 수 있습니다.

예시

- 가장 많은 물고기
- 가장 높은 등급
- 가장 희귀한 물고기

---

## 지원 환경

| 항목 | 내용 |
|------|------|
| Server | Paper |
| Java | 25 |
| Build | Gradle Kotlin DSL |

---

## 선택 의존성

- MMOItems
- PlaceholderAPI
- Vault
- WorldGuard
- ProtocolLib

의존성 플러그인이 없어도 기본 기능은 사용할 수 있으며, 설치된 경우 자동으로 연동됩니다. :contentReference[oaicite:2]{index=2}

---

## 프로젝트 구조

```
src/
gradle/
gradlew
gradlew.bat
build.gradle.kts
settings.gradle.kts
```

---

## Build

Windows

```bash
gradlew.bat build
```

Linux

```bash
./gradlew build
```

빌드된 플러그인은

```
build/libs/
```

폴더에서 확인할 수 있습니다.

---

## 주요 명령어

```
/fishing reload
/fishing debug
/fishing simulate <횟수>
/fishing give <fish>
/fishing test
/fishing info
```

관리자는 확률 검증 및 디버그 기능을 통해 설정을 쉽게 확인할 수 있습니다. :contentReference[oaicite:3]{index=3}

---

## 개발 목표

- 안정적인 Registry 기반 구조
- 확장 가능한 미니게임 시스템
- YAML 중심의 설정
- 높은 유지보수성
- Skript 시스템 완전 대체
- 다양한 플러그인과의 연동 지원

---

## License

This project is licensed under the MIT License.