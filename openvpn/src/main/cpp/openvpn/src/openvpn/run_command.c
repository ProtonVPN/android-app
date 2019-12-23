/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single TCP/UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2002-2017 OpenVPN Technologies, Inc. <sales@openvpn.net>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2
 *  as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#elif defined(_MSC_VER)
#include "config-msvc.h"
#endif

#include "syshead.h"

#include "buffer.h"
#include "error.h"
#include "platform.h"
#include "win32.h"

#include "memdbg.h"

#include "run_command.h"

/* contains an SSEC_x value defined in platform.h */
static int script_security_level = SSEC_BUILT_IN; /* GLOBAL */

int
script_security(void)
{
    return script_security_level;
}

void
script_security_set(int level)
{
    script_security_level = level;
}

/*
 * Print an error message based on the status code returned by system().
 */
static const char *
system_error_message(int stat, struct gc_arena *gc)
{
    struct buffer out = alloc_buf_gc(256, gc);
#ifdef _WIN32
    if (stat == -1)
    {
        buf_printf(&out, "external program did not execute -- ");
    }
    buf_printf(&out, "returned error code %d", stat);
#else  /* ifdef _WIN32 */
    if (stat == -1)
    {
        buf_printf(&out, "external program fork failed");
    }
    else if (!WIFEXITED(stat))
    {
        buf_printf(&out, "external program did not exit normally");
    }
    else
    {
        const int cmd_ret = WEXITSTATUS(stat);
        if (!cmd_ret)
        {
            buf_printf(&out, "external program exited normally");
        }
        else if (cmd_ret == 127)
        {
            buf_printf(&out, "could not execute external program");
        }
        else
        {
            buf_printf(&out, "external program exited with error status: %d", cmd_ret);
        }
    }
#endif /* ifdef _WIN32 */
    return (const char *)out.data;
}

bool
openvpn_execve_allowed(const unsigned int flags)
{
    if (flags & S_SCRIPT)
    {
        return script_security() >= SSEC_SCRIPTS;
    }
    else
    {
        return script_security() >= SSEC_BUILT_IN;
    }
}


#ifndef _WIN32
/*
 * Run execve() inside a fork().  Designed to replicate the semantics of system() but
 * in a safer way that doesn't require the invocation of a shell or the risks
 * associated with formatting and parsing a command line.
 */
int
openvpn_execve(const struct argv *a, const struct env_set *es, const unsigned int flags)
{
    struct gc_arena gc = gc_new();
    int ret = -1;
    static bool warn_shown = false;

    if (a && a->argv[0])
    {
#if defined(ENABLE_FEATURE_EXECVE)
        if (openvpn_execve_allowed(flags))
        {
            const char *cmd = a->argv[0];
            char *const *argv = a->argv;
            char *const *envp = (char *const *)make_env_array(es, true, &gc);
            pid_t pid;

            pid = fork();
            if (pid == (pid_t)0) /* child side */
            {
                execve(cmd, argv, envp);
                exit(127);
            }
            else if (pid < (pid_t)0) /* fork failed */
            {
                msg(M_ERR, "openvpn_execve: unable to fork");
            }
            else /* parent side */
            {
                if (waitpid(pid, &ret, 0) != pid)
                {
                    ret = -1;
                }
            }
        }
        else if (!warn_shown && (script_security() < SSEC_SCRIPTS))
        {
            msg(M_WARN, SCRIPT_SECURITY_WARNING);
            warn_shown = true;
        }
#else  /* if defined(ENABLE_FEATURE_EXECVE) */
        msg(M_WARN, "openvpn_execve: execve function not available");
#endif /* if defined(ENABLE_FEATURE_EXECVE) */
    }
    else
    {
        msg(M_FATAL, "openvpn_execve: called with empty argv");
    }

    gc_free(&gc);
    return ret;
}
#endif /* ifndef _WIN32 */

/*
 * Wrapper around openvpn_execve
 */
bool
openvpn_execve_check(const struct argv *a, const struct env_set *es, const unsigned int flags, const char *error_message)
{
    struct gc_arena gc = gc_new();
    const int stat = openvpn_execve(a, es, flags);
    int ret = false;

    if (platform_system_ok(stat))
    {
        ret = true;
    }
    else
    {
        if (error_message)
        {
            msg(((flags & S_FATAL) ? M_FATAL : M_WARN), "%s: %s",
                error_message,
                system_error_message(stat, &gc));
        }
    }
    gc_free(&gc);
    return ret;
}

/*
 * Run execve() inside a fork(), duping stdout.  Designed to replicate the semantics of popen() but
 * in a safer way that doesn't require the invocation of a shell or the risks
 * associated with formatting and parsing a command line.
 */
int
openvpn_popen(const struct argv *a,  const struct env_set *es)
{
    struct gc_arena gc = gc_new();
    int ret = -1;
    static bool warn_shown = false;

    if (a && a->argv[0])
    {
#if defined(ENABLE_FEATURE_EXECVE)
        if (script_security() >= SSEC_BUILT_IN)
        {
            const char *cmd = a->argv[0];
            char *const *argv = a->argv;
            char *const *envp = (char *const *)make_env_array(es, true, &gc);
            pid_t pid;
            int pipe_stdout[2];

            if (pipe(pipe_stdout) == 0)
            {
                pid = fork();
                if (pid == (pid_t)0)       /* child side */
                {
                    close(pipe_stdout[0]);         /* Close read end */
                    dup2(pipe_stdout[1],1);
                    execve(cmd, argv, envp);
                    exit(127);
                }
                else if (pid > (pid_t)0)       /* parent side */
                {
                    int status = 0;

                    close(pipe_stdout[1]);        /* Close write end */
                    waitpid(pid, &status, 0);
                    ret = pipe_stdout[0];
                }
                else       /* fork failed */
                {
                    close(pipe_stdout[0]);
                    close(pipe_stdout[1]);
                    msg(M_ERR, "openvpn_popen: unable to fork %s", cmd);
                }
            }
            else
            {
                msg(M_WARN, "openvpn_popen: unable to create stdout pipe for %s", cmd);
                ret = -1;
            }
        }
        else if (!warn_shown && (script_security() < SSEC_SCRIPTS))
        {
            msg(M_WARN, SCRIPT_SECURITY_WARNING);
            warn_shown = true;
        }
#else  /* if defined(ENABLE_FEATURE_EXECVE) */
        msg(M_WARN, "openvpn_popen: execve function not available");
#endif /* if defined(ENABLE_FEATURE_EXECVE) */
    }
    else
    {
        msg(M_FATAL, "openvpn_popen: called with empty argv");
    }

    gc_free(&gc);
    return ret;
}
