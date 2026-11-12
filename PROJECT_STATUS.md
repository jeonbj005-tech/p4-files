# P4 Files 개발 현황

2026-11-12 · OAuth 준비 / v1.49

- Google Cloud 전용 프로젝트 `P4 Files Drive Relay` (`p4-files-drive-relay`) 생성, Drive API 활성화, 외부 테스트 동의 화면 생성 및 `jeonbj005@gmail.com` 테스트 사용자 등록 완료.
- 앱 범위로 `drive.file`과 전체 `drive`를 등록. 공개 배포 전에 전체 Drive 범위의 Google 검증 요건을 해결해야 한다.
- 차량 TV/Limited Input 클라이언트 생성. JSON에서 ID/secret을 로컬 `oauth.properties`로 옮겼으며 `.gitignore` 적용을 확인했다. 비밀값은 Git에 넣지 않는다.
- Android OAuth 등록은 `com.polestar4.drivefilemanager` + APK SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A` 조합이 이미 사용 중이라는 Google 오류로 거부됐다. 현재 계정에서 확인 가능한 기존 프로젝트의 활성/삭제된 클라이언트에는 이 앱 등록이 없었다. 폰 OAuth는 미완료이며 서명 키를 바꿔 우회하지 않는다.
- versionCode 59 / versionName 1.49로 올려 기존 설치본 위에 설치 가능한 차량 APK를 빌드한다. APK가 공개 배포되면 TV client secret은 추출 가능하다.
- 실차 차량 로그인·폰 연동·Drive 왕복은 미검증.

2026-11-12 · 실제 Automotive 에뮬레이터 및 폰 화면 점검

- 차량 APK v1.48 / versionCode 58을 Android Automotive API 33 AVD `P4FilesAAOS33`에 설치했다. 최초 알림 권한 요청에서 허용한 뒤 `MainActivity`가 포그라운드에 유지되고 달력/목록 화면이 렌더링됐다. 프로세스 크래시는 관찰되지 않았다.
- DVR `198.18.37.20:80` 연결은 8초 후 실패했고 앱은 오류 대화상자와 상태 문구를 표시했다. 이 AVD에는 차량 DVR 서비스가 없어 실차 DVR 동작을 검증한 것은 아니다. 제공되는 AAOS 시스템 이미지 중 API 32는 없어 API 33으로 점검했다.
- 폰 APK v1.48 / versionCode 58을 Android 14 API 34 AVD에서 실행했다. `PhoneStatusActivity`가 유지되고 Drive 재인증 안내가 표시됐다. OAuth 값이 비어 있어 Google 로그인을 진행하지 않았으며 차량-폰 데이터 왕복은 미검증이다.
- 이 점검은 UI 시작/오류 표시 smoke test이며 코드 변경이나 APK 재빌드는 없었다. AAOS 실차, Google Drive 인증·동기화, 차량 데이터 수신은 남은 검증 항목이다.

2026-11-12 · 차량/폰 APK 에뮬레이터 설치 점검

- 차량 APK v1.48을 `CameraAlertSettingsPreview32` 에뮬레이터에 v1.46 위로 설치했고 `MainActivity` 실행을 확인했다. 이 기기는 실제 SDK 31 일반 Android 이미지이며 Automotive feature가 없다. DVR 주소 `198.18.37.20:80` 연결은 8초 후 실패했다.
- 폰 APK v1.48을 `P4SoundPlayStore34` SDK 34 에뮬레이터에 설치했고 `PhoneStatusActivity` 및 Drive 연결/새로고침 UI를 확인했다.
- 앱 프로세스 크래시는 관찰되지 않았다. Google 로그인/Drive 접근은 수행하지 않았고, OAuth 자격 증명도 비어 있어 차량-폰 왕복은 미검증이다. 이 에뮬레이터 점검은 Polestar AAOS 실차 검증을 대체하지 않는다.

2026-11-12 · 차량·폰 Drive 연동 설정 점검

- `TV_OAUTH_CLIENT_ID/SECRET`을 차량 flavor에만 주입하도록 Gradle 설정을 분리했다. 폰 APK에는 차량 OAuth 자격 증명이 포함되지 않는다.
- OAuth 템플릿에 TV OAuth client의 두 값을 명시하고, 같은 Google Cloud 프로젝트·Drive API·`drive.file`·폰 패키지/서명 SHA-1 등록 절차를 README에 추가했다.
- 확인: v1.48 폰 APK 서명 SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A` (apksigner 확인).
- 확인: 두 flavor `lint`와 `assemble` 성공(현재 비어 있는 OAuth 설정으로 빌드). 실제 연결용 APK는 OAuth 설정을 채운 뒤 새 versionCode로 다시 빌드해야 한다.
- 미완료: 로컬 `oauth.properties` 부재. Google Cloud OAuth 클라이언트 구성, 실제 APK 재빌드, Drive 왕복 및 차량 로그인은 미검증.

