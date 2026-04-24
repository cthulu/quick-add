# Keep Quick Add

A floating widget Android app for quickly adding items to Google Keep lists.

## Features

- **Floating Widget**: Draw-over-apps overlay that works from anywhere on your device
- **List Selection**: Dropdown to select which Keep list to add items to
- **Quick Input**: Text field for entering items quickly
- **Toast Notifications**: Visual feedback when items are added
- **Draggable Widget**: Move the widget anywhere on screen

## Architecture

- `MainActivity`: Entry point that handles permission requests and service control
- `FloatingWidgetService`: Foreground service that manages the floating overlay widget
- Uses `WindowManager` with `TYPE_APPLICATION_OVERLAY` for the floating widget

## Permissions Required

- `SYSTEM_ALERT_WINDOW`: Required to draw over other apps
- `FOREGROUND_SERVICE`: Required to keep the widget running
- `POST_NOTIFICATIONS`: Required for the foreground service notification

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on an emulator or device (API 26+)

## Usage

1. Launch the app
2. Grant "Display over other apps" permission when prompted
3. Tap "Start Floating Widget"
4. The widget will appear as a floating card
5. Select a list from the dropdown
6. Type your item and tap "Add"
7. A toast notification will confirm the addition

## TODO

- [ ] Integrate with Google Keep API for real list sync
- [ ] Add persistent storage for offline items
- [ ] Custom theming options
- [ ] Quick toggle tile in notification shade
