import os, sys, re, stat, shutil, tarfile, zipfile, subprocess
import requests
import rfc6266
import hashlib

j = os.path.join

class Cd(object):
    """
    Cd is a context manager that allows
    you to temporary change the working directory.

    with Cd(dir) as cd:
        ...
    """

    def __init__(self, directory):
        self._dir = directory

    def orig(self):
        return self._orig

    def __enter__(self):
        self._orig = os.getcwd()
        os.chdir(self._dir)
        return self

    def __exit__(self, *args):
        os.chdir(self._orig)

class ModEnv(object):
    """
    Context manager for temporarily
    modifying an env var.  Normally used to make
    changes to PATH.
    """

    def __init__(self, key, value):
        self.key = key;
        self.value = value;

    def __enter__(self):
        self.orig_value = os.environ.get(self.key)
        os.environ[self.key] = self.value
        return self

    def __exit__(self, *args):
        if self.orig_value is not None:
            os.environ[self.key] = self.orig_value            

def rmtree(dir):
    print "RMTREE", dir
    shutil.rmtree(dir, ignore_errors=True)

def rm(fn, silent=False):
    if os.path.exists(fn):
        if not silent:
            print "RM", fn
        os.remove(fn)

def makedirs(dir):
    print "MAKEDIRS", dir
    os.makedirs(dir)

def cp(src, dest):
    print "COPY %s %s" % (src, dest)
    shutil.copy2(src, dest)

def wipetree(dir, wipe=True):
    def onerror(func, path, exc_info):
        """
        Error handler for ``shutil.rmtree``.

        If the error is due to an access error (read only file)
        it attempts to add write permission and then retries.

        If the error is for another reason it ignores.

        Usage : ``shutil.rmtree(path, onerror=onerror)``
        """
        if not os.access(path, os.W_OK):
            # Is the error an access error ?
            try:
                os.chmod(path, stat.S_IWUSR)
                func(path)
            except:
                pass

    if wipe:
        print "WIPETREE", dir
        shutil.rmtree(dir, ignore_errors=False, onerror=onerror)
    if not os.path.isdir(dir):
        makedirs(dir)

def extract_dict(d, k, default=None):
    if k in d:
        v = d[k]
        del d[k]
    else:
        v = default
    return v

def scan_prefixes(prefix, dir, filt=None):
    fns = []
    for dirpath, dirnames, filenames in os.walk(dir):
        for f in filenames:
            if f.startswith(prefix) and (filt is None or filt(f)):
                fns.append(f)
        break
    return fns

def one_prefix(prefix, dir, filt=None):
    f = scan_prefixes(prefix, dir, filt)
    if len(f) == 0:
        raise ValueError("prefix %r not found in dir %r" % (prefix, dir))
    elif len(f) >= 2:
        raise ValueError("prefix %r is ambiguous in dir %r: %r" % (prefix, dir, f))
    return f[0]

def tarsplit(fn):
    if fn.endswith(".tar.gz"):
        t = 'gz'
        b = fn[:-7]
    elif fn.endswith(".tgz"):
        t = 'gz'
        b = fn[:-4]
    elif fn.endswith(".tar.bz2"):
        t = 'bz2'
        b = fn[:-8]
    elif fn.endswith(".tbz"):
        t = 'bz2'
        b = fn[:-4]
    elif fn.endswith(".tar.xz"):
        t = 'xz'
        b = fn[:-7]
    else:
        raise ValueError("unrecognized tar file type: %r" % (fn,))
    return b, t

def zipsplit(fn):
    if fn.endswith(".zip"):
        t = "zip"
        b = fn[:-4]
    else:
        raise ValueError("unrecognized zip file type: %r" % (fn,))
    return b, t

def archsplit(fn):
    try:
        b, t = tarsplit(fn)
    except:
        b, t = zipsplit(fn)
    return b, t

def archsplit_filt(fn):
    try:
        tarsplit(fn)
    except:
        try:
            zipsplit(fn)
        except:
            return False
        else:
            return True
    else:
        return True

def extract(fn, t):
    print "%s EXTRACT %s [%s]" % ("ZIP" if t == "zip" else "TAR", fn, t)

    if t == "zip":
        with zipfile.ZipFile(fn) as z:
            z.extractall()
    else:
        tar = tarfile.open(fn, mode='r:'+t)
        try:
            tar.extractall()
        finally:
            tar.close()

def expand(pkg_prefix, srcdir, lib_versions=None, noop=False):
    if lib_versions and pkg_prefix in lib_versions:
        f = one_prefix(lib_versions[pkg_prefix], srcdir, archsplit_filt)
    else:
        f = one_prefix(pkg_prefix, srcdir, archsplit_filt)

    b, t = archsplit(f)

    if not noop:
        # remove previous directory
        rmtree(os.path.realpath(b))

        # expand it
        extract(os.path.join(srcdir, f), t)

    return b

