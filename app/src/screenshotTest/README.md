# Compose screenshot preview testing
This project is a screenshot testing of compose preview layouts. It helps to catch regressions on layout changes.

### How screenshots are saved
Git LFS is used to the screenshots. To pull all of them use the following command:
```git lfs fetch --all```

Screenshots are located in ```app/src/productionGooglePlayStoreDebug/screenshotTest/reference/com/protonvpn/android/ScreenshotTest```

### How to update screenshots
Update screenshots by running the CI job. This ensures that they are always generated in the same exact way.

To generate the reference screenshots locally, run the following Gradle command:
```./gradlew "-Duser.language=en" "-Duser.country=US" updateProductionGooglePlayStoreDebugScreenshotTest```

Generating screenshots locally can produce different results on different platforms due to varying rendering engines.

### How to run the tests
To validate the screenshots against the baseline, use the following Gradle command:
```./gradlew "-Duser.language=en" "-Duser.country=US" validateProductionGooglePlayStoreDebugScreenshotTest```
