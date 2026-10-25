# Neon Rush

An Android game: ride a glowing bike around a neon grid, loop back to your zone to claim land, and hit other riders' light trails to knock them out. (The repo is still called Chalk-Rush from the first version.)

- Swipe to steer. Bikes ride straight and turn at right angles, leaving straight light walls. Leave your zone, draw a loop, and come back to claim everything inside.
- If anyone touches your trail while you're out, you're knocked out. Crossing your own trail is fine, and the arena wall turns you aside.
- Ride right next to another rider's trail to grind for a speed boost.
- Rounds last 45 seconds. Finish with the most land, or reach the map's target, to win and unlock the next of 10 maps.
- 8 riders with perks, unlocked with coins. Power-ups: speed, shield and energy burst.
- Every win starts a short Bonus Rush: grab flying coins, gems and mystery boxes. A daily reward grows over 7 days.
- Ads (AdMob, Google test ads until real IDs are set in `gradle.properties`): optional videos to revive, double coins, open a golden box, triple the daily reward, or take the mid-round Power Drop (shield + speed, the round pauses while it plays); a full-screen ad every 2nd round, between rounds only; and banners on the menus.

Built with Kotlin and Jetpack Compose. The game rules live in `app/src/main/java/com/vedshubham/chalkrush/game` and are plain Kotlin with unit tests.

## Test build

Every push to `main` builds a test APK at:
https://github.com/vedshubham58-crypto/Chalk-Rush/releases/download/test-build/chalk-rush-test.apk