2026-11-12 · v1.48 / versionCode 58 — 차량용·폰용 APK 분리

- 차량 APK: `releases/P4-Files-v1.48.apk`, 8,098,511 bytes, SHA-256 `3230C6D0C233BDC45A760362A6B40A5DA44106ED100B3B6CC6005111A3847167`, 시작 화면 `MainActivity`.
- 폰 확인 APK: `releases/P4-Files-phone-v1.48.apk`, 8,103,395 bytes, SHA-256 `A656239BB88D5E287F1E89CDA33A618367CCE011478A2C9F00D159CE06B9D4AC`, 시작 화면 `PhoneStatusActivity`; INTERNET 외 차량 백그라운드 권한은 없음.
- 두 APK 모두 package `com.polestar4.drivefilemanager`, versionCode 58, minSdk 26, targetSdk 35, 기존 v1.47과 같은 Android Debug 서명 인증서 SHA-256 `7D48CDBF4BD2898796294E6AB8A9626353375AE6A43EB98A661207079DA9B756`. 차와 폰에 각각 설치하며 같은 기기에 동시 설치할 수 없음.
- 폰 앱은 같은 Google 계정의 `drive.file` 범위에서 차량 상태 JSON만 15초 간격으로 읽는다. 영상·사진·목록 권한은 요청하지 않는다. 같은 Google Cloud 프로젝트의 차량/Android OAuth 클라이언트가 앱 파일을 공유하는지는 실차 왕복 검증이 필요하다.
- 차 OAuth 설정: 현재 작업 폴더에 `oauth.properties`가 없고 v1.47 APK의 `TV_OAUTH_CLIENT_ID/SECRET`도 비어 있음. 새 차량 인증과 폰 왕복은 미검증이며, 앱 업데이트 latest manifest는 안정 배포 v1.47을 유지한다.
- 검증: `lintVehicleDebug`, `lintPhoneDebug`, `assembleVehicleDebug`, `assemblePhoneDebug` 성공. APK 패키지·진입 Activity·서명·권한 정적 확인. `testVehicleDebugUnitTest`는 `DvrStructureTest` `ClassNotFoundException`으로 실행 실패(테스트 클래스 파일은 컴파일됨).

2026-11-12 · v1.47 / versionCode 57

- 깨우기 테스트: 대시캠 화면의 `깨우기 테스트`가 5분 동안 WorkManager 전경 작업으로 DVR `/status`만 15초 간격 확인한다. 파일목록·썸네일·영상 요청이나 DVR 모드 전환은 하지 않는다.
- 기록: 최근 상태 응답/오류와 시각을 앱 내부에 보관하고, 지속 알림에서 중지할 수 있다. 도어 잠금 이벤트를 읽거나 차를 깨우는 기능은 아니다.
- 검증: `lintDebug`, `assembleDebug` 성공. v1.47 APK package/version `com.polestar4.drivefilemanager` / 57 / 1.47, 기존 v1.46과 서명 인증서 일치.
- APK: `releases/P4-Files-v1.47.apk`, 8,288,503 bytes, SHA-256 `23EEF9BB3052DEC4C538C0DB94F2E7BE5DA74559511FFEF796A9EEFE8BE0F417`. 서명 인증서 SHA-256 `7D48CDBF4BD2898796294E6AB8A9626353375AE6A43EB98A661207079DA9B756`.
- 미검증: Polestar 앱 잠금·해제 뒤 DVR 상태 응답, 차량에서 5분 전경 작업 유지 여부.

2026-11-12 · v1.46 / versionCode 56

