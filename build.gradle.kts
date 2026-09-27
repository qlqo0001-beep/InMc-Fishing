plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "me.ninesik"
version = "1.6.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // WorldGuard / WorldEdit
    maven("https://maven.enginehub.org/repo/")
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // 확정 사항(PROGRESS_ARCHIVE.md): paperweight-userdev 미적용, 순수 compileOnly만 사용.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // MMOItems / Vault는 리플렉션 기반 연동이라 compileOnly가 필요 없다
    // (dependency/*.Hook.java 참고).
    //
    // WorldGuard는 예외다. 지역 조회가
    // WorldGuard → Platform → RegionContainer → RegionQuery → ApplicableRegionSet
    // → StateFlag 로 이어지는 6단계라 리플렉션으로 쓰면 코드가 길어지는 것에 더해,
    // 오타나 상위 버전의 시그니처 변경이 전부 런타임에야 드러난다. compileOnly로
    // 두면 컴파일 단계에서 잡히고 최종 jar 크기에도 영향이 없다.
    // 서버에 WorldGuard가 없어도 동작해야 하므로 진입점에서 isAvailable()로 가드한다.
    //
    // isTransitive = false가 필수다. WorldEdit이 Gradle 모듈 메타데이터에
    // "Mojang provides Guava/Gson" 사유로 guava:{strictly 33.5.0-jre},
    // gson:{strictly 2.13.2} 제약을 걸어두는데, paper-api는 guava 33.6.0 /
    // gson 2.14.0을 요구해서 해석 자체가 실패한다(런타임에는 서버가 둘 다
    // 제공하므로 실제 충돌이 아니다). 컴파일에 필요한 건 아래 4개 모듈의
    // 클래스뿐이라 전이 그래프를 통째로 끊는 편이 정확하다.
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.18") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.0") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.0") { isTransitive = false }

    // PlaceholderAPI도 compileOnly가 필요하다. placeholder를 등록하려면
    // 추상 클래스 PlaceholderExpansion을 상속해야 하는데, 추상 클래스라
    // java.lang.reflect.Proxy로는 만들 수 없다(인터페이스만 지원).
    // 서버에 PAPI가 없으면 확장을 아예 생성하지 않는다 — PlaceholderAPIHook 참고.
    compileOnly("me.clip:placeholderapi:2.12.3") { isTransitive = false }

    // bStats 메트릭스 — 최종 jar(Shadow)에만 병합된다.
    implementation("org.bstats:bstats-bukkit:3.2.1")

    // SQLite JDBC — 플레이어 데이터 통합 DB.
    // 런타임에는 Paper 라이브러리 로더가 공급한다 (plugin.yml의 libraries:).
    // implementation으로 두면 shadowJar의 "org.bstats만 병합" 규칙에 걸려 jar에서
    // 빠지고, 서버가 드라이버를 제공하지 않으면 DatabaseManager.init()이 실패해
    // 플러그인 전체가 비활성화된다. 그래서 compileOnly + libraries: 조합을 쓴다.
    compileOnly("org.xerial:sqlite-jdbc:3.49.1.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    processResources {
        filteringCharset = "UTF-8"
    }
}

tasks.shadowJar {
    // runtimeClasspath(bStats만)를 최종 jar에 병합 — paper-api는 compileOnly라 제외된다.
    configurations = project.configurations.runtimeClasspath.map { setOf(it) }

    dependencies {
        // 다른 플러그인의 bStats과 충돌하지 않도록 org.bstats만 병합한다.
        exclude {
            it.moduleGroup != "org.bstats"
        }
    }

    // bStats를 플러그인 패키지(me.ninesik)로 릴로케이트해 타 플러그인과의 충돌을 방지한다.
    relocate("org.bstats", project.group.toString())

    archiveClassifier.set("all")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
