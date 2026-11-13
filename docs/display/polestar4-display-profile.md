# Polestar 4 디스플레이 기준

## 기준 사양과 측정값

요청 기준은 센터 디스플레이 15.4인치, 1920×1200, 16:10이다. 패널 사양은 앱 창 크기와 다르며, 실차 논리 DPI·시스템 인셋·앱 창·콘텐츠 크기를 대신하지 않는다.

| 환경 | 패널/해상도 | DPI | 앱 영역 및 상태 |
|---|---:|---:|---|
| 기존 `P4FilesAAOS33` (`emulator-5554`) | AVD 기본 1080×600, ADB 크기 오버라이드 1920×1200 | 기본 120, 오버라이드 160 | 기존 환경은 유지. `dumpsys display`에서 오버라이드 앱 영역 1920×1104 확인 |
| 기존 `P4SoundPlayStore34` (`emulator-5556`) | 1080×600 가로 | 120 | P4 Sound 화면/오버레이가 보여 P4 Files 단독 기준샷으로 쓰지 않음 |
| 신규 `P4DisplayAAOS1920x1200_TEMP160` (`emulator-5558`) | 기본 1920×1200 가로, Android Automotive API 33 | 160 임시값 | 전용 AVD. 실차 DPI 측정값이 아니며 앱 창·인셋은 자동 진단 보고서에서 별도 수집 |
| Polestar 4 실차 | 요청 기준 1920×1200 | 미측정 | 실제 앱 창, 시스템 바, 콘텐츠 영역, 호환성 동작과 승인 권한은 미검증 |

`aaos-current.png`와 `phone-current.png`는 초기 진단 때 각 AVD에서 P4 Sound가 앞에 있던 증거다. 이후 P4 Files를 전용 AAOS AVD에 실행해 `p4-before-dashcam.png`와 `p4-before-home.png`를 기준 화면으로 저장했다. AVD에 차량 DVR 주소 `198.18.37.20`은 연결되지 않는다.

`p4-before-notification-dialog.png`는 테스트 AVD의 알림 승인 창, `p4-before.png`와 `p4-before.xml`은 사용할 수 없는 DVR endpoint 오류 창의 초기 증거다. 화면 비교는 팝업이 닫힌 대시캠 기준 이미지와 최종 이미지를 사용한다.

## UI 및 원인 분석

- 차량 화면은 `MainActivity`의 Kotlin 네이티브 Android Views다. Compose, WebView, XML 화면 레이아웃은 없다. 폰 확인 화면은 `PhoneStatusActivity`로 별도 구현됐다.
- Manifest에는 고정 방향, 화면 크기 호환 모드나 고정 앱 폭이 없다. 화면 치수는 dp/sp를 쓰고 루트에는 가로 스크롤 가능한 액션 줄과 세로 ScrollView가 있다.
- 차량 UI 선택은 Automotive 기능을 우선하며, 호환 차량이 아닌 경우 가로·600dp 조건을 사용한다. 가로 700dp부터 와이드 2단 레이아웃을 선택한다.
- 초기 AVD 스크린샷은 앱이 아닌 P4 Sound가 보였고, 앱 소스에는 구성 변경을 Manifest에서 받으면서 화면을 다시 그리지 않는 경로가 있었다. 앱 창의 현재 폭을 `WindowManager.currentWindowMetrics`로 구하고, 방향·창 크기 변경 때 현재 화면을 다시 배치하도록 수정했다. API 30 미만은 `Configuration.screenWidthDp`를 사용한다.
- 앱은 물리 패널 해상도, 앱 WindowMetrics, 시스템 바 인셋, 루트 콘텐츠 가시 영역을 각각 로그로 남긴다. 이들은 서로 같은 값으로 간주하지 않는다.
- Android 공개 앱 API는 실제 프레임워크 내부 호환성 확대 상태를 그대로 제공하지 않는다. 보고서는 큰 화면 지원·리사이즈 플래그·Manifest 호환 폭 한도와 시스템 덤프 신호를 기록한다. 직접 신호가 없으면 정확한 확대 적용 상태는 미확인으로 둔다.

## AVD 구성 및 복구

신규 Android Automotive API 33 AVD를 생성하거나 동일 프로파일 설정을 확인한다. 기존 AVD를 삭제하지 않고, `-wipe-data`도 사용하지 않는다.

```powershell
.\tools\New-P4DisplayAvd.ps1
```

이 프로파일의 해상도는 1920×1200, 초기 방향은 가로다. `TEMP160`은 임시 DPI를 뜻한다. 실차 DPI를 모르는 상태에서 비교 실행을 재현하기 위한 값이다.

기존 AVD에 임시 1920×1200@160 오버라이드를 적용할 때 원래 설정을 저장하고:

```powershell
.\tools\Set-P4DisplayProfile.ps1 -Action Configure -Serial emulator-5558
```

검증 뒤에는 원래 크기·밀도 오버라이드를 복구한다:

```powershell
.\tools\Set-P4DisplayProfile.ps1 -Action Restore -Serial emulator-5558
```

스크립트는 `ro.kernel.qemu=1`인 에뮬레이터에서만 동작한다. 복구 정보는 `%LOCALAPPDATA%\P4FilesDisplayProfile`에 저장하며, 실제 기기는 거부한다.

## 자동 진단 및 화면 비교

