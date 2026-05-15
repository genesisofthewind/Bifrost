# ThorDrawBridge

**ThorDrawBridge** is an automation tool designed specifically for the **AYN Thor** dual-screen Android handheld. Its primary goal is to automate drawing within games like *Tomodachi Life: Living the Dream* (running in emulators like Eden Nightly) on the top screen.

## Current Project Status: MVP Skeleton
This project is currently in the **MVP Skeleton** phase. It establishes the architectural foundation for:
- **Accessibility Service Integration**: For programmatic gesture injection (drawing).
- **Floating Overlay Control**: To provide a persistent UI over the top screen while the game is running.
- **Gesture Engine**: Basic conversion logic for shapes into Android `GestureDescription` strokes.
- **Calibration System**: Initial SharedPreferences-based store for screen coordinate mapping.

## Features implemented in this MVP
- **MainActivity**: A Jetpack Compose UI to coordinate permissions and settings.
- **DrawAccessibilityService**: A custom accessibility service with `canPerformGestures` enabled.
- **FloatingOverlayService**: A system-level overlay bubble that remains interactive over other apps.
- **DrawEngine**: Converts `ShapeCommands` (Line, Square) into paths relative to calibrated coordinates.
- **CalibrationStore**: Persistent storage for canvas boundaries.

## Hardware Context (AYN Thor)
- **Dual Screens**: Android sees two physical displays.
- **Accessibility Gestures**: Allows drawing on the "active" screen.
- **Overlays**: Supported via `SYSTEM_ALERT_WINDOW`.

## How to use
1. **Accessibility**: Open Accessibility Settings from the app and enable "ThorDrawBridge".
2. **Overlay**: Grant "Display over other apps" permission.
3. **Calibrate**: Press "Calibrate" (currently mocks TL/BR coordinates) to define the target drawing area.
4. **Draw**: Use the on-screen buttons or the floating bubble to trigger test patterns.

## Next Steps
- Implement real calibration UI for selecting the top screen corners.
- Add an Image Processor to convert PNG/bitmap data into efficient path stroke sequences.
- Integrate a simple HTTP or WebSocket server to receive drawing commands from external scripts.
- Optimize gesture timing for Eden emulator input lag.

## Disclaimer
This project is for educational and automation research purposes. It does not include any copyrighted ROMs, emulator files, or keys.
