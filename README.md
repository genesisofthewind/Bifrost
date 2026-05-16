# Bifrost

**Bifrost** is an automation tool designed specifically for the **AYN Thor** dual-screen Android handheld. Its primary goal is to automate drawing within games like *Tomodachi Life: Living the Dream* (running in emulators like Eden Nightly) on the top screen.

## Current Project Status: Real-Device MVP
This project is currently in the **real-device MVP** phase. Testing on the AYN Thor confirms:
- The app launches successfully on the AYN Thor.
- The floating overlay works over top-screen apps.
- Accessibility gesture dispatch works.
- ibisPaint X receives Bifrost gestures as real drawing input.

The app establishes the architectural foundation for:
- **Accessibility Service Integration**: For programmatic gesture injection (drawing).
- **Floating Overlay Control**: To provide a persistent UI over the top screen while the game is running.
- **Gesture Engine**: Basic conversion logic for shapes into Android `GestureDescription` strokes.
- **Calibration System**: Initial SharedPreferences-based store for screen coordinate mapping.

## Features implemented
- **MainActivity**: A Jetpack Compose UI to coordinate permissions and settings.
- **DrawAccessibilityService**: A custom accessibility service with `canPerformGestures` enabled.
- **FloatingOverlayService**: A system-level overlay bubble that remains interactive over other apps.
- **DrawEngine**: Converts `ShapeCommands` into calibrated stroke sequences.
- **CalibrationStore**: Persistent storage for canvas boundaries.

## Real-device test status
Tested on an AYN Thor with ibisPaint X:
- App launch: working.
- Overlay permission and floating overlay: working.
- Accessibility gesture dispatch: working.
- Test Gesture: working.
- Test Line: working.
- Square: working reliably.
- TopRight to BottomLeft diagonal: working.
- X Shape: working.
- Segmented TopLeft to BottomRight: known issue.

## Hardware Context (AYN Thor)
- **Dual Screens**: Android sees two physical displays.
- **Accessibility Gestures**: Allows drawing on the "active" screen.
- **Overlays**: Supported via `SYSTEM_ALERT_WINDOW`.

## How to use
1. **Accessibility**: Open Accessibility Settings from the app and enable "Bifrost".
2. **Overlay**: Grant "Display over other apps" permission.
3. **Calibrate**: Set canvas bounds in the Calibration tab.
4. **Draw**: Use the Test Shapes tab or floating overlay shortcuts to trigger test patterns.

## Next Steps
- Test against *Tomodachi Life: Living the Dream* running in Eden Nightly.
- Add an adjustable canvas selector.
- Add basic image import and stroke conversion.

## Disclaimer
This project is for educational and automation research purposes. It does not include any copyrighted ROMs, emulator files, or keys.
