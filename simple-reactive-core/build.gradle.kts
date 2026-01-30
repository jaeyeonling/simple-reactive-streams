// simple-reactive-core: Reactive Streams 라이브러리 구현
// 핵심 인터페이스, Publisher, Operator, Scheduler 등

dependencies {
    // Reactive Streams TCK (Technology Compatibility Kit)
    testImplementation("org.reactivestreams:reactive-streams-tck:1.0.4")
    testImplementation("org.testng:testng:7.10.2")
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
