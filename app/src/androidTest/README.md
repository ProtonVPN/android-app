# Main test suite for Proton VPN Android client

## 🔧 Tech Stack

This project utilizes the following technologies for automated UI testing:

- [Espresso](https://developer.android.com/training/testing/espresso) – A Native UI testing framework, mainly used for legacy UI that is not written in compose.
- [JUnit 4](https://junit.org/junit4/) – A unit testing framework for Java (not .NET), which provides structure and assertions for writing and running tests.
- [Jetpack Compose](https://developer.android.com/develop/ui/compose/testing) – A Natie UI testing framework, used for Compose layouts and Unit UI tests.
- [Firebase Test Lab](https://firebase.google.com/docs/test-lab) - Used for running tests on CI.

## 📁 Folder structure

**Data** - contains all constants required for tests e.g Timeouts, etc...

**Di** - All dependency injections are stored here to mock functionality of client.

**Interfaces** - Interfaces for UI tests are saved, so that it follows the same structure across different classes/objects.

**Matchers** - contains customer matchers, which Fusion library currently does not support it.

**Robots** - Defines all UI actions and selectors for UI components. It follows the following structure:

- A Robot class is an object that stores all elements, actions, and verifications related to a UI component or page.
- Every UI element is saved as `val` constant inside robots.
- Every action is saved as a function that returns the Robot it’s stored in.

**TestRules** - here all test rules (SetUp/Cleanup methods) are saved.

**Tests** - this folder contains all tests implemented. Postfix at the end is used for marking test type:

- *Compose* - Mocked Compose components UI tests. Useful to check different states without doing E2E flows to set it up.
- *Core* - Core provided tests for shared components.
- *Black* - Tests are being executed against test environment. Real backend is used.
- *Prod* - Real backend tests that are being executed against production API.
- *Integration* - Integration test that involves testing of 2 isolated components. The reason why it's not inside unit test project, because it requires some dependency from the client like go go-vpn-lib or etc...

**TestHelper** - Like name suggests contains different helpers for tests.

**TestSuites** - All test suites that are being executed on CI are saved here.

## 🔥 Firebase device config

Device config can be found in `firebase-device-config.yml`. For device coverage we are trying to cover:

- Highest supported Android version.

- Lowest supported Android version (If tooling supports it and test suite is stable)

## 🔑 Environment Variables

`BLACK_TOKEN` - Use for authentication of API to test environments.

`TEST_ENV_DOMAIN` - Test environment URL that is going to be used in the build.

`TEST_ACCOUNT_PASSWORD` - Paid account password that is being used on production.

`SPECIAL_CHAR_PASSWORD` - Password that contains special chars, used for login test case.