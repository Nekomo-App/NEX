# Third-party notices

NEX is licensed under the Apache License 2.0. It bundles these open-source libraries (all Apache License 2.0 unless noted):

| Library | Purpose |
|---|---|
| AndroidX (Core, Activity, Lifecycle, Navigation, Room, WorkManager, DataStore) | Android framework support, storage, background work |
| Jetpack Compose and Material 3 | User interface |
| OkHttp 4.12 | Networking |
| kotlinx.serialization, kotlinx.coroutines | JSON and concurrency |
| Coil 2.7 | Image loading |

Mihon, Aniyomi, CloudStream and Zangetsu are separate projects, not affiliated with NEX. Their names are used only to describe compatibility.
Regenerate the exact dependency list with `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` before each release.
