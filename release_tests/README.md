# Tests for release builds of Proton VPN

The project is a test app that runs UiAutomator tests against Proton VPN. The tests can be run
on against non-debug, R8 optimized builds.

These tests serve two purposes:
- `smoke` folder serve as a sanity check for R8 optimizations and don't replace the full application test suite.
- `sli` folder contains all E2E performance (SLI) tests, which are being used to track client's performance over time.

### Executing tests

- Install app that is using `productionGoogleDevDebug` flavor

- Build and install the test app:

```
./gradlew :release_tests:installDebug
```
- Run the tests

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
adb shell am instrument -w -e class com.protonvpn.android.release_tests.tests.smoke.SmokeTest#testSignIn com.protonvpn.android.release_tests/androidx.test.runner.AndroidJUnitRunner
```

See https://developer.android.com/studio/test/command-line#run-tests-with-adb for more.

### Android Studio support

Unfortunately the test app is a bit of a hack and Android Studio is unable to run tests directly
nor debug them. You can use "Run" in Android Studio to deploy the application and then use one
of the commands above to run them.

## E2E Performance (SLI) tests

### 🎯 Purpose

This project is meant to track E2E performance of critical client paths.

### ⚙️ How does it work?

It combines E2E tests with Loki and Grafana. While the test is running, specific flows are measured and reported to Loki with logs, allowing investigation of each individual event.

### 🛠️ How to Set Up a Performance (SLI) Test?

There are 3 main parts:

- Mark test that it is a measurement
```
@Test
@Measure <--- Annotation to mark this test that this is a measurement and results should be reported to Loki.
fun loginSli() {
    // Test flow goes here
}
```

- Setup measurement profile. You will need to provide the following information:

```
val measurementContext = measurementRule.measurementContext(LokiConfig.measurementConfig)  <--- Setups Loki environment where to push data and adds environment metadata to metrics.

val profile = measurementContext
    .setWorkflow("login_flow") <--- Groups measurements and logs by defining shared name.
    .setServiceLevelIndicator("login") <--- Set unique name for the measurement.
    .addMeasurement(DurationMeasurement()) <--- Select what test should measure.
    .setLogcatFilter(LokiConfig.logcatFilter) <--- Define what type of logs should be reported.
```

- Define using profile which part of flow should be measured

```
profile.measure {
    LoginRobot.waitUntilLoggedIn()
}
```

Full tests can be found in `sli` folder. The easiest flow is the login one, which is store in `MainMeasurementsSli` class.

## 🔑 Environment Variables

#### Shared varriables

`TEST_ACCOUNT_PASSWORD` - password for paid user.

#### SLI specific varriables

`LOKI_CERTIFICATE_ANDROID` - Certificate of the internal Loki instance. Used to authenticate API calls to Loki.
`LOKI_PRIVATE_KEY_ANDROID` - Private key of the internal Loki instance. Used to authenticate API calls to Loki.
`BTI_CONTROLLER_URL` - Used to control networking scenarios on internal testing infrastructure. Required for Anti-Censorship SLIs.
`LOKI_ENDPOINT` - Endpoint to the internal Loki instance. Used by the API client to push data.
