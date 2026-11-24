# AdMob, UMP, Room, Compose and Play Review supply their own consumer rules.
# AndroidViewModelFactory constructs this application view model reflectively.
-keepclassmembers class com.drygonkiller.wordsearch.GameViewModel {
    public <init>(android.app.Application);
}
