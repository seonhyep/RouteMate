# 실제 검증 범위

제작일: 2026-11-04

## 수행 완료

1. 순수 Kotlin 코드(core/TaskParser.kt, core/Domain.kt)와 세 JUnit 테스트 파일을 Kotlin 2.0.21 JVM 컴파일러로 컴파일했습니다.
2. Java 17.0.21 환경에서 JUnit 4.13.2를 실행했습니다.
3. 문장 분석 30개, 추천 14개, 차량 상태 8개, 합계 **52개 통과**했습니다.
4. Android/Compose/Room 코드는 별도 정적 검토를 수행했습니다. 이 검토는 실제 Android 빌드를 대체하지 않습니다.
5. Gradle 8.9 공식 Wrapper JAR를 포함했고 Gradle 배포 ZIP의 공식 SHA-256 값을 wrapper 설정에 지정했습니다.

실제 JUnit 출력:

~~~text
JUnit version 4.13.2
....................................................
Time: 0.359

OK (52 tests)
~~~

## 실행하지 못한 검증

- Android SDK가 제공되지 않아 전체 Gradle Sync / assembleDebug / APK 설치는 실행하지 않았습니다.
- Compose 화면 렌더링, 기기 음성 인식, Android 시스템 권한 흐름, 실기기 Room DB 동작은 실행 검증하지 않았습니다.
- 포함된 Room 계측 테스트 4개는 작성만 했으며 미실행입니다.
- 따라서 **스마트폰에서 실행 확인 완료**, **APK 빌드 성공**, **모든 Android 테스트 통과**라고 주장하지 않습니다.

README의 Android Studio 절차로 빌드하고 docs/TESTING.md를 따라 기기 검증한 뒤 공모전 개발을 이어가세요.
