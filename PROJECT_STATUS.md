# P4 Files 개발 현황

2026-11-02 · v1.41 / versionCode 51

- 시작 동작: 캐시를 먼저 표시하고 현재 달 DVR 목록을 앱 실행당 한 번 갱신한다. 완료된 과거 달은 월 캐시를 사용한다.
- 썸네일: 조회 후 선택 날짜·활성 종류에서 누락된 최대 50개만 한 번 요청한다. 오늘 기록이 없으면 최신 기록 날짜를 선택한다.
- 검증: `compileDebugUnitTestKotlin`, `lintDebug`, `assembleDebug` 통과. JUnit 4 직접 실행 11개 통과.
- 산출물: `releases/P4-Files-v1.41.apk`, SHA-256 `FB81C74E4080E56F8FE842A46B01318B1EFBB99856F2D35E83A7EB501A96248F`.
- 서명: 차량 설치본과 동일한 SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A`.
- 차량 검증 필요: 시작 시 현재 달 갱신, 선택 날짜 썸네일 유지/로드, DVR 조회 중 녹화 영향.
