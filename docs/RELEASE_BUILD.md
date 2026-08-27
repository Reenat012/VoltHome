# VoltHome release build

Release configuration is intentionally kept outside Git.

## Required configuration

AppMetrica must be supplied through one of these sources, in priority order:

1. `APP_METRICA_API_KEY` environment variable;
2. Gradle property in `~/.gradle/gradle.properties`;
3. ignored project file `secrets.properties`.

For local signing, the existing ignored `keystore.properties` remains supported.
CI may use the following environment variables instead:

- `VOLTHOME_KEYSTORE_PATH`;
- `VOLTHOME_KEYSTORE_PASSWORD`;
- `VOLTHOME_KEY_ALIAS`;
- `VOLTHOME_KEY_PASSWORD`.

## Build and verify

```shell
./gradlew clean testDebugUnitTest lintDebug assembleRelease
```

Before upload, verify that the APK is signed with the expected upload certificate
and that `versionCode` is greater than the latest code published in RuStore.
