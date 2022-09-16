# ProtonVPN for Android

Copyright (c) 2019 Proton Technologies AG

## Build instructions
- Install sdk, ndk, cmake, swig
- Clone this repository
- `./gradlew assembleProdDebug` or open and build in Android Studio

### Build release
To complete a release build signing keys need to be provided:
./gradlew assembleProdRelease -PkeyStoreFilePath=<keystore> -PkeyStoreKeyAlias=<alias> -PkeyStorePassword=<pass> -PkeyStoreKeyPassword=<key-pass>`

## Code style
Project uses agreed code style ProtonStyle.xml for java. Import it in Android studio via ```File>>Settings>>Editor>>Code style>>Import Scheme```
For kotlin's code style we use ktlint with default rules

## Contributing
If you would like to contribute, please keep in mind the following rules:
- Try to stick to the project's existing code style and naming conventions
- The code base is mostly written in Java, but we are transitioning to Kotlin, so new code should be written in Kotlin where possible
- Our preferred tech stack is Kotlin, MVVM, data-binding and coroutines, so any new features or large refactors should conform to this preferred tech stack
- After adding/updating open source dependencies run `gradlew updateLicensesJson` to update attributions.

Internally our CI automatically checks all pull requests for code style issues, and runs our tests. However you can run those locally as well.
```bash
gradlew checkstyle
gradlew detekt
gradlew test
gradlew androidTest
```

By making a contribution to this project you agree to the following:

1. I assign any and all copyright related to the contribution to Proton Technologies AG;
2. I certify that the contribution was created in whole by me;
3. I understand and agree that this project and the contribution are public and that a record of the contribution (including all personal information I submit with it) is maintained indefinitely and may be redistributed with this project or the open source license(s) involved.

## Manage Image for the CI

### Create an image NAME_IMAGE:branch-<branch>

```sh
$ make image [branch=name]
```

### For the dev ~ Build the image on your computer: output ci-android:latest

```sh
$ make local
```

### Run the image built via make local on your compute so you can inspect its content

```sh
$ make debug-local
```

### Deploy the image of the CI from your computer

```sh
$ make deploy-local: login-deploy imag
```

## Versioning
- Version matches format: `[major][minor][patch]`

## License

The code and datafiles in this distribution are licensed under the terms of the GPLv3 as published by the Free Software Foundation. See <https://www.gnu.org/licenses/> for a copy of this license.

Copyright (c) 2019 Proton Technologies AG
