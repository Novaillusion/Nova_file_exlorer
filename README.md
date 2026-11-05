# File Explorer (Android)

A file manager for Android written in Kotlin + Jetpack Compose (Material 3).

## Features
The interface is modelled on **Windows 11 File Explorer**:

- **Address bar**: back, forward, up and refresh buttons, plus clickable breadcrumbs
  (`This phone > Internal storage > DCIM >`). Tap the empty part of the bar to type a path.
- **Search box**: searches the current folder and everything inside it. Results come in as they're found,
  each with its location.
- **Command bar**: New ▾ (folder or text document), Cut, Copy, Paste, Rename, Share, Delete, Sort ▾, View ▾,
  and a ⋯ menu (Select all / none / invert, Pin to Quick access, Copy as path, Properties).
- **Navigation pane**: Home, Quick access folders (Downloads, Documents, Pictures, Music, Videos, Camera, plus
  your own pins), and *This phone* with every storage volume. It stays open on tablets and slides out on phones.
- **Home page**: *Quick access* tiles and *Devices and drives*, with Explorer's capacity bars ("X GB free of Y GB").
  The bars turn red when a drive is over 90% full.
- **Details view**: Name / Date modified / Type / Size columns. Tap a column header to sort, tap again to reverse.
- **Large icons view**: a grid with real thumbnails for images and videos.
- **Status bar**: item and selection counts, plus Details and Large icons toggles, like Explorer.
- **Properties dialog**: type, location, size (worked out for folders), contents, modified date, attributes.
- Dark and light Fluent-style themes that follow the system setting.

Long-press an item to start selecting (check boxes appear), then tap more items to add them.

## Using it as your main file manager
Android has no "default file manager" setting. Instead, the app answers the standard requests other apps send:

| When another app… | Android action | What happens |
|---|---|---|
| asks you to choose a file (attach, upload, import) | `GET_CONTENT`, `PICK` | A Windows-style **Open** dialog appears. Files that don't match the requested type are dimmed. The chosen files go back to the app with temporary read access. Multi-select works when the app allows it. |
| wants to show a folder ("Open folder") | `VIEW` with a directory type | Opens straight to that folder. |
| launches "the Files app" (Settings ▸ Storage, some launchers) | `MAIN` + `APP_FILES` | Opens the explorer. |

The first time, Android asks which app to use. Pick **File Explorer** and tap **Always**.
To undo that later: **Settings ▸ Apps ▸ File Explorer ▸ Open by default ▸ Clear defaults**.

**Limitation:** many newer apps use `OPEN_DOCUMENT` or the photo picker. Android always routes those to its own system
picker, and no app can take them over. In the system picker, File Explorer is still listed in the ☰ menu
whenever the requesting app used `GET_CONTENT`.

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
├── MainActivity.kt         entry point and permission gate
├── StoragePermission.kt    permission checks and the "grant access" screen
├── FileOperations.kt       FileItem model, sorting, and file operations (list, create, rename, delete, copy, move)
├── StorageData.kt          storage volumes and Quick access pins
├── ExplorerViewModel.kt    state, back/forward history, search, selection, clipboard
├── ExplorerScreen.kt       window layout (pane + main area) and dialog routing
├── ExplorerChrome.kt       address bar, search box, command bar, status bar, navigation pane
├── ExplorerContent.kt      Home page, Details view, Large icons view
├── ExplorerDialogs.kt      New / Rename / Delete / Properties dialogs
├── WinIcons.kt             folder and drive glyphs, file type icons, thumbnails, size formatting
├── FileIntents.kt          open and share through FileProvider
├── PickerActivity.kt       "choose a file" requests from other apps
├── PickSupport.kt          type filtering for picks; mapping "open folder" intents to paths
└── Theme.kt                Windows 11 colours (dark and light)
```

## Notes on permissions
- **Android 11+** uses `MANAGE_EXTERNAL_STORAGE` ("All files access"). This is the only way to get a real
  file manager on modern Android. Google Play only allows it for apps whose core purpose is file management,
  and you have to declare it in the Play Console.
- **Android 7–10** uses the classic `READ/WRITE_EXTERNAL_STORAGE` runtime permissions.
- Even with All files access, Android blocks `Android/data` and `Android/obb` on 11+. Those folders appear empty.

## Ideas for next steps
- Tabs, like Windows 11 Explorer
- A Recycle Bin instead of permanent delete
- Progress and cancel for large copies (move the work to `WorkManager`)
- Zip and unzip
