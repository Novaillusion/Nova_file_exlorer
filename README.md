# File Explorer (Android)

A file manager for Android written in Kotlin + Jetpack Compose (Material 3).

## Features
- Browse internal storage, with tappable breadcrumbs and the system back button to go up
- Open files in other apps (through a `FileProvider`) and share one or more files
- Create folders, rename, and delete (folders are deleted with their contents, after a confirmation)
- Multi-select with a long press, then copy, cut, and paste into any folder (name clashes become `name (1).ext`)
- Sort by name, date or size, with folders always listed first
- Show or hide hidden files, and search the current folder
- Material You dynamic colour on Android 12+, plus dark mode
- Remembers the current folder if Android kills the app in the background

## Requirements
- Android Studio (Ladybug or newer), JDK 17
- minSdk 24 (Android 7.0), target/compile SDK 35

## Running it
1. Open the `FileExplorer` folder in Android Studio (**File → Open**).
2. Let Gradle sync. If Android Studio offers to create or upgrade the Gradle wrapper or AGP, accept.
3. Run on a device or emulator, tap **Grant access**, and turn on **Allow access to manage all files**.

## Project layout
```
app/src/main/java/com/example/fileexplorer/
├── MainActivity.kt            entry point and permission gate
├── StoragePermission.kt       permission checks and the "grant access" screen
├── FileOperations.kt          FileItem model, sorting, and file operations (list, create, rename, delete, copy, move)
├── FileExplorerViewModel.kt   UI state, selection, clipboard (StateFlow)
├── FileExplorerScreen.kt      Compose UI: top bars, breadcrumbs, list, dialogs
├── FileIntents.kt             open and share through FileProvider
└── Theme.kt
```

## Notes on permissions
- **Android 11+** uses `MANAGE_EXTERNAL_STORAGE` ("All files access"). This is the only way to get a real
  file manager on modern Android. Google Play only allows it for apps whose core purpose is file management,
  and you have to declare it in the Play Console.
- **Android 7–10** uses the classic `READ/WRITE_EXTERNAL_STORAGE` runtime permissions.
- Even with All files access, Android blocks `Android/data` and `Android/obb` on 11+. Those folders appear empty.

## Ideas for next steps
- Recursive search across subfolders
- Thumbnails for images and videos (for example with Coil)
- Showing SD cards and USB drives (`StorageManager.storageVolumes`)
- Progress and cancel for large copies (move the work to `WorkManager`)
- Zip and unzip
