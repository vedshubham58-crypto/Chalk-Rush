# Chalk Rush

An Android game where you draw chalk loops on a sidewalk to claim land, against computer players.

- Swipe to steer. Leave your land, draw a loop, and come back to claim everything inside.
- If anyone touches your line while you're out, you're knocked out. Cut other players' lines to knock them out.
- Rounds last 3 minutes. Whoever owns the most of the sidewalk wins.

Built with Kotlin and Jetpack Compose. The game rules live in `app/src/main/java/com/vedshubham/chalkrush/game` and are plain Kotlin with unit tests.

## Test build

Every push to `main` builds a test APK at:
https://github.com/vedshubham58-crypto/Chalk-Rush/releases/download/test-build/chalk-rush-test.apk
