# Bifrost

**Bifrost** is an Android companion tool for tracing and importing images into games or apps by converting images into touch/stroke input. Its current real-world test target is *Tomodachi Life: Living the Dream* drawing mode running on the AYN Thor dual-screen Android handheld.

## Current Status

Bifrost is in a real-device MVP phase. Testing on the AYN Thor confirms:

- The app launches and runs on real hardware.
- The visual canvas selector can define the target drawing area.
- Android AccessibilityService gesture dispatch draws real strokes into apps.
- Bifrost can draw directly into *Tomodachi Life: Living the Dream*.
- A simple Kirby image has been traced and drawn into Tomodachi Life, with manual outline cleanup and bucket coloring afterward.
- More detailed character art, including Lucario-style images, can preserve internal line detail with the detailed character presets.

## Build

From the project root:

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Current Features

- Compact Jetpack Compose control UI for the Thor bottom screen.
- Visual canvas selector overlay for setting drawing bounds.
- Manual calibration and test-shape drawing.
- Image import with black/white threshold preview.
- Stroke generation from processed images using selectable trace modes.
- Sequential AccessibilityService gesture dispatch with cancel support.

## Trace Presets

Current presets:

- **Tomodachi Simple Cartoon**: best for Kirby, simple cartoons, icons, and bucket-fill-friendly outlines.
- **Clean Cartoon PNG**: best for transparent cartoon images with alpha/background cleanup.
- **Tomodachi Detailed Character**: best for Pokemon, anime/game characters, and interior markings.
- **Soft / Light Character**: best for pale characters, soft anime/game art, and Gardevoir-like low-contrast images.
- **Fast Sketch**: quick rough trace with fewer strokes.
- **Balanced**: general cartoon fallback.
- **Dense Detail**: slower, more complete or sketchy output.
- **Outline Only**: edge-focused trace for bold line art.
- **Custom**: active when manual settings are changed.

## Known Issues

- Some outlines are not fully connected, so bucket fill still requires manual cleanup.
- Color switching is not implemented yet.

## Near-Term Roadmap

- Add Bucket Fill Safe / Watertight Cartoon tracing.
- Add stronger gap closing and outline cleanup.
- Add palette profile groundwork for future automatic color selection.

## Disclaimer

This project is for educational and automation research purposes. It does not include any copyrighted ROMs, emulator files, or keys.
