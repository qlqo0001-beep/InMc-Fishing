plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "me.ninesik"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 확정 사항(PROGRESS_ARCHIVE.md): paperweight-userdev 미적용, 순수 compileOnly만 사용.
    // MMOItems/PlaceholderAPI/Vault/WorldGuard/ProtocolLib는 리플렉션 기반 연동이라
    // 별도 compileOnly 의존성이 필요 없다 (dependency/*.Hook.java에서 확인됨).
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // bStats 메트릭스 — 최종 jar(Shadow)에만 병합된다.
    implementation("org.bstats:bstats-bukkit:3.2.1")
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
