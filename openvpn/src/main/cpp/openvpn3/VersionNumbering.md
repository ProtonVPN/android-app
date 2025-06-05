OpenVPN 3 version numbering and release process
===============================================

OpenVPN 3 version numbers will always be prefixed with `3.` which
indicates the OpenVPN generation. This library is the third generation
of the OpenVPN protocol implementation.

Since OpenVPN 3.2 we use a single positive integer indicating a
release number as the version reference.

Git branches and versioning
---------------------------

Main development will happen on the git master branch. This will not
contain any specific version. It is will be set to `3.X_git:master`;
where `X` is the version number of the next planned release. This
branch will contain both stable and unstable code, which will be
bleeding edge at any time. Do not depend on git master for production
code.

Once features and fixes in git master have stabilized, they will be
merged into the `released` branch. Code extracted from this branch
will contain the release number of the last release. The `released`
branch is suitable for production code.

We usually only support one release at a time so there is only one
`released` branch. Older releases can still be accesses via their
tags.

There is no specific plan for when releases will occur. We might
want to collect up a smaller set of features before defining it ready as
a release, depending on the size of the changes. At release time,
the version string will be updated and the release tagged
(with PGP signature).

We should not pile up too many features for each release. It is better
to release often with smaller changesets.

Hot-fixes
---------

We will not do any patch number releases for the current release
unless strictly needed. Such releases will be called hot-fixes and will
be prepared in separate branches. These branches will be
named `hotfix/3.X.Y`; where `X` denotes the release number the hotfix
targets and `Y` the number of the hotfix for that release.
The version number in the hotfix branch will be `3.X.Y_dev` to
indicate that the hotfix is still being prepared.

Once a hotfix is ready for release it will be merged into the `released`
branch. The version number will then be changed to `3.X.Y` and the
hotfix release tagged.

Hot-fixes will only be used for highly critical issues which cannot wait
for a release or if the feature gap to move to a newer release is
considered too big. But it should also only occur for releases which are
still relevant.

Examples
--------

git `master` branch: version string will be e.g. `3.11_git:master`.

git `released` branch: version string will be e.g. `3.10` if there was no
hotfix for 3.10.

First hotfix for v3.10 will be in `hotfix/3.10.1` and the version string will be
`3.10.1_dev`. When the hotfix is ready it will be merged to `released` and the
version number changed to `3.10.1`.
