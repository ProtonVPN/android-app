import os, re

from utils import *
from parms import PARMS

def build_openssl(parms):
    print "**************** OpenSSL"
    with Cd(parms['BUILD']) as cd:
        with ModEnv('PATH', "%s;%s\\bin;%s" % (parms.get('NASM'), parms.get('GIT'), os.environ['PATH'])):
            dist = os.path.realpath('openssl')
            rmtree(dist)
            d = expand('openssl', parms['DEP'], parms.get('LIB_VERSIONS'))
            os.chdir(d)
            patch("ossl-win", parms['PATCH'])
            makedirs(dist)
            # needs more work for x64, see:
            # http://stackoverflow.com/questions/158232/how-do-you-compile-openssl-for-x64
            targets = {
                'x86' : "VC-WIN32",
                'amd64' : "VC-WIN64A",
                }
            call(['perl', 'Configure', targets[parms['ARCH']], 'no-idea', 'no-mdc2', 'no-rc5', '--prefix=%s' % (dist,)])
            archscripts = {
                'x86'   : "ms\\do_nasm",
                'amd64' : "ms\\do_win64a",
                }
            vc_cmd(parms, archscripts[parms['ARCH']])
            vc_cmd(parms, "nmake -f ms\\ntdll.mak")
            vc_cmd(parms, "nmake -f ms\\ntdll.mak install")

            # copy DLLs to PARMS['DIST']
            cp(os.path.join(dist, "bin", "libeay32.dll"), PARMS['DIST'])
            cp(os.path.join(dist, "bin", "ssleay32.dll"), PARMS['DIST'])

def build_boost(parms):
    print "**************** Boost"
    with Cd(parms['BUILD']) as cd:
        d = expand('boost', parms['DEP'], parms.get('LIB_VERSIONS'))
        os.chdir(d)
        archopts = {
            'x86'   : "",
            'amd64' : "architecture=x86 address-model=64",
            }
        vc_cmd(parms, "bootstrap", arch="x86")
        vc_cmd(parms, "b2 --toolset=msvc-12.0 --with-system --with-thread --with-atomic --with-date_time --with-regex link=shared threading=multi runtime-link=shared %s stage" % (archopts[parms['ARCH']],))

        # copy DLLs to PARMS['DIST']
        r = re.compile(r"boost_(atomic|chrono|system|thread)-vc\d+-mt-[\d_]+\.dll")
        os.chdir(os.path.join("stage", "lib"))
        for dirpath, dirnames, filenames in os.walk('.'):
            for f in filenames:
                if re.match(r, f):
                    cp(f, PARMS['DIST'])
            break

wipetree(PARMS['BUILD'])
wipetree(PARMS['DIST'])
build_openssl(PARMS)
build_boost(PARMS)
