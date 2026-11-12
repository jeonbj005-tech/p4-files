# GitHub Releases 앱 업데이트

P4 Files는 저장소 주소를 앱에 고정해 최신 `update.json`을 확인한다. 저장소와 Release는 APK를 차량에서 받을 수 있도록 공개 상태여야 한다.

## Release assets

각 버전 Release에 `P4-Files-v<versionName>.apk`와 `update.json`을 올린다. 앱은 아래의 latest manifest URL만 사용하며, APK URL은 버전별 Release 경로와 파일명을 검증한다.

```text
https://github.com/jeonbj005-tech/p4-files/releases/latest/download/update.json
```

```json
{
  "packageName": "com.polestar4.drivefilemanager",
  "versionCode": 57,
  "versionName": "1.47",
  "minimumSdk": 26,
  "apkUrl": "https://github.com/jeonbj005-tech/p4-files/releases/download/v1.47/P4-Files-v1.47.apk",
  "sha256": "APK SHA-256 64자리",
  "size": 12345678,
  "signingCertificateSha256": "서명 인증서 SHA-256 64자리",
  "notes": "업데이트 설명"
}
```

릴리스 작업은 실제 APK에서 `size`, APK SHA-256, 서명 인증서 SHA-256을 계산해 manifest와 Release asset에 기록해야 한다. 예시는 [`update-manifest.example.json`](update-manifest.example.json) 참고.

## 앱 동작

1. 앱 시작 후 하루에 한 번 최신 manifest를 확인하고, 새 버전이면 백그라운드로 내려받는다. 수동 `업데이트` 버튼은 즉시 확인한다.
2. 패키지, 버전, 최소 Android API, 저장소 URL, 크기, APK SHA-256, 현재 설치본과 같은 서명을 확인한다. 다운로드 중단은 Range 요청으로 이어받고, 검증 완료 APK만 설치 UI에 전달한다.
3. 설치는 Android 시스템 확인 화면에서 사용자가 승인한다. 설치 출처 권한이 차량에서 제한되면 Download/USB 저장을 안내한다. APK 저장은 설치 성공으로 간주하지 않는다.
4. 시스템 화면에서 돌아오면 설치된 versionCode와 서명을 확인한다.

APK는 앱 내부 `files/Updates/`에 보관한다. `ACTION_OPEN_DOCUMENT` 파일 선택기에서 `P4 Files 업데이트 저장소`를 선택해 APK를 고르면, 앱은 공급자 authority·크기·SHA-256을 확인하고 Android 설치기로 읽기 권한을 전달한다. 저장소는 읽기 전용이고, `MANAGE_DOCUMENTS`는 공급자의 시스템 접근 제한으로만 선언한다.

전체 경로, 권한 비교, API 호환성, 미실행 테스트 항목은 [`docs/AUTO_UPDATE_FLOW.md`](docs/AUTO_UPDATE_FLOW.md)에 기록한다.
