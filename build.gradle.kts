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
    // Testing
    testImplementation(platform("org.junit:junit-bom:6.0.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    
    // JUnit Platform Launcher (JUnit 6에서 필수)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Reactive Streams TCK (Technology Compatibility Kit)
    // TCK 사용 시 org.reactivestreams 인터페이스가 필요하므로,
    // 우리가 만든 인터페이스를 TCK의 인터페이스로 어댑팅해야 함
    testImplementation("org.reactivestreams:reactive-streams-tck:1.0.4")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
