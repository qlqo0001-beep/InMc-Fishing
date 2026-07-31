plugins {
    java
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
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    processResources {
        filteringCharset = "UTF-8"
    }
}
