OpenVPN 3 version numbering and release process
===============================================

OpenVPN 3 version numbers will always be prefixed with ``3.`` which
indicates the OpenVPN generation.  This library is the third
generation of the OpenVPN protocol implementation.

As of OpenVPN 3.2, we will use a single positive integer indicating a
release number as the version reference.


Git branches and versioning
---------------------------
Main development will happen on the git master branch.  This will not
contain any specific version.  It is will be set to ``3.git:master``.
This branch will contain both stable and unstable code, which will be
bleeding edge at any time.  Do not depend on git master for production code.

Once features and fixes in git master has stabilized, they will be
merged into the ``stable`` branch.  Code extracted from the stable branch
will contain the release number of the last release.  The stable
branch is suitable for production code.

It is not set up a specific plan for when releases will occur. We
might want to collect up a smaller set of features before defining it
ready as a release, depending on the size of the changes.  At the
release time, the version string will be updated and tagged (with
a PGP signature).

We should not pile up too many features for each release.  It is
better to release often with smaller changesets.


Hot-fixes
---------

We will not do any patch number releases unless strictly needed for
older releases numbers.  Such releases will be called hot-fixes and
will be handled in separate branches only when needed. These branches
will be named ``hotfix/3.X``; where X denotes the release number the
hotfix targets.  Hotfixes need to update the version string as well
as attaching a git tag with the proper version number.

**Hot-fixes should be avoided as much as possible** and we should
**encourage users to base their work on the stable branch** primarily.
Hot-fixes will only be used for highly critical issues which cannot
wait for a release or the feature gap to move to a newer release is
considered too big.  But it should also only occur for releases which
are still relevant.


Examples
--------

git ``master`` branch:  version string will be ``3.git:master``

git ``stable`` branch:  version string will be ``3.2``, ``3.3``, etc

hotfix for v3.2 will be in ``hotfix/3.2`` and the version string will be
``3.2.1``

Similarly, hotfix for v3.3 will be found in ``hotfix/3.3`` and the version
string will be ``3.3.1``.
