# Changelog

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
