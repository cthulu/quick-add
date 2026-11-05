# Project Overview
Quick Add is an Android app that:
- allows creation of new entries in Google Keep lists using a simple floating window
- allows creation of new calendar entries in Google Calendar using a simple floating window

# Build Commands
- `./gradlew assembleDebug` - Build the debug version of the app.
- `./gradlew testDebugUnitTest` - Run unit tests.

# Code Style Guidelines
- Use camelCase for variable names.
- Follow the Android coding conventions.
- Keep the methods concise
- Focus on single responsibility of a method and class
- Avoid adding unnecessary comments
- Make sure you optimize the imports

# Static shortcuts
The launcher shortcuts are defined in four `res/xml/shortcuts.xml` files that must be kept in sync:
- `app/src/main/res/xml/shortcuts.xml` — standard release
- `app/src/personal/res/xml/shortcuts.xml` — personal release
- `app/src/standardDebug/res/xml/shortcuts.xml` — standard debug
- `app/src/personalDebug/res/xml/shortcuts.xml` — personal debug

The debug variants exist because the debug build type adds `applicationIdSuffix = ".debug"`, and the `android:targetPackage` in shortcut XML is a hardcoded literal that cannot reference the applicationId dynamically. So the two debug files use `nl.freshlytyped.keepquickadd.debug` while the release files use `nl.freshlytyped.keepquickadd`. If you change the shortcut content, update all four files.

# Feature work
- Always create a new branch for new features
- Keep the commit message clean without mentioning the files
- Focus on what the actual commit added/removed
- Depending on the type of work, prefix commit with: 'feat' (for feature), 'fix' (for fixes), 'other' for other type of work

# Error handling and logging
- Handle and test error handling in case of connection errors
- Use standard logging mechanisms to log errors
- Avoid logging sensitive data

# Testing rules
- Create unit tests where possible
