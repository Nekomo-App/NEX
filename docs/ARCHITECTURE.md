# Architecture

NEX is a single `:app` module (Kotlin, Jetpack Compose, Material 3), split into packages:

- **integrations**: `IntegrationAdapter` defines what an app integration provides: package names, supported `RepoFormat`s, a `Capability` set, how to detect the app and its installed extensions, how to parse an index, compatibility rules and how to hand a repository to the app (`RepoHandoff.OpenIntent` or `CopyOrShare`). `IntegrationRegistry` lists the five adapters. `Formats` holds the pure parsers, which are unit tested.
- **data**: `NexRepository` is the single entry point the UI and worker use. It handles preview → add → refresh, merges installed and remote extensions into `ExtensionRow`s, and covers install and update, API testing, backup and maintenance. Persistence uses Room (`NexDatabase`: repositories, extensions, history, activity, apis), DataStore (`SettingsStore`) and `SecureStore` (an AndroidKeyStore AES-GCM key for API credentials).
- **net**: `NexHttp` is a shared OkHttp client with a disk cache, configurable timeout, a 16 MB index cap and a 200 MB APK cap.
- **pkg**: `ApkInstaller` verifies the downloaded APK (SHA-256, package name, version code, required `uses-feature`, and that the signing certificate matches the installed copy). It then opens a `PackageInstaller` session, and the system shows its own confirmation. `InstallResultReceiver` passes the result back.
- **work**: `UpdateWorker` is a periodic WorkManager job (network, battery-not-low and optionally unmetered) that refreshes repos, checks for updates and posts a notification.
- **ui**: `NexApp` shows a bottom navigation bar on phones and a navigation rail on TV (`LocalIsTv`). It has five tabs, matching the starter design: Home, Extensions, Sources, Updates, Settings.

Adding an app: implement `IntegrationAdapter`, add any new `RepoFormat` and parser to `Formats`, register the adapter, and add tests.
