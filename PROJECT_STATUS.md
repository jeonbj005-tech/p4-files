# P4 Files 개발 현황

2026-11-01 · v1.40 / versionCode 50

- 수정: 대시캠 썸네일을 임시 파일에 기록·동기화한 뒤 원자적으로 교체해, 앱 재실행 시 유지되도록 저장 경로를 보강했다. 손상된 캐시만 제거하며 읽기 실패 시 원본 파일은 보존한다.
- 로그: 썸네일 읽기·저장·마이그레이션 실패를 민감한 URL 없이 해시 키로 기록한다.
- 산출물: `releases/P4-Files-v1.40.apk`, SHA-256 `6421EA903DCD0F4F2289F82DBD80C674F3A8AD78AD11951408115D15FF445422`.
- 확인: `lintDebug`, `assembleDebug` 통과. JUnit 4 직접 실행 11개 통과. Gradle `testDebugUnitTest`는 테스트 클래스 로딩 `ClassNotFoundException`으로 실패했다.
- 서명: SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A`로 v1.39와 동일하다.
- 차량 검증 필요: 앱 재실행 후 기존 썸네일 복원, 신규 썸네일 저장, 캐시 읽기/쓰기 오류 로그.
- 잔여 위험: Android 앱 데이터 삭제 또는 앱 제거·재설치는 `filesDir` 캐시를 지운다.\n
