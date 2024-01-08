# Tests for release builds of Proton VPN

The project is a test app that runs UiAutomator tests against Proton VPN. The tests can be run
on against non-debug, R8 optimized builds.

These tests serve as a sanity check for R8 optimizations and don't replace the full application
test suite.

## Executing tests

Build and install the test app:

```
./gradlew :release_tests:installDebug
```

With Test Orchestrator that clears app data between tests:

```
adb shell 'CLASSPATH=$(pm path androidx.test.services) app_process / \
 androidx.test.services.shellexecutor.ShellMain am instrument -w \
 -e clearPackageData true \
 -e class com.protonvpn.android.release_tests.suites.AllTests \
 -e targetInstrumentation com.protonvpn.android.release_tests/androidx.test.runner.AndroidJUnitRunner \
 androidx.test.orchestrator/.AndroidTestOrchestrator'
```

Directly run single test (`testSignIn` in the example below):  
(app state is preserved between tests but it's faster, use for running single tests)
```
adb shell am instrument -w -e class com.protonvpn.android.release_tests.SmokeTest#testSignIn com.protonvpn.android.release_tests/androidx.test.runner.AndroidJUnitRunner
```

See https://developer.android.com/studio/test/command-line#run-tests-with-adb for more.

## Android Studio support

Unfortunately the test app is a bit of a hack and Android Studio is unable to run tests directly
nor debug them. You can use "Run" in Android Studio to deploy the application and then use one
of the commands above to run them.
