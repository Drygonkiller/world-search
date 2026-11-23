# Google Play Data safety cheat sheet

App package `com.drygonkiller.wordsearch` · Release build with Google Mobile Ads SDK and User Messaging Platform (UMP).

Declare that the app contains ads. In Data safety, declare collection and sharing where applicable to the AdMob SDK and your release configuration. Offline puzzle generation, preferences, progress, hints, coins and streaks stay on-device.

| SDK data to review | Play Console data type | What the SDK uses it for |
| --- | --- | --- |
| IP address used to infer a general location | Location → Approximate location | Advertising, analytics and fraud prevention |
| App launches, taps and ad video views | App activity → App interactions | Advertising, analytics and fraud prevention |
| SDK and app performance signals | App info and performance → Diagnostics | Advertising, analytics and fraud prevention |
| Android Advertising ID, app set ID and applicable device or account identifiers | Device or other IDs | Advertising, analytics and fraud prevention |

- Report advertising or marketing, analytics, and fraud prevention, security and compliance as applicable. Review diagnostics separately from crash logs and other performance-data categories if you enable features that collect them.
- Google states that the Mobile Ads SDK encrypts its collected data in transit. Check any SDK or mediation changes and the actual release configuration before answering the form's encryption, collection and sharing questions.
- Offline play remains available. For each data type, verify the form's required/optional and ephemeral-processing answers against your AdMob and UMP configuration and Google Play's definitions.
- The app has no account creation or developer-side player records. Players can delete on-device progress through Android's app storage controls or by uninstalling. Google handles its advertising data under its own privacy and retention policies.
- Replace `[DEVELOPER_EMAIL]` in both policy files, host the HTML at a publicly accessible HTTPS URL, and use that URL in Play Console and in the app before submission.

## Official references

- [Google Mobile Ads SDK data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- [Google Play Data safety form guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google UMP for Android](https://developers.google.com/admob/android/privacy)
