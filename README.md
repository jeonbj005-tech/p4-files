# P4 Files 1.48

차량 APK는 Polestar 4에서 대시캠과 로컬/USB·Google Drive 파일 관리를 제공하고, 폰 APK는 차량 DVR 상태 확인만 제공합니다.

## 배포 APK

- `releases/P4-Files-v1.48.apk`: 차량용 대시캠·파일 매니저와 5분 DVR 상태 확인 기능
- `releases/P4-Files-phone-v1.48.apk`: 휴대폰에서 차량 상태 기록만 확인하는 앱
- 두 APK는 차량과 휴대폰에 각각 설치합니다. package ID가 같아 한 기기에 둘 다 설치하면 서로 교체됩니다.
- 휴대폰은 차량과 같은 Google 계정으로 Drive에 연결하며, 차량이 올리는 소형 상태 JSON만 읽습니다. 영상·사진·파일목록은 전송하지 않습니다.
- **인증 설정 필요:** 새 차량 로그인을 위해 로컬 `oauth.properties`의 `clientId`와 `clientSecret`이 필요합니다. 현재 작업 폴더에는 이 값이 없으므로 Drive 상태 동기화는 아직 검증되지 않았습니다.
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
- 휴대폰: Google Play services AuthorizationClient 전체 Drive 인증
- 폴스타/AAOS: Play services가 없으면 TV·Limited Input Device Code 인증으로 자동 전환
- 차량 호환 인증은 Google 정책에 따라 `drive.file` 범위(앱이 만든/허용받은 파일) 사용
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

## 실기기 검증 상태

- 휴대폰에서 Google 전체 Drive 목록 조회 성공 이력 있음
- v1.48의 두 APK는 빌드·정적 패키지 검증 대상이며, 차량-폰 간 상태 JSON 왕복은 미검증
- Polestar 4 실차에서 차량 로그인, 백그라운드 상태 업로드, 폰 수신을 함께 확인해야 합니다.
