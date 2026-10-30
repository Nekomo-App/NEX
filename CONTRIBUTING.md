# Contributing

1. Use JDK 17 and Android SDK 35. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` before opening a PR.
2. Keep each app's logic in its own adapter. Don't claim a capability an app doesn't expose; document the limitation instead.
3. New repository formats need a parser in `Formats` and a unit test with a real-world sample.
4. Never commit keystores, API keys or `local.properties`.
5. Don't add or bundle third-party repositories. NEX ships empty.
