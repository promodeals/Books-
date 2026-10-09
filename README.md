# Books — Android personal library

This is the standalone **Books** Android app project in its own repository.

Standalone Java Android app. The installed app label is **Books**.

## Features
- Import files through Android's system document picker (Google Files may appear as a source).
- Copies imported files into private app storage.
- Local SQLite book catalog, search by title/author/category, filters for unread/reading/finished/favorites.
- Edit title, author, category, reading progress, rating and notes.
- Open supported files in installed reader apps, share files, favorite books, and delete imported copies.
- No account or cloud service required.

## Build APK using GitHub Actions
Push to `main` or manually run the **Build Books APK** workflow under the repository's Actions tab. Download the `Books-debug-apk` artifact from a successful run and install the APK on Android.

This project has not yet been verified on a physical device. File viewing depends on a compatible reader app being installed.
