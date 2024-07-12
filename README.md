# ProtonVPN for Android

Copyright (c) 2019 Proton AG

## Download

<p align="center">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/2.jpg" height="400">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/3.jpg" height="400">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/4.jpg" height="400">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/5.jpg" height="400">
</p>

<p align="center">
    <a href="https://play.google.com/store/apps/details?id=ch.protonvpn.android">
        <img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" alt="Get it on Google Play" height="80">
    </a>
    <a href="https://f-droid.org/packages/ch.protonvpn.android">
        <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
    </a>
    <a href="https://github.com/ProtonVPN/android-app/releases">
        <img src="https://i.postimg.cc/kXfbyWjL/get-it-on-github.png" alt="Get it on GitHub" height="80">
    </a>
</p>

## Build instructions
- Install sdk, ndk, cmake, swig
- Clone this repository
- `./gradlew assembleProductionVanillaOpenSourceDebug` or open and build in Android Studio

### Build release
To complete a release build signing keys need to be provided:
- `./gradlew assembleProductionVanillaOpenSourceRelease -PkeyStoreFilePath=<keystore> -PkeyStoreKeyAlias=<alias> -PkeyStorePassword=<pass> -PkeyStoreKeyPassword=<key-pass>`

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
```
gradlew checkstyle
gradlew detekt
gradlew test
gradlew androidTest
```

By making a contribution to this project you agree to the following:

1. I assign any and all copyright related to the contribution to Proton Technologies AG;
2. I certify that the contribution was created in whole by me;
3. I understand and agree that this project and the contribution are public and that a record of the contribution (including all personal information I submit with it) is maintained indefinitely and may be redistributed with this project or the open source license(s) involved.

## Signature

All our builds (except for F-droid) will be signed with same key.
SHA256 fingerprint for signing certificate
```
ch.protonvpn.android
DC:C9:43:9E:C1:A6:C6:A8:D0:20:3F:34:23:EE:42:BC:C8:B9:70:62:8E:53:CB:73:A0:39:3F:39:8D:D5:B8:53
```

## Versioning
- Version matches format: `[major][minor][patch][hotfix]`

## License

The code and datafiles in this distribution are licensed under the terms of the GPLv3 as published by the Free Software Foundation. See <https://www.gnu.org/licenses/> for a copy of this license.

Copyright (c) 2019 Proton AG
