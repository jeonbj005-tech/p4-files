# P4 Drive File Manager OAuth 분석 및 수정 보고서

## 현재 v1.48 연동 방식 (2026-11-12)

- 차량은 Google Play services가 있으면 `AuthorizationClient`의 전체 Drive scope를 사용하고, 없으면 TV/Limited Input Device Code Flow로 전환해 `openid email drive.file`을 요청한다.
- 차량 Device Code Flow는 TV OAuth client ID와 secret이 필요하다. 로컬 `oauth.properties`에서 읽고, 현재 저장소에는 그 파일이 없다.
- 폰 확인 APK는 `AuthorizationClient`로 `drive.file`만 요청한다. Android OAuth client는 같은 Cloud 프로젝트, `com.polestar4.drivefilemanager`, 설치 APK 서명 SHA-1과 일치해야 한다.
- 두 APK는 같은 계정으로 각각 로그인한다. 차량이 Drive에 쓴 `P4FilesWakeStatus.json`을 폰이 읽는다. 이 통신은 영상/사진을 전송하지 않는다.
- 클라이언트 값은 `vehicle` flavor에만 빌드된다. `phone` APK에는 TV client ID/secret을 넣지 않는다. 차량 APK에 포함되는 client secret도 배포 앱에서 추출할 수 있으므로 기밀로 보호되는 값은 아니다.
- 같은 Cloud 프로젝트 안의 여러 OAuth client는 같은 앱의 클라이언트로 취급될 수 있고, `drive.file`은 그 앱이 만들었거나 사용자에게 앱 접근을 허용받은 파일만 보여준다. 실제 차량-폰 왕복은 실기기에서 확인해야 한다.

## 이전 v1.7 분석 (당시 기록)

## 원인

- PoleSight의 OAuth 자격 증명은 다른 앱/프로젝트용이므로 재사용할 수 없다.
- TV 및 입력 제한 기기용 Device Authorization Flow는 `drive.file`에서는 동작했지만 전체 Drive 범위 `https://www.googleapis.com/auth/drive`를 거부했다. 실기기 오류는 `Invalid device flow scope`였다.
- 따라서 전체 Drive 접근은 Android의 Google Play services `AuthorizationClient` 경로로 분리해야 한다.
- Redirect URI는 사용하지 않는다. Android OAuth 등록값은 package와 APK 서명 SHA-1이다.

## v1.3 이후 현재 v1.7 인증 구조

- 인증을 `Google Play services AuthorizationClient`로 전환했다.
- 전체 Drive 범위를 요청한다.
- 앱에 OAuth Client Secret을 내장하지 않으며 기존 기기 코드 인증용 자격 증명도 마이그레이션 시 제거한다.
- 액세스 토큰과 인증 코드는 Logcat에 기록하지 않는다.
- 만료된 토큰은 연결 상태로 표시하지 않고 다시 로그인을 요구한다.
- `CONFIG_CHECK`, `AUTH_START`, `AUTH_CALLBACK`, `TOKEN_EXCHANGE`, `DRIVE_API_REQUEST`, `AUTH_COMPLETE` 단계 로그를 유지한다.
- 인증 실패와 무관하게 USB/내장 저장소 탐색 및 다운로드 파일 열기는 계속 동작한다.
- v1.7에서 사용하지 않는 TV Device Code Flow와 Client Secret 입력 코드를 제거했다.
- 백그라운드 전송 중 토큰이 만료되면 작업을 실패 상태로 보존하고, 재로그인 후 사용자가 재시도할 수 있다.

## Google Cloud 등록 결과

- 프로젝트: `project-9ae3b421-dd7e-40ea-97d`
- Android OAuth Client: `P4 Drive File Manager Android`
- package/applicationId: `com.polestar4.drivefilemanager`
- debug SHA-1: `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A`
- Drive API: 활성화됨
- 요청 범위: `https://www.googleapis.com/auth/drive`
- OAuth 게시 상태가 테스트이면 등록된 테스트 사용자만 로그인할 수 있다.

다른 서명 키로 APK를 만들면 그 SHA-1에 맞는 Android OAuth Client를 추가해야 한다.

## 검증 결과

- `assembleDebug`: 성공
- `lintDebug`: 성공
- v1.1 `drive.file` 기기 코드 로그인 및 Drive 목록 API: 실기기 성공 확인
- v1.2 전체 Drive + 기기 코드 방식: Google이 scope를 거부하는 것을 실기기에서 확인
- AuthorizationClient 전체 Drive 로그인 및 휴대폰 Drive 목록 조회: 성공 확인
- v1.7 백그라운드 재개 전송과 파일 관리: 빌드 검증 완료, 실기기 확인 필요
- 차량 Google Play services 계정 선택 UI와 네트워크: 차량에서 확인 필요

## 재현 및 로그

1. v1.3을 기존 앱 위에 설치한다.
2. Google Drive → 계정 연결을 누른다.
3. Google 계정 및 전체 Drive 권한을 승인한다.
4. 폴더 목록을 확인하고 작은 파일 하나를 업로드/다운로드한다.
5. 실패 시 `adb logcat -s P4DriveAuth`에서 단계와 오류 코드만 확인한다.
