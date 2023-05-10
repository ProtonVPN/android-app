//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#include <stdio.h>
#include <unistd.h>
#include <jni.h>

#ifdef SWIGEXPORT
#define EXPORT SWIGEXPORT
#else
#define EXPORT
#endif

#ifndef OPENVPN_PACKAGE_ID
#error OPENVPN_PACKAGE_ID must be defined
#endif

#define MAKE_SYM2(pkg_id, suffix) Java_##pkg_id##_CPUUsage_##suffix
#define MAKE_SYM(pkg_id, suffix) MAKE_SYM2(pkg_id, suffix)

#define CPU_USAGE MAKE_SYM(OPENVPN_PACKAGE_ID, cpu_1usage)

extern "C"
{
    jdouble CPU_USAGE(JNIEnv *env, jclass);
};

EXPORT jdouble CPU_USAGE(JNIEnv *env, jclass)
{
    char fnbuf[64];
    const pid_t pid = getpid();
    double ret = 0.0;

    snprintf(fnbuf, sizeof(fnbuf), "/proc/%u/stat", (unsigned int)pid);
    FILE *fp = fopen(fnbuf, "r");
    if (fp)
    {
        double user = 0.0;
        double system = 0.0;
        if (fscanf(fp, "%*s %*s %*s %*s %*s %*s %*s %*s %*s %*s %*s %*s %*s %lf %lf", &user, &system) == 2)
            ret = (user + system) / sysconf(_SC_CLK_TCK);
        fclose(fp);
    }
    return ret;
}
