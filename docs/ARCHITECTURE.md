# 구조와 확장 설계

## 현재 구조

- MainActivity: Compose 진입점, 포그라운드 복귀 시 날짜 갱신.
- RouteMateViewModel: StateFlow UI 상태, 직렬화된 사용자 명령, Room 저장, 추천 상태 조정.
- core/TaskParser.kt: TaskAnalyzer 포트와 RuleBasedTaskAnalyzer 구현. java.time만 사용하며 Android와 분리.
- core/Domain.kt: TaskItem, 상태, RouteProvider, VehicleGateway, RecommendationEngine. 순수 Kotlin.
- data: TaskRecord/SettingsRecord ↔ 도메인 모델 변환, DAO, Room DB, Repository.
- platform/SpeechInput.kt: Android 인식기 Intent 어댑터.
- ui: 홈, 등록, 이동 추천, 기록 및 공통 구성요소.

## 저장과 무결성

- 업무: 제목, 카테고리, ISO 마감일, 실제 등록 장소, 중요도, 상태, 메모, 생성/완료 시각, 미룬 이동 ID, 샘플 여부, 별도 데모 경유지.
- 설정: 첫 초기화 표식, 데모 경로 ID, 차량 단계, 이동 ID.
- 첫 실행 샘플 생성은 설정 행 확인과 함께 Room 트랜잭션으로 실행합니다. 업무가 0개여도 재생성하지 않습니다.
- 여러 분석 결과 저장 + 추천 상태 재계산은 한 Room 트랜잭션입니다. 저장 실패 시 부분 삽입으로 남기지 않습니다.
- 이동 상태와 상태 변경 업무는 DAO 트랜잭션으로 함께 저장합니다.
- 실제 등록 장소와 가상 데모 경유지를 별도 필드에 저장해 원래 장소를 보존합니다.
- 완료 시각은 사용자의 체크에서만 기록합니다. 차량 도착 이벤트는 완료 값을 만들지 않습니다.
- 업무 수가 적은 MVP라 전체 활성 업무를 읽어 추천을 재계산합니다. 대규모 서비스 단계에서는 기간/상태별 DAO 쿼리, 인덱스, 페이징을 적용하세요.
- DB 버전 1, destructive migration 없음. Room 스키마는 첫 KSP 빌드 후 app/schemas에 생성됩니다. 이후 변경 시 export된 스키마와 명시적인 Migration을 커밋하세요.

## 이동 상태 머신

~~~text
READY --주행 시작--> DRIVING --목적지 도착--> ARRIVED
  ^                                             |
  |                                           주차 완료
  |                                             v
  +---------- 다음 이동 준비 (ID + 1) -------- PARKED
~~~

출발 전 할당된 이동 ID를 주행/도착/주차 동안 유지합니다. 미룬 일은 해당 이동 ID에서 숨깁니다. 주차 후 다음 이동을 준비하면 추천을 먼저 확인할 수 있고, 그 뒤 출발합니다. 실제 GPS나 차량 연동을 흉내 낸 것일 뿐 자동 감지는 하지 않습니다.

## UI와 안전

- 바깥 Scaffold가 시스템 인셋을 처리하고, LazyColumn으로 세로 스크롤을 제공합니다.
- 탭별 SaveableStateHolder와 업무 Saver로 탭 전환·화면 회전 시 입력값을 보존합니다. 영구 저장은 사용자가 저장 버튼을 누른 후입니다.
- 주행 중에는 집중 화면으로 전환하고 등록·기록 탐색과 수정·완료 작업을 차단합니다. 데모 도착 버튼은 시연을 위한 예외입니다.
- 주행 차단은 데모 상태에만 의존합니다. 실제 주행을 감지하거나 안전을 보장하는 기능이 아닙니다.
- 자동 TTS나 백그라운드 시스템 알림은 제공하지 않습니다. 안내는 현재 Compose 화면과 스낵바에서 확인합니다.

## Pleos Connect 확장 지점 (향후 작업)

아래는 교체 설계이며 현재 구현되어 있다는 뜻이 아닙니다. 최신 공식 SDK 문서·배포 정책과 권한 요건을 확인해야 합니다.

1. **Vehicle SDK**: VehicleGateway의 데모 버튼 전환 대신 실제 차량 상태 이벤트 수신기를 추가합니다. 중복/역순 이벤트, 연결 해제, 프로세스 재시작을 처리하고 READY/DRIVING/ARRIVED/PARKED로 정규화합니다.
2. **NaviHelper**: RouteProvider를 실제 경로·경유 후보 조회 어댑터로 교체합니다. 현재 extraMinutes는 샘플입니다. 승인 성공은 내비게이션 반영 응답 후 확정하고 실패하면 진행 중 전환을 롤백해야 합니다.
3. **FusedLocation**: 별도의 위치 공급자에 좌표·관측 시각·정확도를 담고, 사용자가 동의한 위치만 경로 공급자에 전달합니다. 지오펜스 도착도 할 일 자동 완료 신호로 쓰지 않습니다.
4. **SpeechToText**: platform의 SpeechIntentProvider 대신 차량 SDK의 음성 입력 어댑터를 연결합니다. 취소·권한·언어 오류를 텍스트 입력으로 회복합니다.
5. **TextToSpeech**: 화면 안내와 분리된 안내 출력 포트를 추가합니다. 실제 주행 중에는 짧은 문장, 중복 억제, 우선순위, 사용자의 음소거를 지원해야 합니다.
6. **외부 AI**: TaskAnalyzer 구현만 교체하되 구조화 결과 검증, 타임아웃, 실패 시 RuleBasedTaskAnalyzer fallback을 추가합니다. 비밀 API 키는 앱에 넣지 말고 인증된 서버 프록시를 사용하세요. 클라우드 전송 전 음성/텍스트 개인정보 동의가 필요합니다.

## 출시/공모전 전 추가 검증

- 실제 Pleos 앱 템플릿과 서명·배포·차량 화면 규격 적용.
- 운전자 주의 분산 정책 및 차량 UX/안전 요건에 맞춘 실제 주행 잠금.
- 실제 SDK의 권한/라이프사이클/백그라운드 제약 검증.
- 다크 모드, 대형 글꼴, 접근성, 다양한 화면 크기와 로케일.
- 실제 경로·영업시간·위치 정확도 및 오탐 안내.
- 시스템 알림 권한과 채널, 필요한 경우 WorkManager/Foreground Service 설계.
- Room 마이그레이션, 데이터 백업/복원 정책, 개인정보 처리방침.
