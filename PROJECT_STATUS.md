# P4 Files 상태

2026-11-01 · v1.39 / versionCode 49

- DVR은 최신순 페이지에서 선택 월까지만 조회하고, 완전 확인된 월은 캐시에 기록해 다음 달력 이동 때 재조회하지 않는다. 상단 새로고침은 현재 선택 월만 다시 읽는다.
- 변경: `DvrMonthScanner`, 월 단위 스냅샷 병합, 완료 월 캐시. 기타 달 기록은 보존한다.
- 산출물: `releases/P4-Files-v1.39.apk`, SHA-256 `A4E290543C9F4BCFF147508B97EAB8932D7ADE89BDA12E2261692C437B8FBBA3`.
- 배포: https://github.com/jeonbj005-tech/p4-files/releases/tag/v1.39
- 확인: `lintDebug`, `assembleDebug`, 테스트 소스 컴파일 성공, JUnit 4 직접 실행 10개 통과. Gradle 테스트 작업은 worker `ClassNotFoundException`으로 실패.
- 실차에서 DVR 정렬 순서, 조회 시간, 캐시 재사용 동작은 미확인.
- 다음: 차량에서 11월 조회 요청 수와 10월 재방문 요청 수를 확인하고, 순정 DVR 녹화에 영향이 없는지 확인.
