#!/usr/bin/env python3
"""Capture and check a P4 Files display profile using Android's public diagnostics."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

PACKAGE = "com.polestar4.drivefilemanager"
ROOT = Path(__file__).resolve().parents[1]
ADB = shutil.which("adb") or str(Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", ""))) / "platform-tools/adb.exe")


def wm_value(text: str, label: str) -> str | None:
    match = re.search(rf"(?m)^{re.escape(label)}:\s*(\S+)", text)
    return match.group(1) if match else None


def latest_metrics(text: str) -> dict | None:
    for line in reversed(text.splitlines()):
        match = re.search(r"P4DisplayMetrics:\s*(\{.*\})\s*$", line)
        if match:
            try:
                return json.loads(match.group(1))
            except json.JSONDecodeError:
                continue
    return None


def screenshot_size(data: bytes) -> tuple[int, int]:
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("ADB did not return a PNG screenshot")
    return struct.unpack(">II", data[16:24])


def has_vertical_overflow(content_px: int | None, viewport_px: int | None, density: float) -> bool:
    return content_px is not None and viewport_px is not None and content_px > viewport_px + max(2, int(density * 4))


def visible_clipping(xml: str, width: int, height: int) -> list[dict]:
    root = ET.fromstring(xml)
    clipped = []
    for node in root.iter("node"):
        if node.attrib.get("visible-to-user") != "true":
            continue
        match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.attrib.get("bounds", ""))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        if left < 0 or top < 0 or right > width or bottom > height:
            clipped.append({"text": node.attrib.get("text", ""), "class": node.attrib.get("class", ""), "bounds": node.attrib["bounds"]})
    return clipped


class Device:
    def __init__(self, serial: str):
        if not Path(ADB).is_file():
            raise FileNotFoundError(f"adb was not found on PATH or under ANDROID_HOME: {ADB}")
        self.adb = [ADB, "-s", serial]

    def run(self, *args: str, binary: bool = False, timeout: int = 30) -> str | bytes:
        result = subprocess.run(self.adb + list(args), capture_output=True, timeout=timeout, check=False)
        if result.returncode:
            raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace')}")
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

    def shell(self, *args: str, timeout: int = 30) -> str:
        return self.run("shell", *args, timeout=timeout)  # type: ignore[return-value]

    def hierarchy(self) -> str:
        self.shell("uiautomator", "dump", "/sdcard/p4-display-window.xml", timeout=40)
        return self.shell("cat", "/sdcard/p4-display-window.xml")

    def screenshot(self) -> bytes:
        return self.run("exec-out", "screencap", "-p", binary=True)  # type: ignore[return-value]


def dismiss_known_avd_error(device: Device, xml: str) -> bool:
    if "198.18.37.20" not in xml:
        return False
    root = ET.fromstring(xml)
    for node in root.iter("node"):
        if node.attrib.get("class") == "android.widget.Button" and node.attrib.get("text") == "확인":
            match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                device.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1)
                return True
    return False


def image_comparison(before: Path, after: Path, output: Path) -> dict:
    try:
        from PIL import Image, ImageChops, ImageStat
    except ImportError:
        return {"available": False, "reason": "Pillow is not installed"}
    first, second = Image.open(before).convert("RGB"), Image.open(after).convert("RGB")
    if first.size != second.size:
        return {"available": True, "sameDimensions": False, "before": first.size, "after": second.size}
    diff = ImageChops.difference(first, second)
    mean = sum(ImageStat.Stat(diff).mean) / 3
    Image.blend(first, second, 0.5).save(output)
    return {"available": True, "sameDimensions": True, "meanAbsoluteDifference": round(mean, 3), "comparison": str(output)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--out-dir", type=Path, default=ROOT / "docs/display/reports")
    parser.add_argument("--wait-seconds", type=int, default=12)
    args = parser.parse_args()
    if args.baseline and not args.baseline.is_file():
        parser.error(f"baseline screenshot does not exist: {args.baseline}")
    device = Device(args.serial)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    device.run("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(max(0, args.wait_seconds))
    hierarchy = device.hierarchy()
    dismissed_error = dismiss_known_avd_error(device, hierarchy)
    if dismissed_error:
        hierarchy = device.hierarchy()
    screenshot = device.screenshot()
    image = args.out_dir / "display-current.png"
    image.write_bytes(screenshot)
    width, height = screenshot_size(screenshot)
    (args.out_dir / "display-current.xml").write_text(hierarchy, encoding="utf-8")

    size_text = device.shell("wm", "size")
    density_text = device.shell("wm", "density")
    physical_size = wm_value(size_text, "Physical size")
    size_override = wm_value(size_text, "Override size")
    effective_size = size_override or physical_size
    effective_wh = tuple(map(int, effective_size.split("x"))) if effective_size else None
    display_dump = device.shell("dumpsys", "display")
    window_dump = device.shell("dumpsys", "window")
    package_dump = device.shell("dumpsys", "package", PACKAGE)
    log = device.run("logcat", "-d", "-s", "P4DisplayMetrics:I")
    metrics = latest_metrics(log)  # type: ignore[arg-type]
    density_default = wm_value(density_text, "Physical density")
    density_override = wm_value(density_text, "Override density")
    clipping = visible_clipping(hierarchy, width, height)
    reported_area = metrics.get("contentBoundsPx", {}) if metrics else {}
    body_range = metrics.get("bodyScrollRangePx") if metrics else None
    body_extent = metrics.get("bodyScrollExtentPx") if metrics else None
    overflow = has_vertical_overflow(body_range, body_extent, metrics.get("density", 1) if metrics else 1)
    scroll_viewport_dp = round(body_extent / metrics["density"], 1) if body_extent is not None and metrics and metrics.get("density", 0) > 0 else None
    vehicle_layout = metrics.get("vehicleLayout") if metrics else None
    app_version = re.search(r"(?m)^\s*versionName=([^\s]+)", package_dump)
    compatibility_signals = re.findall(r"(?im)^.*(?:mGlobalScale|mCompatScale|compatibility mode).*?$", window_dump)
    compat_limit = metrics.get("compatibleWidthLimitDp") if metrics else None

    report = {
        "capturedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "serial": args.serial,
        "package": PACKAGE,
        "appVersion": app_version.group(1) if app_version else None,
        "reference": {"panelResolutionPx": "1920x1200", "aspectRatio": "16:10", "diagonalInches": 15.4, "realVehicleDpi": "unverified", "realVehicleAppWindow": "unverified"},
        "display": {
            "physicalSizePx": physical_size, "sizeOverridePx": size_override,
            "effectiveSizePx": effective_size,
            "wmBaseDensityDpi": density_default, "wmDensityOverrideDpi": density_override,
            "effectiveWmDensityDpi": density_override or density_default,
            "appLogicalDensityDpi": metrics.get("densityDpi") if metrics else None,
            "orientation": metrics.get("orientation") if metrics else None,
        },
        "screenshot": {"path": str(image), "widthPx": width, "heightPx": height},
        "appWindowMetrics": metrics,
        "systemDisplayDumpAvailable": bool(display_dump), "windowDumpAvailable": bool(window_dump),
        "compatibility": {
            "supportsLargeScreens": metrics.get("supportsLargeScreens") if metrics else None,
            "resizeableForScreens": metrics.get("resizeableForScreens") if metrics else None,
            "compatibleWidthLimitDp": compat_limit,
            "largestWidthLimitDp": metrics.get("largestWidthLimitDp") if metrics else None,
            "widthLimitExceeded": metrics.get("compatibilityWidthLimitExceeded") if metrics else None,
            "systemDumpSignals": compatibility_signals,
            "exactRuntimeScalingState": "unavailable from public app API" if not compatibility_signals else "system dump contains compatibility scaling signal",
        },
        "accessibility": {"visibleNodeCount": len(list(ET.fromstring(hierarchy).iter("node"))), "clippedVisibleNodes": clipping},
        "layout": {"automotiveFeature": metrics.get("automotiveFeature") if metrics else None, "vehicleLayout": vehicle_layout, "wideLayout": metrics.get("wideLayout") if metrics else None, "bodyScrollOverflow": overflow, "bodyScrollRangePx": body_range, "bodyScrollExtentPx": body_extent, "bodyScrollViewportDp": scroll_viewport_dp},
        "knownAvdDvrErrorDismissed": dismissed_error,
        "baselineComparison": image_comparison(args.baseline, image, args.out_dir / "display-comparison.png") if args.baseline and args.baseline.is_file() else None,
        "checks": {
            "physicalResolutionRead": bool(wm_value(size_text, "Physical size")),
            "physicalResolutionMatches1920x1200Reference": physical_size == "1920x1200",
            "wmDensityRead": bool(density_default),
            "appLogicalDensityRead": bool(metrics and metrics.get("densityDpi", 0) > 0),
            "screenshotMatchesEffectiveDisplay": effective_wh in ((width, height), (height, width)),
            "appWindowMetricsRead": metrics is not None,
            "contentAreaMeasured": bool(reported_area) and metrics.get("contentWidthPx", 0) > 0,
            "vehicleLayoutSelected": vehicle_layout is True,
            "compatibilityConstraintsMeasured": metrics is not None and compat_limit is not None and "resizeableForScreens" in metrics,
            "noVisibleNodeClipping": not clipping,
            "verticalScrollViewportAtLeast48dpWhenNeeded": not overflow or (scroll_viewport_dp is not None and scroll_viewport_dp >= 48),
        },
    }
    json_path = args.out_dir / "display-diagnostics.json"
    md_path = args.out_dir / "display-diagnostics.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    checks = "\n".join(f"- {'PASS' if value else 'FAIL'}: {key}" for key, value in report["checks"].items())
    comparison = report["baselineComparison"]
    comparison_summary = f"평균 절대 차이 {comparison['meanAbsoluteDifference']} / 255 · `{comparison['comparison']}`" if comparison and comparison.get("sameDimensions") else "기준 이미지 비교를 실행하지 않았거나 이미지 크기가 다릅니다."
    md = f"""# P4 Files 디스플레이 진단