앱을 실행하고 디스플레이 덤프, `WindowMetrics` 로그, 접근성 트리, 스크린샷, 레이아웃 판정과 기준 이미지 비교를 저장한다:

```powershell
py -3 tools/verify_display.py --serial emulator-5558 --baseline docs/display/p4-before-dashcam.png
```

결과는 `docs/display/reports/display-diagnostics.json`과 `.md`, 스크린샷 PNG/XML 및 비교 이미지다. 검사 항목은 물리·유효 해상도, 논리 밀도, 앱 창과 콘텐츠 영역, 시스템 인셋, 잘린 접근성 노드, 차량/와이드 레이아웃, 호환성 제약, 세로 콘텐츠 넘침이다. 화면 비교 수치는 시각 변화량이며 기준 이미지와 픽셀이 같아야 한다는 뜻은 아니다.

## AAOS와 일반 Android의 차이

신규 AVD는 Android Automotive 시스템 이미지와 차량 기능 플래그를 가진다. `P4SoundPlayStore34`는 가로 화면이어도 Google Play 일반 Android 이미지라서 Automotive 기능·정책·런처 환경과 다르다. 일반 폰 AVD만으로는 차량 UI 완료 판정을 하지 않는다. AAOS AVD도 Polestar OEM 런처, 실제 차량 정책, DVR, 실차 DPI나 권한 승인을 증명하지는 않는다.

## PolDock v1.3.17 권한 기준 16개

PolDock `app/src/main/AndroidManifest.xml` 선언 목록과 P4 Files 차량 Manifest를 비교했다. Manifest 선언, 테스트 AVD 런타임 승인, 실차 OEM 승인 여부는 별도 상태다.

| PolDock 기준 권한 | P4 Files 원본 Manifest | v1.57 차량 APK 병합 Manifest | AVD 상태 | 디스플레이 필요 | 실차 승인 |
|---|---|---|---|---:|---|
| `SYSTEM_ALERT_WINDOW` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `FOREGROUND_SERVICE` | 선언 | 포함 | 선언; 런타임 팝업 없음 | 아니요 | 미검증 |
| `RECEIVE_BOOT_COMPLETED` | 미선언 | WorkManager에서 병합 | 일반 권한; OEM 정책 별도 | 아니요 | 미검증 |
| `INTERNET` | 선언 | 포함 | 일반 권한 | 아니요 | 미검증 |
| `CAMERA` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `REQUEST_INSTALL_PACKAGES` | 선언 | 포함 | 선언; 설치 출처 AppOp 승인 여부는 별도 | 아니요 | 미검증 |
| `POST_NOTIFICATIONS` | 선언 | 포함 | 이 AVD에서 화면 확인을 위해 허용 | 아니요 | 미검증 |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `ACCESS_FINE_LOCATION` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `ACCESS_COARSE_LOCATION` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `CAR_POWERTRAIN` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `CAR_EXTERIOR_ENVIRONMENT` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `CAR_INFO` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `CAR_SPEED` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `CAR_ENERGY` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |
| `CAR_VENDOR_EXTENSION` | 미선언 | 미포함 | 해당 없음 | 아니요 | 미검증 |

원본 차량 Manifest는 위 표 중 `FOREGROUND_SERVICE`, `INTERNET`, `REQUEST_INSTALL_PACKAGES`, `POST_NOTIFICATIONS`와 비교표 외 `FOREGROUND_SERVICE_DATA_SYNC`를 직접 선언한다. APK 병합 결과에는 AndroidX Media3가 `ACCESS_NETWORK_STATE`, WorkManager가 `WAKE_LOCK`과 `RECEIVE_BOOT_COMPLETED`를 추가한다. AndroidX Core가 서명 보호 수준의 앱 전용 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`을 생성한다. 이는 PolDock의 16개 기준 권한과 별개다. phone APK의 병합 결과에는 `INTERNET`과 앱 전용 서명 권한만 있다.

업데이트 DocumentsProvider의 `MANAGE_DOCUMENTS`는 provider 접근 제한이며 앱 `uses-permission` 선언이 아니다. AVD에서 허용한 `POST_NOTIFICATIONS`와 APK에 병합된 일반 권한은 실차 OEM 승인이 아니다. 디스플레이 측정은 추가 권한을 요구하지 않는다.

## 실차에서 추가 확인할 항목

- `wm size`, `wm density`, 회전, `dumpsys display/window`에서 물리·논리 해상도와 유효 앱 화면을 각각 기록한다.
- P4 Files 실행 중 `P4DisplayMetrics` 로그로 앱 WindowMetrics, 시스템 인셋, 콘텐츠 가시 영역과 실제 선택 레이아웃을 수집한다.
- 화면 잘림, 가로·세로 스크롤, 터치 목표의 실제 크기와 간격을 차량에서 확인한다.
- 시스템이 호환성 확대/레터박스를 적용하는지와 차량 OEM 정책을 확인한다. 현재 API 진단에서 직접 신호가 없으면 계속 미검증으로 남긴다.
- PolDock 권한 16개마다 선언·요청·AVD 승인·실차 OEM 승인 여부를 따로 점검한다. P4 Files에 필요하지 않은 기존 기능/권한은 화면 문제 해결을 위해 제거하지 않는다.
- 실차 DVR 스트림과 Drive 연결을 함께 확인하고 업로드 오류창이나 백그라운드 전환이 UI 진단을 방해하는지 확인한다.
