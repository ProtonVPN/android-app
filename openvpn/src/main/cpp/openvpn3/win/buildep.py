import glob
import os
import re

from utils import *

def compile_one_file(parms, srcfile, incdirs):
    extra = {
        "srcfile" : srcfile,
        "incdirs" : ' '.join([r"/I %s" % (x,) for x in incdirs]),
        }

    vc_parms(parms, extra)

    vc_cmd(parms, r"cl /c /DNOMINMAX /D_CRT_SECURE_NO_WARNINGS %(incdirs)s /EHsc %(link_static_dynamic_flags)s /W3 %(dbg_rel_flags)s /nologo %(srcfile)s" % extra, arch=os.environ.get("ARCH"))

def build_asio(parms):
    print "**************** ASIO"
    with Cd(build_dir(parms)):
        asio_ver = parms["ASIO_VERSION"]
        url = "https://github.com/chriskohlhoff/asio/archive/%s.tar.gz" % asio_ver
        arch_path = os.path.join(build_dir(parms), download(url))
        checksum = sha256_checksum(arch_path)
        if checksum != parms["ASIO_CSUM"]:
            sys.exit("Checksum mismatch, expected %s, actual %s" % (parms["ASIO_CSUM"], checksum))
        with ModEnv('PATH', "%s\\bin;%s" % (parms.get('GIT'), os.environ['PATH'])):
            extract(arch_path, "gz")
            dist = os.path.realpath('asio')
            rmtree(dist)
            os.rename("asio-%s" % asio_ver, dist)
            rm(arch_path)

            for patch_file in glob.glob(os.path.join(parms.get('OVPN3'), "core", "deps", "asio", "patches", "*.patch")):
                call(["git", "apply", "--whitespace=nowarn", "--ignore-space-change", "--verbose", patch_file], cwd=dist)

def build_mbedtls(parms):
    print "**************** MBEDTLS"
    with Cd(build_dir(parms)):
        url = "https://tls.mbed.org/download/%s-apache.tgz" % parms["MBEDTLS_VERSION"]
        arch_path = os.path.join(build_dir(parms), download(url))
        checksum = sha256_checksum(arch_path)
        if checksum != parms["MBEDTLS_CSUM"]:
            sys.exit("Checksum mismatch, expected %s, actual %s" % (parms["MBEDTLS_CSUM"], checksum))
        with ModEnv('PATH', "%s\\bin;%s" % (parms.get('GIT'), os.environ['PATH'])):
            extract(arch_path, "gz")
            dist = os.path.realpath('mbedtls')
            rmtree(dist)
            os.rename(parms["MBEDTLS_VERSION"], dist)
            rm(arch_path)

            # edit mbedTLS config.h
            conf_fn = os.path.join(dist, 'include', 'mbedtls', 'config.h')
            with open(conf_fn) as f:
                conf = f.read()
            conf = re.sub(r"^//(?=#define MBEDTLS_MD4_C)", "", conf, flags=re.M);
            with open(conf_fn, 'w') as f:
                f.write(conf)

            # apply patches
            unapplicable_patches = ["0005-data_files-pkcs8-v2-add-keys-generated-with-PRF-SHA1.patch"]

            for patch_file in glob.glob(os.path.join(parms.get('OVPN3'), "core", "deps", "mbedtls", "patches", "*.patch")):
                for unapplicable_patch in unapplicable_patches:
                    if patch_file.endswith(unapplicable_patch):
                        print "Skipping %s, 'git apply' doesn't apply it on Windows" % patch_file
                        break
                else:
                    call(["git", "apply", "--whitespace=nowarn", "--ignore-space-change", "--verbose", patch_file], cwd=dist)

            # compile the source files
            os.chdir(os.path.join(dist, "library"))
            obj = []
            for dirpath, dirnames, filenames in os.walk("."):
                for f in filenames:
                    if f.endswith(".c"):
                        compile_one_file(parms, f, (r"..\include",))
                        obj.append(f[:-2]+".obj")
                break

            # collect object files into mbedtls.lib
            vc_cmd(parms, r"lib /OUT:mbedtls.lib " + ' '.join(obj))

def build_lz4(parms):
    print "**************** LZ4"
    with Cd(build_dir(parms)):
        url = "https://github.com/lz4/lz4/archive/v%s.tar.gz" % parms["LZ4_VERSION"][4:]
        arch_name = download(url)
        checksum = sha256_checksum(arch_name)
        if checksum != parms["LZ4_CSUM"]:
            sys.exit("Checksum mismatch, expected %s, actual %s" % (parms["LZ4_CSUM"], checksum))
        with ModEnv('PATH', "%s\\bin;%s" % (parms.get('GIT'), os.environ['PATH'])):
            extract(arch_name, "gz")
            dist = os.path.realpath('lz4')
            rmtree(dist)
            os.rename(parms["LZ4_VERSION"], dist)
            rm(arch_name)
            os.chdir(os.path.join(dist, "lib"))
            compile_one_file(parms, "lz4.c", ())
            vc_cmd(parms, r"lib /OUT:lz4.lib lz4.obj")

def build_tap(parms):
    print "**************** Windows-TAP"
    with Cd(build_dir(parms)):
        url = "https://github.com/OpenVPN/tap-windows6/archive/%s.zip" % parms["TAP_VERSION"]
        arch_name = download(url)
        checksum = sha256_checksum(arch_name)
        if checksum != parms["TAP_CSUM"]:
            sys.exit("Checksum mismatch, expected %s, actual %s" % (parms["TAP_CSUM"], checksum))
        with ModEnv('PATH', "%s\\bin;%s" % (parms.get('GIT'), os.environ['PATH'])):
            extract(arch_name, "zip")
            dist = os.path.realpath('tap-windows')
            rmtree(dist)
            os.rename("tap-windows6-%s" % parms["TAP_VERSION"], dist)
            rm(arch_name)

def build_jsoncpp(parms):
    print "**************** JSONCPP"
    with Cd(build_dir(parms)):
        url = "https://github.com/open-source-parsers/jsoncpp/archive/%s.tar.gz" % parms["JSONCPP_VERSION"]
        arch_name = download(url)
        checksum = sha256_checksum(arch_name)
        if checksum != parms["JSONCPP_CSUM"]:
            sys.exit("Checksum mismatch, expected %s, actual %s" % (parms["JSONCPP_CSUM"], checksum))
        with ModEnv('PATH', "%s\\bin;%s" % (parms.get('GIT'), os.environ['PATH'])):
            dist = os.path.realpath('jsoncpp')
            rmtree(dist)
            extract(arch_name, "gz")
            rm(arch_name)
            os.rename("jsoncpp-%s" % parms["JSONCPP_VERSION"], dist)
            os.chdir(dist)
            call(["python", "amalgamate.py"])
            os.chdir(os.path.join(dist, "dist"))
            compile_one_file(parms, "jsoncpp.cpp", (".",))
            vc_cmd(parms, r"lib /OUT:jsoncpp.lib jsoncpp.obj")

def build_all(parms):
    wipetree(build_dir(parms))
    build_asio(parms)
    build_mbedtls(parms)
    build_lz4(parms)
    build_jsoncpp(parms)
    build_tap(parms)

if __name__ == "__main__":
    build_all(read_params())