- 캡처: {report['capturedAt']}
- 기기: `{args.serial}` · 앱 `{report['appVersion'] or '확인 불가'}`
- 스크린샷: `{width}×{height}` · `{image}`
- 기준 이미지 비교: {comparison_summary}
- 디스플레이: 물리 `{physical_size}`, 크기 오버라이드 `{size_override or '없음'}`; ADB 기본 밀도 `{density_default} dpi`, 밀도 오버라이드 `{density_override or '없음'}`, 앱 논리 밀도 `{report['display']['appLogicalDensityDpi']} dpi`
- 앱 WindowMetrics: `{json.dumps(metrics, ensure_ascii=False) if metrics else 'logcat에서 찾지 못함'}`
- 차량/와이드 레이아웃 선택: `{report['layout']['vehicleLayout']}` / `{report['layout']['wideLayout']}`
- 호환성 제약: 호환 폭 `{compat_limit}` dp, 초과 `{report['compatibility']['widthLimitExceeded']}`, 대형 화면 지원 `{report['compatibility']['supportsLargeScreens']}`, 화면 크기 조절 `{report['compatibility']['resizeableForScreens']}`
- 런타임 호환성 확대 상태: `{report['compatibility']['exactRuntimeScalingState']}`. 앱 공개 API는 선언된 제약과 결과 WindowMetrics를 제공하며 프레임워크 내부 확대 상태는 직접 제공하지 않습니다.
- 잘린 접근성 노드: `{len(clipping)}` · 세로 콘텐츠 넘침: `{overflow}` · 스크롤 필요 시 뷰포트 `{scroll_viewport_dp}dp` (48dp 이상인지 확인)
- 실차 DPI, 앱 창·인셋, 정확한 호환성 확대 상태, 권한 승인: **미검증**

## 자동 검사

{checks}

## Evidence

- 스크린샷 접근성 트리: `{args.out_dir / 'display-current.xml'}`
- 스크린샷 PNG: `{image}`
- ADB 디스플레이/윈도우 덤프: {'수집됨' if display_dump and window_dump else '수집 불가'}
"""
    md_path.write_text(md, encoding="utf-8")
    print(f"JSON: {json_path}\nMarkdown: {md_path}\nScreenshot: {image}")
    return 0 if all(report["checks"].values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
