# Unit test framework for OpenVPN3 #

The unit test framework is written in the [Google Test](https://github.com/google/googletest)
framework.

## Building/running the unit tests ##
Before building the unit tests themselves, you should build the dependencies
as described in the [README.rst](../../README.rst).

The unit test cmake files assume here that the deps directory is on the same
level as the openvpn3 directory unless overridden by the DEP_DIR variable.

The directory for cmake to build a project can be everywhere, but it is recommended to keep
it outside of the source tree. 

Building unit tests (assuming you are in the openvpn3 directory):

    ➜ mkdir ../unit_test_build
    ➜ cd ../unit_test_build
    ➜ cmake ../openvpn3
    ➜ cmake --build . --target coreUnitTests

Note: On Linux and Mac OS you can use `make coreUnitTests` instead of `cmake --build`

Run the unit tests:

    ➜  ./test/unittests/coreUnitTests --gtest_shuffle

On a Mac with OpenSSL from [homebrew](brew.sh):

    ➜ cmake ../openvpn3 -DOPENSSL_ROOT_DIR=/usr/local/opt/openssl@1.1

Using mbed TLS instead of OpenSSL

    ➜ cmake ../openvpn3 -DUSE_MBEDTLS

A full list of build options can shown together with short descriptions can be shown with

    ➜ cmake -LH .

Examplary commands for building and running on Windows:

    ➜ cmake -DDEP_DIR=C:\o3\deps -DUSE_MBEDTLS=true -DCMAKE_GENERATOR_PLATFORM=x64 C:\o3\openvpn3
    ➜ cmake --build . --target coreUnitTests
    ➜ test\unittests\Debug\coreUnitTests.exe --gtest_output="xml:test_core.xml" --gtest_shuffle
    
### Frequently used command line options ###

Show the help for gtest command line options:
    
    ➜ ./test/unittests/coreUnitTests --help
    
Run only tests starting with Base64 or a sepcific Base64 test:

    ➜ ./test/unittests/coreUnitTests --gtest_filter='Base64.*'
    ➜ ./test/unittests/coreUnitTests --gtest_filter=Base64.tooshortdest
    
Run all test but the Base64 tests

    ➜ ./test/unittests/coreUnitTests --gtest_filter='-Base64.*'
    
Multiple pattern can be specified with a list separated by :

    ➜ ./test/unittests/coreUnitTests --gtest_filter='OpenSSL_X509_get_serial.*:Base64.*'
    
    
Shuffle order the order in which the tests are run:

    ➜ ./test/unittests/coreUnitTests --gtest_shuffle

If a certain order yields failures, repeat that order:

    ➜ ./test/unittests/coreUnitTests --gtest_shuffle --gtest_random_seed=23
    
Run also the tests that are normally disabled

    ➜ ./test/unittests/coreUnitTests --gtest_also_run_disabled_tests

## Writing unit tetss ##

Each new test suite should be a new a file called `test_suitename.cpp` and added to the
`CMAKELists.txt` file. Eah test includes an `#include test_common.h` at the top to setup
common openvpn3 library parameter and to set the `OPENVPN_EXTERN` define to `extern`. This
is done so header files that define globals can be included multiple times. The only file that
includes headers without the `OPENVPN_EXTERN` being set is the `core_tests.cpp` file. All
global includes should be done in this file.

Currently all tests can fit in the same compilation unit `coreUnitTests`. If a unit test
requires special compile/includes or other options that are not compatible with the rest of
the unit tests, another  compilation unit should be added to the CMAKELists.txt

The `test_helper.cc` file adds helper functions that can be used for unit tests. See the file
for more information.
