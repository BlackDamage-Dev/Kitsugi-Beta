# Validation artifacts

These logs are real sandbox runs against the listed production sources and test doubles. They are not a full Gradle/Android build.

- 61 selected pure tests PASS
- 7 scheduler + 3 cross-write tests PASS (71 selected unit tests total)
- 8 functional scenario groups PASS, including two backup JUnit tests
- 12 TMDB error-classification checks PASS; their production sources were unchanged after that run

Still required on an Android SDK machine: testDebugUnitTest, assembleDebug, Room device migration, live TMDB account and phone/TV UI verification.
