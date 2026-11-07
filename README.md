# cordova-plugin-inappbrowser-kotlin-swift

Modern Cordova In-App Browser plugin with native implementations in Kotlin (Android) and Swift (iOS).

## Features

- Native Kotlin Android implementation
- Native Swift iOS implementation
- Compatible with the standard Cordova InAppBrowser JavaScript API

## Quick start

### Installation

Install from npm (when published):

```bash
cordova plugin add cordova-plugin-inappbrowser-kotlin-swift
```

Install from GitHub:

```bash
cordova plugin add https://github.com/<user>/cordova-plugin-inappbrowser-kotlin-swift.git
```

Install locally:

```bash
cordova plugin add /path/to/cordova-plugin-inappbrowser-kotlin-swift
```

### Usage

Open a URL:

```js
var ref = cordova.InAppBrowser.open('https://example.com', '_blank', 'location=yes,toolbar=yes');
```

Listen for events:

```js
ref.addEventListener('loadstart', function(event) { console.log('loadstart', event.url); });
ref.addEventListener('loadstop',  function(event) { console.log('loadstop', event.url); });
ref.addEventListener('loaderror', function(event) { console.error('loaderror', event.message); });
ref.addEventListener('exit',      function()      { console.log('IAB closed'); });
```

Close the browser:

```js
ref.close();
```

### Options

- `location` — `yes`/`no` (show URL bar)
- `toolbar` — `yes`/`no` (platform-specific)
- `hidden` — `yes`/`no` (open hidden)
- `clearcache` / `clearsessioncache` — `yes`/`no`

Platform support and option availability may vary between Android and iOS; refer to platform-specific notes below.

## Platform notes

- **Android**: implementation is written in Kotlin and builds with Gradle using AndroidX. Minimum supported Android API: 24 (Android 7.0).
- **iOS**: implementation is written in Swift; ensure your Cordova iOS platform supports Swift code. Minimum deployment target: iOS 15.0.

## Development / Testing

Create a test app and add the plugin locally, or use the included Android demo:

```bash
# Using Cordova to create a demo
cordova create demo
cd demo
cordova platform add android
cordova platform add ios
cordova plugin add /path/to/cordova-plugin-inappbrowser-kotlin-swift
cordova run android
# or
cordova run ios

# Or use the included Android demo (has Gradle wrapper):
cd android-demo
./gradlew :app:test      # run unit tests (Linux/macOS)
.\gradlew.bat :app:test  # run unit tests (Windows)
./gradlew :app:connectedAndroidTest  # run instrumentation tests (requires device/emulator)
```

When contributing, run device/emulator tests on both platforms and include any required native build configuration in your PR.

## Android demo — running tests locally

Prerequisites:
- JDK 11 or newer installed and `java` available on PATH.
- Internet access for the first run to download the Gradle distribution.
- Android SDK (for instrumentation tests) and an emulator or device for connected tests.

Run unit tests (Windows):

```powershell
cd android-demo
.\gradlew.bat :app:test
```

Run unit tests (macOS/Linux):

```bash
cd android-demo
./gradlew :app:test
```

Run instrumentation tests (requires emulator/device):

```bash
cd android-demo
./gradlew :app:connectedAndroidTest
```

Notes:
- The wrapper downloads Gradle to `android-demo/.gradle` on first run.
- If you see download errors, ensure PowerShell/curl/wget can access the internet and that `java -version` returns the installed JDK.
- If you prefer CI, I can add a GitHub Actions job that runs `./gradlew :app:test`.

## Contributing

Fork the repository, create a feature branch, add tests and documentation, and submit a pull request. Open issues for bugs or feature requests.

## License

See the LICENSE file for license details.

---

If you'd like, I can add a small example app in this repo showing the plugin in use, or create CI config for automated testing.