def call(cmd, **kw):
    print "***", cmd

    ignore_errors = extract_dict(kw, 'ignore_errors', False)
    extra_env = extract_dict(kw, 'extra_env', None)
    if extra_env:
        env = kw.get('env', os.environ).copy()
        env.update(extra_env)
        kw['env'] = env
    succeed = extract_dict(kw, 'succeed', 0)

    # show environment
    se = kw.get('env')
    if se:
        show_env(se)
        print "***"

    ret = subprocess.call(cmd, **kw)
    if not ignore_errors and ret != succeed:
        raise ValueError("command failed with status %r (expected %r)" % (ret, succeed))

def vc_cmd(parms, cmd, arch=None, succeed=0):
    # arch should be one of amd64 (alias x64), x86, x86_xp, or None
    # (if None, use parms.py value)
    if arch is None:
        arch = parms['ARCH']
    if arch == "x64":
        arch = "amd64"
    with ModEnv('PATH', "%s;%s\\VC;%s\\VC\\Auxiliary\\Build;" % (os.environ['PATH'], parms['MSVC_DIR'], parms['MSVC_DIR'])):
        call('vcvarsall.bat %s && %s' % (arch, cmd), shell=True, succeed=succeed)

def vc_parms(parms, cmd_dict):
    cmd_dict["dbg_rel_flags"] = "/Zi" if parms['DEBUG'] else "/O2"
    flags = "/MT" if parms['STATIC'] else "/MD"
    if parms['DEBUG']:
        flags += "d"
    cmd_dict["link_static_dynamic_flags"] = flags

def patchfile(pkg_prefix, patchdir):
    return os.path.join(patchdir, one_prefix(pkg_prefix, patchdir))

def patch(pkg_prefix, patchdir):
    patch_fn = patchfile(pkg_prefix, patchdir)
    print "PATCH", patch_fn
    call(['patch', '-p1', '-i', patch_fn])

def build_dir(parms):
    return os.path.join(parms['BUILD'], parms['ARCH'])

# remove .obj files
def rm_obj(dir):
    fns = []
    for dirpath, dirnames, filenames in os.walk(dir):
        for f in filenames:
            path = os.path.join(dirpath, f)
            if f.endswith(".obj"):
                rm(path)

# zip a directory
# sample usage:
#   zipf = zipfile.ZipFile('Python.zip', 'w')
#   zipdir('tmp/', zipf)
#   zipf.close()
def zipdir(path, ziph):
    # ziph is zipfile handle
    for root, dirs, files in os.walk(path):
        for file in files:
            ziph.write(os.path.join(root, file))

def download(url):
    print "Downloading %s" % url
    response = requests.get(url)
    fname = rfc6266.parse_headers(response.headers['content-disposition']).filename_unsafe
    with open(fname, "wb") as f:
        f.write(response.content)
    return fname

def sha256_checksum(filename, block_size=65536):
    sha256 = hashlib.sha256()
    with open(filename, 'rb') as f:
        for block in iter(lambda: f.read(block_size), b''):
            sha256.update(block)
    return sha256.hexdigest()

def read_params():
    if not os.environ.get('O3'):
        sys.exit("Missing required O3 env variable")

    params={}
    params['OVPN3'] = os.environ.get('O3').rstrip()
    if not os.environ.get('DEP_DIR'):
        params["BUILD"] = os.path.join(params['OVPN3'], "deps")
    else:
        params['BUILD'] = os.environ.get('DEP_DIR').rstrip()
    params['ARCH'] = os.environ.get('ARCH', 'amd64').rstrip()
    params['DEBUG'] = os.environ.get('DEBUG')
    params['STATIC'] = os.environ.get('STATIC')
    params['MSVC_DIR'] = os.environ.get('MSVC_DIR', 'c:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Professional').rstrip()
    # Community: tap0901, Access Server: tapoas
    params['TAP_WIN_COMPONENT_ID'] = os.environ.get('TAP_WIN_COMPONENT_ID', 'tap0901')
    params['CPP_EXTRA'] = os.environ.get('CPP_EXTRA', '').rstrip()
    if os.environ.get('USE_JSONSPP'):
        params['USE_JSONCPP'] = True
    if os.environ.get('USE_JSONSPP'):
        params['CONNECT'] = True
    params['GTEST_ROOT'] = os.environ.get('GTEST_ROOT')

    # read versions
    with open(os.path.join(params['OVPN3'], "core", "deps", "lib-versions")) as f:
        for l in [line.strip() for line in f if line.strip()]:
            name, val = l.split("=")
            if name.startswith("export"):
                name = name[6:].strip()
            params[name] = val

    return params