- 업데이트 저장소: 검증한 APK를 앱 내부 `files/Updates/`에 저장하고 읽기 전용 `DocumentsProvider`를 `P4 Files 업데이트 저장소`로 시스템 문서 선택기에 공개.
- 설치: 설치 버튼에서 `ACTION_OPEN_DOCUMENT`를 열고 사용자가 가상 저장소의 APK를 선택. provider authority·크기·SHA-256을 재확인해 Android 설치 화면으로 전달하며, 사용자 승인과 설치 후 버전·서명 확인은 유지.
- 권한/호환: 앱은 특권 `MANAGE_DOCUMENTS`를 요청하지 않는다. Provider에만 시스템 접근 제한을 선언했고, 기존 external `FileProvider` 경로는 v1.45 대기 설치 및 저장 fallback용으로 유지.
- 검증: `lintDebug`, `assembleDebug`, `assembleRelease` 성공. API 31 AVD에서 `P4 Files 업데이트 저장소` 표시 → v1.46 APK 선택 → Android 설치 확인 → PackageManager versionCode 56 확인까지 완료. 설치 흐름을 확인하기 위해 같은 v1.46 코드를 담은 임시 debug versionCode 55를 출발 버전으로 사용.
- 차량 한계: Polestar 4 실차 DocumentsUI 검색, 설치 정책과 사용자 승인 결과는 미검증.
- APK: `releases/P4-Files-v1.46.apk`, 6,420,506 bytes, SHA-256 `78C15F485DC54C9D82FD201AC69805153A1EC84C8CFDAB5923DA2C610AD2A1F2`. 이전 배포본과 같은 서명 인증서 SHA-256 `7D48CDBF4BD2898796294E6AB8A9626353375AE6A43EB98A661207079DA9B756`.
- 제한: 앱 제거 시 내부 APK도 삭제된다. 차량 DocumentsUI의 provider 검색 및 설치기 전달은 실차에서 확인해야 한다.

2026-11-11 · v1.45 / versionCode 55

- 업데이트 주소: GitHub 최신 manifest URL을 앱에 고정. 앱 시작 시 하루 한 번 확인하고 새 APK를 내려받으며, 수동 업데이트 확인도 유지.
- 검증: manifest와 APK의 package/version/minSdk/Release URL/크기/SHA-256/서명 인증서를 대조. 중단된 다운로드는 Range 응답을 확인해 이어받고, 완성본만 시스템 설치기로 전달.
- 설치: 시스템 설치 확인을 사용자가 승인해야 함. 차량에서 설치 출처가 제한되면 Download 또는 SAF로 지정한 USB 위치에 저장. 설치 후 versionCode와 서명 재확인.
- 권한: 기존 INTERNET, REQUEST_INSTALL_PACKAGES, FileProvider만 사용. 특권 설치 우회 권한 추가 없음.
- 검증: `lintDebug`, `assembleDebug`, `assembleRelease` 성공. APK package/version/minSdk/signature/hash/size 확인.
- APK: `releases/P4-Files-v1.45.apk`, 6,412,314 bytes, SHA-256 `59D077113DC448B514A7797CA3880570BEDF6F3F3F66ADC1DB6AA00D98B35756`.
- 에뮬레이터(API 31)에서 v1.45 설치·실행과 최신 manifest 확인 성공. v1.44는 새 버전 감지 후 다운로드 중 같은 Downloads 파일을 다시 복사해 `ENOENT`로 실패하는 것을 재현했고, v1.45는 APK를 `Updates/`로 분리.
- 따라서 v1.44에서 첫 v1.45 업데이트는 APK 직접 설치/USB 사이드로드가 필요할 수 있음. Polestar 4 실차 설치 및 자동 재개 다운로드는 미검증.

2026-11-10 · v1.44 / versionCode 54

- 대시캠 Drive 업로드: 영상을 차량 Downloads에 먼저 복사하던 경로를 없애고 DVR 응답을 1MB 단위로 Drive에 직접 스트리밍한다. 일반 SAF 업로드도 파일 크기 미상일 때 전체 캐시 복사를 하지 않는다.
- 업데이트: GitHub 최신 manifest URL을 앱 코드에 고정하고 주소 입력/변경 대화상자를 제거했다.
- 검증: `lintDebug`, `assembleDebug`, `assembleRelease` 성공. APK package/version 및 기존 차량 설치본과 같은 서명 SHA-1 확인.
- APK: `releases/P4-Files-v1.44.apk`, SHA-256 `61B95C2F0B814CE0C3966F6D6E729D6D4FE6C037CE0C78BF79FC216312EAED57`.
- GitHub 릴리스: [v1.44](https://github.com/jeonbj005-tech/p4-files/releases/tag/v1.44) 공개. 최신 manifest가 versionCode 54와 APK SHA-256을 반환하고 APK 링크 HEAD 200 및 6,404,066바이트를 확인.
- 차량 실기기 업로드·업데이트와 DVR Range 응답은 미검증.

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
