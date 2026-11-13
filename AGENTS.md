# P4 Files development rules

- Before every vehicle UI change, check `docs/display/polestar4-display-profile.md` and confirm the connected AVD's physical display, ADB overrides, density, orientation, current `WindowMetrics`, insets, and app content bounds.
- Treat 1920×1200, 16:10, 15.4 inches as the task's Polestar 4 reference. A temporary AVD density is not a measured vehicle density. Never infer the vehicle's usable app area from its panel resolution.
- Do not mark vehicle UI complete from a phone-only emulator. Use the dedicated AAOS AVD and record the real vehicle values as unverified until measured on the car.
- Keep display size changes responsive to `WindowMetrics`; do not add phone-sized fixed widths or scale every control in proportion to the display.
- Preserve existing features and permissions when changing layout. Review source and merged APK declarations against the 16-item PolDock v1.3.17 baseline in `docs/display/polestar4-display-profile.md`; keep those declarations separate from AVD/runtime grants and real-vehicle approval.
- Display diagnostics use public APIs and require no added manifest permission. Do not treat a permission string, emulator grant, or declared Car API permission as evidence of approval on the vehicle.
- Keep the existing AVDs. Create separate named profiles for new display baselines, and restore any ADB size/density override with `tools/Set-P4DisplayProfile.ps1`.
- Record screenshots, app-window measurements, compatibility evidence, checks, and unverified vehicle behavior in the JSON and Markdown display report.
