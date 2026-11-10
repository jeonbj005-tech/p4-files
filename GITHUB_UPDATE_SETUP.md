# GitHub Releases 업데이트 설정

P4 Files v1.25부터 Google Drive 권한과 무관한 HTTPS 업데이트를 지원한다.

## 1. GitHub 저장소 준비

이 프로젝트를 GitHub 저장소에 올린다. 저장소는 APK를 차량에서 내려받을 수 있도록 공개 저장소를 권장한다. 비공개 저장소의 Release 다운로드에는 인증 토큰이 필요하므로 현재 차량 업데이트 기능과 맞지 않는다.

## 2. Release 파일

각 Release에는 버전이 붙은 APK와 `update.json`을 첨부한다.

- `P4-Files-v<versionName>.apk` (예: `P4-Files-v1.44.apk`)
- `update.json`

`update.json` 형식:

```json
{
  "versionCode": 35,
  "versionName": "1.25",
  "apkUrl": "https://github.com/OWNER/REPOSITORY/releases/latest/download/P4-Files.apk",
  "sha256": "P4-Files.apk의 SHA-256 64자리 대문자 값",
  "notes": "업데이트 설명"
}
```

`OWNER`와 `REPOSITORY`는 실제 GitHub 계정과 저장소 이름으로 바꾼다. 임의 값으로 APK에 내장하지 않는다.

## 3. 업데이트 주소

최신 manifest 주소는 앱 소스에 고정되어 있어 차량에서 주소를 입력할 필요가 없다.

```text
https://github.com/jeonbj005-tech/p4-files/releases/latest/download/update.json
```

차량에서는 P4 Files의 `앱 업데이트`를 눌러 최신 버전을 확인한다.

## 4. 업데이트 동작

1. HTTPS로 `update.json`을 읽는다.
2. 설치된 `versionCode`와 새 버전을 비교한다.
3. 새 버전이면 APK를 앱 다운로드 폴더에 저장한다.
4. 다운로드한 APK의 SHA-256을 검증한다.
5. APK를 공용 `Download`에 저장하고 크기·SHA-256을 검증한다.
6. 차량 순정 다운로드/파일 앱을 연다. 거기서 APK를 선택해 설치한다.

P4 Files가 직접 설치를 시작하지 않는 것은 차량에서 앱 출처 설치가 제한될 수 있기 때문이다. 선택 저장 위치로 저장한 경우에도 차량 순정 파일 앱에서 APK를 연다. SHA-256이 다르면 완료 안내를 표시하지 않는다.
