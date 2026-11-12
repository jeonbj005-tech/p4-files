# P4 Files 1.48

차량 APK는 Polestar 4에서 대시캠과 로컬/USB·Google Drive 파일 관리를 제공하고, 폰 APK는 차량 DVR 상태 확인만 제공합니다.

## 배포 APK

- `releases/P4-Files-v1.48.apk`: 차량용 대시캠·파일 매니저와 5분 DVR 상태 확인 기능
- `releases/P4-Files-phone-v1.48.apk`: 휴대폰에서 차량 상태 기록만 확인하는 앱
- 두 APK는 차량과 휴대폰에 각각 설치합니다. package ID가 같아 한 기기에 둘 다 설치하면 서로 교체됩니다.
- 휴대폰은 차량과 같은 Google 계정으로 Drive의 앱 파일 범위(`drive.file`)에 연결해 상태 JSON만 읽습니다. 영상·사진·파일목록은 전송하지 않습니다.
- **인증 설정 필요:** 차량용 TV OAuth client 값은 로컬 `oauth.properties`에 설정합니다. 이 저장소에는 현재 파일이 없으므로 Drive 상태 왕복은 아직 검증되지 않았습니다.
- v1.48은 테스트 산출물입니다. 차량 OAuth 설정과 실차 동기화를 확인하기 전까지 자동 업데이트의 latest manifest는 v1.47을 가리킵니다.

v1.9에서는 Polestar 시스템 폴더 선택기의 흰 배경/흰 글자 충돌을 줄이기 위해 플랫폼 위젯 테마를 명시적인 Light 테마로 분리했습니다. 앱 본문은 기존 다크 UI를 유지합니다.

v1.10부터 선택한 SAF 저장소 권한과 URI를 보존합니다. 시스템 폴더 선택기는 최초 선택 또는 사용자가 `저장소 변경`을 누를 때만 열리고, 이후에는 P4 Files 내부 탐색기를 바로 표시합니다.

v1.11에서는 가로 2분할 화면의 빈 차량 저장소와 빈 차량 Drive에 원인·해결 버튼을 표시합니다.

v1.12에서는 폴더를 `DIR` 파란 배지로, 파일을 확장자 배지로 구분하고 차량 Drive 빈 화면에서 바로 P4 Files 업로드를 시작할 수 있습니다.

v1.13에서는 차량 2분할 저장소와 일반 로컬 폴더에 정렬·새로고침 버튼을 추가했습니다.

## 주요 기능

- 차량에서 사용자가 시작하는 5분 DVR 상태 관찰(파일목록·녹화 모드 미변경)
- 폰에서 Drive에 기록된 차량 상태 확인(차량 잠금 이벤트를 감지하거나 차를 깨우지는 않음)

- 세로 화면: 컴팩트 단일 목록
- 700dp 이상 가로 화면: 차량 저장소와 Google Drive 2분할
- 차량: Google Play services가 있으면 AuthorizationClient 전체 Drive 인증, 없으면 TV·Limited Input Device Code 인증으로 `drive.file` 사용
- 폰 상태 확인 APK: Google Play services AuthorizationClient로 `drive.file`만 요청
- Drive 폴더 탐색, 업로드, 다운로드, 새 폴더
- Drive 이름 변경, 파일 복사, 상위 폴더 이동, 휴지통 이동
- SAF 로컬 폴더 탐색, 열기, 이름 변경, 복사, 이동, 삭제
- 이름/종류 검색 및 동영상·이미지·APK·PDF 필터
- 이름, 날짜, 용량 정렬
- 다중 선택 후 업로드·다운로드·삭제
- 즐겨찾기와 최근 항목
- 파일 정보와 20MB 이하 이미지/PDF 등의 임시 미리보기
- WorkManager 기반 지속 전송 대기열
- 전송 일시정지, 계속, 취소, 실패 재시도
- 1MB 청크 Drive 업로드와 저장된 세션 URI 기반 재개
- 대시캠 영상을 차량 임시 파일로 복사하지 않고 Drive에 직접 스트리밍 업로드
- 부분 다운로드 파일 기반 이어받기

## 제한 사항

