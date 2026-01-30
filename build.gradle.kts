plugins {
    java
}

group = "io.simplereactive"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Testing - JUnit
    testImplementation(platform("org.junit:junit-bom:6.0.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    
    // JUnit Platform Launcher (JUnit 6에서 필수)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Reactive Streams TCK (Technology Compatibility Kit)
    // TCK는 TestNG 기반이므로 TestNG 의존성 필요
    testImplementation("org.reactivestreams:reactive-streams-tck:1.0.4")
    testImplementation("org.testng:testng:7.10.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// TCK 테스트 (TestNG 기반) 별도 태스크
tasks.register<Test>("tckTest") {
    useTestNG()
    include("**/tck/**")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
