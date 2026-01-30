// simple-reactive-example: Module 9 실전 프로젝트 예제
// Legacy vs Reactive 비교 코드

dependencies {
    // core 모듈 의존성
    implementation(project(":simple-reactive-core"))
    
    // 테스트에서 core 모듈의 테스트 유틸리티(TestSubscriber) 사용
    testImplementation(project(":simple-reactive-core"))
}
