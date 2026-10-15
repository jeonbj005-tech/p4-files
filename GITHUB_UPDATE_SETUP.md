# GitHub Releases 업데이트 설정

P4 Files v1.25부터 Google Drive 권한과 무관한 HTTPS 업데이트를 지원한다.

## 1. GitHub 저장소 준비

이 프로젝트를 GitHub 저장소에 올린다. 저장소는 APK를 차량에서 내려받을 수 있도록 공개 저장소를 권장한다. 비공개 저장소의 Release 다운로드에는 인증 토큰이 필요하므로 현재 차량 업데이트 기능과 맞지 않는다.

## 2. Release 파일

각 Release에는 이름이 고정된 다음 두 파일을 첨부한다.

- `P4-Files.apk`
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

## 3. 차량에서 최초 설정

1. P4 Files의 대시캠 화면에서 `앱 업데이트`를 누른다.
2. 다음 형식의 주소를 입력한다.

```text
https://github.com/OWNER/REPOSITORY/releases/latest/download/update.json
```

3. 이후에는 `앱 업데이트`만 누르면 최신 버전을 확인한다.

## 4. 업데이트 동작

1. HTTPS로 `update.json`을 읽는다.
2. 설치된 `versionCode`와 새 버전을 비교한다.
3. 새 버전이면 APK를 앱 다운로드 폴더에 저장한다.
4. 다운로드한 APK의 SHA-256을 검증한다.
5. 설치 권한을 확인한다.
6. Android 패키지 설치 화면을 연다.

SHA-256이 다르면 파일을 삭제하고 설치하지 않는다.

## 5. 빌드 시 주소 고정(선택)

프로젝트 루트에 커밋하지 않는 `update.properties`를 만들 수 있다.

```properties
manifestUrl=https://github.com/OWNER/REPOSITORY/releases/latest/download/update.json
```

이 값이 없으면 차량 앱에서 최초 한 번 직접 입력한다.
