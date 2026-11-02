# P4 Files 개발 현황

2026-11-02 · v1.42 / versionCode 52

- 시작 동작: 캐시를 먼저 표시하고 현재 달 DVR 목록을 앱 실행당 한 번 갱신한다. 완료된 과거 달은 월 캐시를 사용한다.
- 썸네일: 조회 후 선택 날짜·활성 종류에서 누락된 최대 50개만 한 번 요청한다. 오늘 기록이 없으면 최신 기록 날짜를 선택한다.
- 검증: `lintDebug`, `assembleDebug`, `assembleRelease` 통과. `testDebugUnitTest`는 기존 `DvrStructureTest`의 Gradle 테스트 워커 `ClassNotFoundException`으로 실패.
- 산출물: `releases/P4-Files-v1.42.apk`, SHA-256 `26C9CCD848F38EA0026C9D24DB663E8DB825AE7DB2B2787348F140D7E49E76E9`.
- 배포 준비: update manifest는 versionCode 52 / v1.42 APK와 일치하도록 갱신. GitHub Release 업로드 대기.
- 서명: 차량 설치본과 동일한 SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A`.
- 차량 검증 필요: 시작 시 현재 달 갱신, 선택 날짜 썸네일 유지/로드, DVR 조회 중 녹화 영향.
- 업데이트: v1.31에서 쓰던 순정 Download 설치 동선을 복구했다. APK를 공용 Download에 저장하고 SHA-256 확인 후 순정 다운로드 앱을 연다. P4 Files에서 설치를 직접 요청하지 않는다.
- 업데이트 차량 검증 필요: 순정 Download 앱 열기와 설치 완료까지 차량에서 확인해야 한다.
