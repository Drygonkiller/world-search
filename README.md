# Word Search - Puzzle Games

An offline Android word search. Milestone 1 has Easy, Medium, and Hard puzzles, 12 bundled categories, a date-seeded Daily Challenge, and on-device progress and settings.

## Build and test

Use JDK 17 and install Android SDK Platform 36. Open the project in Android Studio or run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

On Windows PowerShell, run `./gradlew.bat testDebugUnitTest assembleDebug`. The app supports Android 8.0 (API 26) and later.

## Phone acceptance check

1. Install the debug build on a real Android phone. Keep internet access enabled to receive the AdMob test ad.
2. Open the app, complete any consent prompt, and tap Play. Drag from the first to the last letter of each word to solve the Easy puzzle.
3. Use **Use free hint**, then **Watch ad for a hint**. Watch the test ad until it awards the reward and dismiss it. Confirm a first letter is highlighted after each hint.
4. Finish the Easy puzzle and check the 10-coin reward. Return Home, finish the Daily Challenge, and check the 20-coin reward.
5. Force-stop the app and reopen it. Check 30 coins, a one-day streak, and today's Daily Challenge marked complete when starting from clean app data.

Puzzle generation, solving, progress, coins, streaks, and settings use local data. AdMob test ads and Google's UMP consent flow are the app's only network features.
