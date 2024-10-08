# Compose screenshot preview testing
This project is a screenshot testing of compose preview layouts. It helps to catch regressions on layout changes.

### How screenshots are saved
Git LFS is used to the screenshots. To pull all of them use the following command:
```git lfs fetch --all```

Screenshots are located in ```app/src/productionGooglePlayStoreDebug/screenshotTest/reference/com/protonvpn/android/ScreenshotTest```

### How to update screenshots
To update the current set of screenshots, run the following Gradle command:
```./gradlew "-Duser.language=en" "-Duser.country=US" updateProductionGooglePlayStoreDebugScreenshotTest```

### How to run the tests
To validate the screenshots against the baseline, use the following Gradle command:
```./gradlew "-Duser.language=en" "-Duser.country=US" validateProductionGooglePlayStoreDebugScreenshotTest```
