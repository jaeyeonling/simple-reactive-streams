plugins {
    java
}

group = "io.simplereactive"
version = "1.0.0-SNAPSHOT"

// 모든 프로젝트에 공통 설정
allprojects {
    repositories {
        mavenCentral()
    }
}

// 하위 모듈에 공통 설정
subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    dependencies {
        // Testing - JUnit
        testImplementation(platform("org.junit:junit-bom:6.0.2"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.assertj:assertj-core:3.27.7")

        // JUnit Platform Launcher (JUnit 6에서 필수)
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}
