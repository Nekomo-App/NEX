# Changelog

## 0.2.3-alpha
- **Custom app name and icon:** Settings > App name and icon. Pick a name and picture with a live preview, add it to the home screen as a shortcut, and reset to default at any time. It also shows in the long-press shortcut and the recent-apps card. Android TV launchers cannot pin shortcuts, so on TV it applies to the recent-apps card and long-press shortcut only. Separate from Nekomo.

## 0.2.2-alpha
- **Fuller backup and restore:** a backup now also stores the list of installed extensions (restore shows what to reinstall) and, if you choose a passphrase, your API keys encrypted with it (PBKDF2 + AES-256-GCM). Repositories, APIs and preferences were already included. Existing entries are kept when restoring.

## 0.2.1-alpha
- Add repository accepts a repository code as well as a URL. A code can add a repository or open a source or API in Nekomo (Nekomo 2.9.2+).
- Third-party notices added (docs/THIRD_PARTY.md).

## 0.2.0-alpha
- **Built-in updater:** NEX checks its own GitHub releases (pre-releases included while alpha) and installs updates after verifying the SHA-256 from `SHA256SUMS.txt` and the package name. Updates show in Updates, in background checks and in the notification. Debug builds never self-update.
- **Open in Nekomo:** sources and extensions (CloudStream plugins and Zangetsu scripts) open in Nekomo with `nekomo-repo://source?repo=&id=&type=`, which shows the source and installs it after you confirm. Any repository (Nekomo, CloudStream, Zangetsu, Aniyomi, Mihon) can be sent with "Open in Nekomo" (`nekomo-repo://add?url=`), and Nekomo's built-in metadata APIs open with `nekomo-repo://api?id=`. Needs Nekomo 2.9.2 or newer; older versions get the URL copied. Nekomo never installs APK extensions.
- **No screenshots:** screenshots, screen recording and the recents thumbnail are blocked in NEX (FLAG_SECURE).
- The first-run legal screen respects the status and navigation bars, so "I understand and agree" is no longer hidden behind the system bar.
- **New signing key.** 0.2.0-alpha is signed with a new key, so Android will not update 0.1.x over it: uninstall NEX 0.1.x once, then install 0.2.0-alpha. Future updates install in place.

## 0.1.1-alpha
- "Install in <app>" on extensions NEX can't install itself (CloudStream plugins, Nekomo/Zangetsu JS sources): opens the app's own add-repository screen for the repo offering the latest version, or copies the URL and opens the app when it has no link.
- "Open <app>" on extension and repository details; "Open in <app>" on APIs copies the endpoint and opens the app.
- API keys follow redirects only within the same https host and port and under the API's path.

## 0.1.0-alpha
- First working build, based on the NEX Android starter: Home, Extensions, Sources, Updates and Settings.
- Adapters for Nekomo, Mihon, Aniyomi, CloudStream and Zangetsu.
- Repository preview, validation, trust and refresh. Extension install, update and uninstall for APK-based apps. Background update checks.
- APIs with Keystore-encrypted keys, backup and restore, diagnostics, Android TV layout, and the legal notice.
- Fixes before release: readable legal notice on dark themes; Nekomo manifest details list linked repositories; HTTPS policy applies to manifest child repos and APIs; API keys aren't sent across cross-host redirects; Android 13+ asks for notification permission; empty Mihon/Aniyomi repos can be added; partial child-repo failures show as warnings; the compatibility-warnings switch works; oversized downloads are cleaned up; enabling a restored repo loads it; one permission screen per install.
