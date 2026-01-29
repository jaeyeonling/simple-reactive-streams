plugins {
    java
}

group = "io.simplereactive"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Reactive Streams API (참조용, 우리가 직접 구현할 것)
    compileOnly("org.reactivestreams:reactive-streams:1.0.4")
    
    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    
    // Reactive Streams TCK (Technology Compatibility Kit)
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