- Google Drive 폴더 전체 재귀 다운로드/복사는 아직 제공하지 않습니다.
- 로컬 다중 업로드는 선택된 일반 파일만 처리합니다.
- Google Docs/Sheets/Slides 네이티브 파일은 export가 필요하므로 일반 다운로드 대상이 아닙니다.
- Drive 이동 UI는 현재 열린 폴더에서 상위 폴더로 이동하는 동작을 제공합니다.
- 휴대폰과 차량의 직접 Wi-Fi 전송은 v2.0 범위이며 포함되지 않았습니다.
- 운전 중 제한을 우회하지 않습니다. 차량에서는 주차 상태에서 사용해야 합니다.
- 전체 Drive scope는 제한된 OAuth 범위이므로 공개 배포 시 Google 검증이 필요할 수 있습니다.

## 빌드

- applicationId: `com.polestar4.drivefilemanager`
- versionName: `1.48`, versionCode `58`
- minSdk 26 / targetSdk 35
- `assembleVehicleDebug` → 차량 APK
- `assemblePhoneDebug` → 휴대폰 상태 확인 APK
- 각 flavor의 Lint를 별도로 실행합니다.

## 차량 ↔ 폰 상태 연결 설정

두 앱이 직접 페어링하지 않고, 같은 Google 계정의 Drive를 작은 상태 파일 하나의 중계로 사용합니다. 영상과 사진은 이 연결로 전송하지 않습니다.

현재 Cloud 프로젝트는 `P4 Files Drive Relay` (`p4-files-drive-relay`)이며 Drive API, 외부 테스트 동의 화면, `jeonbj005@gmail.com` 테스트 사용자를 구성했습니다. 차량 TV/Limited Input OAuth 클라이언트와 `drive.file`·`drive` 범위도 등록했습니다.

1. Google Cloud Console에서 Google Drive API를 활성화하고 OAuth 동의 화면에 `drive.file`과 차량의 Google Play services 경로에서 쓰는 `drive` 범위를 등록합니다. 테스트 상태라면 차량과 폰에서 로그인할 Google 계정을 테스트 사용자에 등록합니다.
2. **같은 Cloud 프로젝트 안에** OAuth 클라이언트를 각각 만듭니다.
   - 차량의 제한 입력 인증: `TVs and Limited Input devices`
   - Android 앱 인증: `Android`, 패키지 `com.polestar4.drivefilemanager`, 현재 APK 서명 SHA-1 `A2:18:83:47:12:EC:30:FE:C4:8E:D5:C4:56:04:8E:FA:E4:9C:2E:3A`
   - 현재는 Android 클라이언트 등록이 패키지+SHA-1 중복으로 거부되어 휴대폰 연결이 미완료입니다. 기존 Android OAuth 등록이 있는 프로젝트를 찾아 같은 프로젝트 정책을 맞춰야 합니다.
3. 차량 OAuth 클라이언트의 ID와 secret을 `oauth.properties.template` 형식대로 저장소 루트의 로컬 `oauth.properties`에 입력합니다. 이 파일은 Git에서 제외됩니다. secret은 차량 배포 APK에서 추출 가능하므로 강한 비밀키처럼 취급할 수 없습니다.
4. 차량용 APK를 다시 빌드해 차량에 설치합니다. 차량에서 `drive.file` 인증을 완료하고 `깨우기 테스트`를 시작합니다.
5. 폰용 APK에서 `Drive 연결`을 눌러 Android OAuth 인증을 완료하고, 차량과 동일한 Google 계정을 선택합니다. 차량이 만든 `P4FilesWakeStatus.json` 상태와 기록이 표시됩니다.

OAuth 클라이언트가 서로 다른 Cloud 프로젝트에 있거나 APK의 패키지/서명 SHA-1이 Android 클라이언트 등록 정보와 다르면 같은 계정이어도 앱 파일 접근이 이어지지 않을 수 있습니다. `drive.file`은 이 앱이 만든 파일 또는 사용자가 앱에 허용한 파일만 다룹니다. 전체 `drive` scope는 Google 검증이 필요한 제한 범위여서 공개 배포 시 별도 심사가 필요할 수 있습니다.

## 실기기 검증 상태

- 휴대폰에서 Google 전체 Drive 목록 조회 성공 이력 있음
- v1.49 차량 APK는 OAuth 설정을 넣어 빌드합니다. Android OAuth 중복 등록 문제와 차량-폰 간 상태 JSON 왕복은 미검증
- Polestar 4 실차에서 차량 로그인, 백그라운드 상태 업로드, 폰 수신을 함께 확인해야 합니다.
