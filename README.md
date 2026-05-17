# Bifrost

**Bifrost** is an automation tool designed specifically for the **AYN Thor** dual-screen Android handheld. Its primary goal is to automate drawing within games like *Tomodachi Life: Living the Dream* (running in emulators like Eden Nightly) on the top screen.

## Current Project Status: Real-Device MVP
This project is currently in the **real-device MVP** phase. Testing on the AYN Thor confirms:
- The app launches successfully on the AYN Thor.
- The floating overlay works over top-screen apps.
- Accessibility gesture dispatch works.
- ibisPaint X receives Bifrost gestures as real drawing input.
- *Tomodachi Life: Living the Dream* receives Bifrost gestures as real drawing input.
- The visual canvas selector can define a visible drawing area on the top screen.
- Calibrated test shapes can be drawn inside the selected game canvas area.

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
- **CanvasSelectorOverlayService**: A visual top-screen selector for choosing the active drawing area.

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

Tested on an AYN Thor with *Tomodachi Life: Living the Dream* running in Eden Nightly:
- Bifrost can draw directly into the game.
- The floating overlay works over the game.
- The selector overlay works over the game.
- The selected drawing area is visible on the top screen.
- Calibrated shapes can be drawn inside the selected game canvas area.

## Milestone: Drawing in Tomodachi Life on real hardware
Bifrost has reached its first target-app milestone: it can draw into *Tomodachi Life: Living the Dream* on the AYN Thor using Android AccessibilityService gesture injection and floating overlays. This confirms the core approach works on real hardware in the actual target environment.

## Hardware Context (AYN Thor)
- **Dual Screens**: Android sees two physical displays.
- **Accessibility Gestures**: Allows drawing on the "active" screen.
- **Overlays**: Supported via `SYSTEM_ALERT_WINDOW`.

## How to use
1. **Accessibility**: Open Accessibility Settings from the app and enable "Bifrost".
2. **Overlay**: Grant "Display over other apps" permission.
3. **Calibrate**: Set canvas bounds in the Calibration tab or use Canvas Selector Mode.
4. **Draw**: Use the Test Shapes tab or floating overlay shortcuts to trigger test patterns.

## Known Issues / Future Improvements
- Selector drag behavior may need more real-device tuning for comfort and precision.
- Selector control usability can still be improved for the Thor top-screen space.
- Image import and image tracing/stroke conversion are not implemented yet.

## Next Steps
- Continue Tomodachi Life canvas tuning with the visual selector.
- Add an adjustable canvas selector workflow with more precise controls.
- Add basic image import and stroke conversion.

## Disclaimer
This project is for educational and automation research purposes. It does not include any copyrighted ROMs, emulator files, or keys.
