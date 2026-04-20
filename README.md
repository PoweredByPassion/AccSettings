# Acc Setting

Native Android UI for managing [Advanced Charging Controller](https://github.com/VR-25/acc) on rooted devices.

## Highlights

- Native Compose UI with Overview, Configuration, Tools, and About pages
- Draft-based configuration editing before applying changes to the device
- Install, repair, restart, and diagnostics actions for ACC
- Built-in ACC runtime log viewer for troubleshooting
- English and Simplified Chinese resources

## Package

- App name: `Acc Setting`
- Application id: `app.owlow.accsetting`

## Development

Requirements:

- Android SDK 36
- Java 17+
- Rooted target device for real ACC integration

Useful commands:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./scripts/build-debug-apk.sh
```

## Project Layout

- `app/`: Android application module
- `docs/`: plans and project documentation
- `scripts/`: local build and release helpers

## Documentation

- [Changelog](CHANGELOG.md)
- [Docs index](docs/README.md)
