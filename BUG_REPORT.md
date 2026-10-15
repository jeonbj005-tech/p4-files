# P4 Files 버그 리포트

## BUG-001: 다운로드한 APK의 `열기`가 패키지 설치 화면을 실행하지 않음

- 상태: v1.25에서 수정 구현 / 차량 실기기 검증 필요
- 발견일: 2026-10-15
- 확인 버전: P4 Files v1.24 (`versionCode 34`)
- 환경: Polestar 4 차량 Android Automotive OS
- 관련 파일: `P4-Files-v1.24-Persistent-Cache-Fixed-Calendar.apk`
- 증거 화면: `1000070722.jpg`

### 재현 절차

1. P4 Files에서 Google Drive의 APK 파일을 다운로드한다.
2. 홈에서 `다운로드한 파일`을 연다.
3. 다운로드된 APK를 선택한다.
4. 파일 작업 메뉴에서 `열기`를 누른다.

### 실제 결과

`열기`를 눌러도 Android 패키지 설치 화면이 실행되지 않는다. 사용자에게 설치 실패 원인이나 권한 안내도 표시되지 않는다.

### 기대 결과

APK 다운로드가 완전히 끝난 뒤 패키지 설치 화면이 실행되어야 한다. 설치 권한이 없거나 차량 정책으로 차단된 경우에는 다음 원인을 구분해 안내해야 한다.

- 이 앱의 `알 수 없는 앱 설치` 권한이 꺼져 있음
- 차량 정책 또는 사용자 제한으로 APK 설치가 차단됨
- 패키지 설치기를 찾을 수 없음
- APK 파일 또는 `FileProvider` URI 접근 실패
- APK 다운로드가 완료되지 않았거나 파일이 손상됨

### 추정 원인

현재 일반 파일과 APK 모두 `ACTION_VIEW` 경로로 열고 있다. AAOS에서는 APK에 대해 일반 `ACTION_VIEW`가 패키지 설치기로 연결되지 않거나, MIME/URI 권한 및 알 수 없는 앱 설치 권한 확인이 별도로 필요할 수 있다.

### 수정 방향

1. `.apk` 확장자 또는 `application/vnd.android.package-archive` MIME을 별도로 감지한다.
2. APK는 `Intent.ACTION_INSTALL_PACKAGE`와 `FileProvider`의 `content://` URI를 사용한다.
3. `FLAG_GRANT_READ_URI_PERMISSION`을 반드시 추가한다.
4. Android 8 이상에서는 `packageManager.canRequestPackageInstalls()`를 확인한다.
5. 권한이 없으면 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`의 현재 앱 설정 화면으로 안내한다.
6. 설치 인텐트를 처리할 앱이 없거나 차량 정책으로 거절되면 명확한 오류 메시지를 표시한다.
7. 설치 요청 전에 파일 존재 여부, 크기 및 다운로드 완료 상태를 검증한다.

### 회귀 테스트

- 차량의 `다운로드한 파일`에서 APK 선택 → 설치 화면 표시
- Google Drive에서 방금 받은 APK → 다운로드 완료 후 설치 가능
- APK 이외의 동영상·이미지·PDF 열기 동작 유지
- 설치 권한이 꺼진 상태에서 권한 설정 안내 표시
- 설치 권한 허용 후 앱으로 돌아와 다시 설치 가능
- 손상되거나 0바이트인 APK는 설치 화면 대신 오류 표시

### v1.25 수정 내역

- APK 파일을 일반 `ACTION_VIEW`가 아닌 `ACTION_INSTALL_PACKAGE`로 분기
- `REQUEST_INSTALL_PACKAGES` 권한 선언
- `FileProvider` URI와 읽기 권한 전달
- 차량에서 설치 권한 설정 화면이 차단되면 APK를 공용 `Download`에 저장하고 순정 파일 앱으로 연결
- `canRequestPackageInstalls()` 사전 확인
- 권한이 없으면 현재 앱의 `알 수 없는 앱 설치` 설정 화면으로 안내
- 설정에서 복귀하면 보류한 APK 설치 자동 재시도
- 패키지 설치기 부재 또는 차량 정책 거절 시 오류 안내
- GitHub Releases 업데이트 다운로드에도 같은 설치 경로 적용

# 2026-10-15 — DVR 1초 영상 대량 생성 / 차량 업데이트 설치 차단

- 증상: 대시캠 화면 사용 후 `NOR_...MP4` 파일이 1초 간격으로 대량 생성됨(현장 확인 1,129개).
- 원인: 차량에서 파일목록 모드 진입/복귀가 녹화 파일을 분할할 수 있는데 앱 시작 시 자동 조회가 실행됨. 구버전의 썸네일 조회는 이 전환을 더 자주 유발할 수 있었음.
- 수정(v1.31): 시작 시 DVR HTTP 요청을 전혀 보내지 않고 캐시만 표시. 사용자가 `내역 새로고침`을 누른 경우에만 월별 목록을 한 번 조회.
- 업데이트 문제: AAOS 정책상 P4 Files 자체가 알 수 없는 앱 설치 권한을 받을 수 없음.
- 수정(v1.31): APK는 공용 Download에 저장하고, 설치 시도/권한 설정 버튼 대신 순정 Download 파일 앱을 기본 설치 경로로 사용.

