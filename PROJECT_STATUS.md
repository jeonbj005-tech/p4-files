# P4 Files 개발 현황

2026-11-10 · v1.44 / versionCode 54

- 대시캠 Drive 업로드: 영상을 차량 Downloads에 먼저 복사하던 경로를 없애고 DVR 응답을 1MB 단위로 Drive에 직접 스트리밍한다. 일반 SAF 업로드도 파일 크기 미상일 때 전체 캐시 복사를 하지 않는다.
- 업데이트: GitHub 최신 manifest URL을 앱 코드에 고정하고 주소 입력/변경 대화상자를 제거했다.
- 검증: `lintDebug`, `assembleDebug`, `assembleRelease` 성공. APK package/version 및 기존 차량 설치본과 같은 서명 SHA-1 확인.
- APK: `releases/P4-Files-v1.44.apk`, SHA-256 `61B95C2F0B814CE0C3966F6D6E729D6D4FE6C037CE0C78BF79FC216312EAED57`.
- GitHub 릴리스: 준비 중. 차량 실기기 업로드·업데이트와 DVR Range 응답은 미검증.

2026-11-02 · v1.43 / versionCode 53

- 시작 동작: 캐시를 먼저 표시하고 현재 달 DVR 목록을 앱 실행당 한 번 갱신한다. 완료된 과거 달은 월 캐시를 사용한다.
- 썸네일: 조회 후 선택 날짜·활성 종류에서 누락된 최대 50개만 한 번 요청한다. 오늘 기록이 없으면 최신 기록 날짜를 선택한다.
- 검증: `lintDebug`, `assembleDebug`, `assembleRelease` 통과. BuildConfig에 GitHub 최신 manifest URL이 포함되는 것 확인.
- 산출물: `releases/P4-Files-v1.43.apk`, SHA-256 `DA430EC0464D5B80E449342043EEBC891E0BBF7EA264C7FDBA2B224A68C82AB1`.
- 배포: https://github.com/jeonbj005-tech/p4-files/releases/tag/v1.43. 공개 최신 manifest 및 APK의 versionCode 53 / SHA-256 일치 확인.
- 서명: 차량 설치본과 동일한 SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A`.
- 차량 검증 필요: 시작 시 현재 달 갱신, 선택 날짜 썸네일 유지/로드, DVR 조회 중 녹화 영향.
- 업데이트: v1.31에서 쓰던 순정 Download 설치 동선을 복구했다. APK를 공용 Download에 저장하고 SHA-256 확인 후 순정 다운로드 앱을 연다. P4 Files에서 설치를 직접 요청하지 않는다.
- 업데이트 차량 검증 필요: 순정 Download 앱 열기와 설치 완료까지 차량에서 확인해야 한다.
- 주소 설정 회귀: v1.42에서 기본 주소가 비어 `업데이트 주소 설정` 대화상자가 표시됨. v1.43 빌드에 `https://github.com/jeonbj005-tech/p4-files/releases/latest/download/update.json`을 기본 내장했다. v1.42에서 v1.43을 받으려면 기존 대화상자에 이 주소를 한 번 입력한다.
