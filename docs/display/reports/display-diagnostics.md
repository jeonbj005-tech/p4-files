# P4 Files 디스플레이 진단

- 캡처: 2026-11-13T05:25:40+00:00
- 기기: `emulator-5558` · 앱 `1.57`
- 스크린샷: `1920×1200` · `C:\Users\bjjeon\Documents\ChatGPT\폴스타4 대쉬캠\p4-drive-file-manager\docs\display\reports\display-current.png`
- 기준 이미지 비교: 평균 절대 차이 0.09 / 255 · `C:\Users\bjjeon\Documents\ChatGPT\폴스타4 대쉬캠\p4-drive-file-manager\docs\display\reports\display-comparison.png`
- 디스플레이: 물리 `1920x1200`, 크기 오버라이드 `없음`; ADB 기본 밀도 `160 dpi`, 밀도 오버라이드 `없음`, 앱 논리 밀도 `160 dpi`
- 앱 WindowMetrics: `{"package": "com.polestar4.drivefilemanager", "activity": "MainActivity", "apiLevel": 33, "windowBoundsPx": {"left": 0, "top": 0, "right": 1920, "bottom": 1200}, "systemBarsInsetsPx": {"left": 0, "top": 76, "right": 0, "bottom": 96}, "contentVisible": true, "contentBoundsPx": {"left": 0, "top": 76, "right": 1920, "bottom": 1104}, "contentWidthPx": 1920, "contentHeightPx": 1028, "bodyScrollRangePx": 828, "bodyScrollExtentPx": 824, "bodyScrollYpx": 0, "densityDpi": 160, "density": 1, "configWidthDp": 1920, "windowWidthDp": 1920, "smallestWidthDp": 1200, "orientation": 2, "automotiveFeature": true, "vehicleLayout": true, "wideLayout": true, "multiWindow": false, "supportsLargeScreens": true, "resizeableForScreens": true, "compatibleWidthLimitDp": 0, "largestWidthLimitDp": 0, "compatibilityWidthLimitExceeded": false, "targetSdk": 35, "displayId": 0}`
- 차량/와이드 레이아웃 선택: `True` / `True`
- 호환성 제약: 호환 폭 `0` dp, 초과 `False`, 대형 화면 지원 `True`, 화면 크기 조절 `True`
- 런타임 호환성 확대 상태: `unavailable from public app API`. 앱 공개 API는 선언된 제약과 결과 WindowMetrics를 제공하며 프레임워크 내부 확대 상태는 직접 제공하지 않습니다.
- 잘린 접근성 노드: `0` · 세로 콘텐츠 넘침: `False` · 스크롤 필요 시 뷰포트 `824.0dp` (48dp 이상인지 확인)
- 실차 DPI, 앱 창·인셋, 정확한 호환성 확대 상태, 권한 승인: **미검증**

## 자동 검사

- PASS: physicalResolutionRead
- PASS: physicalResolutionMatches1920x1200Reference
- PASS: wmDensityRead
- PASS: appLogicalDensityRead
- PASS: screenshotMatchesEffectiveDisplay
- PASS: appWindowMetricsRead
- PASS: contentAreaMeasured
- PASS: vehicleLayoutSelected
- PASS: compatibilityConstraintsMeasured
- PASS: noVisibleNodeClipping
- PASS: verticalScrollViewportAtLeast48dpWhenNeeded

## Evidence

- 스크린샷 접근성 트리: `C:\Users\bjjeon\Documents\ChatGPT\폴스타4 대쉬캠\p4-drive-file-manager\docs\display\reports\display-current.xml`
- 스크린샷 PNG: `C:\Users\bjjeon\Documents\ChatGPT\폴스타4 대쉬캠\p4-drive-file-manager\docs\display\reports\display-current.png`
- ADB 디스플레이/윈도우 덤프: 수집됨
