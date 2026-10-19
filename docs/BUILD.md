# Build and verify

## Requirements

- JDK 17 or newer (Android Studio’s bundled runtime works).
- Android SDK with compile platform 37, build tools, platform-tools, and accepted SDK licenses.
- Network access for the pinned Gradle/Maven dependencies on the first build.

Set JAVA_HOME and ANDROID_HOME for your machine. The helper script can also find Android Studio and the SDK at their usual macOS locations. No service key is needed.

```sh
./scripts/build.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug package is `dev.kiankonrad.currencyconverter.debug`; production is `dev.kiankonrad.currencyconverter`. Minimum SDK 28, target SDK 36, compile SDK 37. Gradle wrapper 9.3.1, Android Gradle Plugin 9.1.1, Kotlin 2.2.20, Compose BOM 2026.09.00. Build outputs and project caches go under `~/.cache/currency-converter/`.

## Native integration tests

Start an Android 16 emulator or connect an authorized test device, then run:

```sh
./scripts/build.sh :app:connectedDebugAndroidTest
```

The integration journey uses the actual network provider. It tests calculations, percentage rules, live conversion against the returned rate table, history/recreation, the AED vector, fixed swap position, keypad controls, creator credit, and icon selection for the device’s installed-pack state. It clears only the debug app’s preferences for test isolation. Use a dedicated emulator, and expect the live check to depend on provider availability. Release/phone data are separate.

## Release signing

The published APK uses a persistent private signing key. That key and its passwords are never included in the repository. To build your own signed APK, create your own keystore and an untracked private file at `~/.config/currency-converter/signing.properties`:

```properties
storeFile=/absolute/path/to/your/private-release.jks
storePassword=YOUR_PRIVATE_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_PRIVATE_KEY_PASSWORD
```

Then build:

```sh
./scripts/build.sh :app:assembleRelease
```

Your own signature will not update the author’s signed installation. Use a distinct application ID for a fork, or install it separately. Keep the same signing key for updates to your own distribution. Never commit keys, password files, local SDK paths, or rate caches.

## Source layout

- MainActivity.kt: native UI, haptics, connectivity handling, currency picker and about/history views.
- ConverterState.kt: entry, currency pair, history, persistence and rate coordination.
- MoneyInput.kt / MoneyEngine.kt: keypad behavior and decimal expression evaluation.
- RatesRepository.kt: validated snapshots, local crossrates, fiat metadata and last-good cache.
- LauncherIconManager.kt: this app’s normal/white launcher alias selection.
- Assets/Dirham/: exact official vector source and provenance.

Known boundaries: provider snapshots update approximately every five minutes; rate availability is external. Whicons detection means the package is installed, not that every launcher has enabled that pack. An existing pinned icon can remain cached until the launcher refreshes it.
