/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single TCP/UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2002-2018 OpenVPN Inc <sales@openvpn.net>
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

#ifndef MISC_H
#define MISC_H

#include "argv.h"
#include "basic.h"
#include "common.h"
#include "env_set.h"
#include "integer.h"
#include "buffer.h"
#include "platform.h"

/* socket descriptor passed by inetd/xinetd server to us */
#define INETD_SOCKET_DESCRIPTOR 0

/* forward declarations */
struct plugin_list;


/* Set standard file descriptors to /dev/null */
void set_std_files_to_null(bool stdin_only);

/* dup inetd/xinetd socket descriptor and save */
extern int inetd_socket_descriptor;
void save_inetd_socket_descriptor(void);

/* Make arrays of strings */

const char **make_arg_array(const char *first, const char *parms, struct gc_arena *gc);

const char **make_extended_arg_array(char **p, struct gc_arena *gc);

/* prepend a random prefix to hostname */
const char *hostname_randomize(const char *hostname, struct gc_arena *gc);

/*
 * Get and store a username/password
 */

struct user_pass
{
    bool defined;
    bool nocache;
    bool wait_for_push; /* true if this object is waiting for a push-reply */

/* max length of username/password */
#ifdef ENABLE_PKCS11
#define USER_PASS_LEN 4096
#else
#define USER_PASS_LEN 128
#endif
    char username[USER_PASS_LEN];
    char password[USER_PASS_LEN];
};

#ifdef ENABLE_MANAGEMENT
/*
 * Challenge response info on client as pushed by server.
 */
struct auth_challenge_info {
#define CR_ECHO     (1<<0)  /* echo response when typed by user */
#define CR_RESPONSE (1<<1)  /* response needed */
    unsigned int flags;

    const char *user;
    const char *state_id;
    const char *challenge_text;
};

struct auth_challenge_info *get_auth_challenge(const char *auth_challenge, struct gc_arena *gc);

/*
 * Challenge response info on client as pushed by server.
 */
struct static_challenge_info {
#define SC_ECHO     (1<<0)  /* echo response when typed by user */
    unsigned int flags;

    const char *challenge_text;
};

#else  /* ifdef ENABLE_MANAGEMENT */
struct auth_challenge_info {};
struct static_challenge_info {};
#endif /* ifdef ENABLE_MANAGEMENT */

/*
 * Flags for get_user_pass and management_query_user_pass
 */
#define GET_USER_PASS_MANAGEMENT    (1<<0)
/* GET_USER_PASS_SENSITIVE     (1<<1)  not used anymore */
#define GET_USER_PASS_PASSWORD_ONLY (1<<2)
#define GET_USER_PASS_NEED_OK       (1<<3)
#define GET_USER_PASS_NOFATAL       (1<<4)
#define GET_USER_PASS_NEED_STR      (1<<5)
#define GET_USER_PASS_PREVIOUS_CREDS_FAILED (1<<6)

#define GET_USER_PASS_DYNAMIC_CHALLENGE      (1<<7) /* CRV1 protocol  -- dynamic challenge */
#define GET_USER_PASS_STATIC_CHALLENGE       (1<<8) /* SCRV1 protocol -- static challenge */
#define GET_USER_PASS_STATIC_CHALLENGE_ECHO  (1<<9) /* SCRV1 protocol -- echo response */

#define GET_USER_PASS_INLINE_CREDS (1<<10)  /* indicates that auth_file is actually inline creds */

bool get_user_pass_cr(struct user_pass *up,
                      const char *auth_file,
                      const char *prefix,
                      const unsigned int flags,
                      const char *auth_challenge);

static inline bool
get_user_pass(struct user_pass *up,
              const char *auth_file,
              const char *prefix,
              const unsigned int flags)
{
    return get_user_pass_cr(up, auth_file, prefix, flags, NULL);
}

void fail_user_pass(const char *prefix,
                    const unsigned int flags,
                    const char *reason);

void purge_user_pass(struct user_pass *up, const bool force);

void set_auth_token(struct user_pass *up, struct user_pass *tk,
                    const char *token);

/*
 * Process string received by untrusted peer before
 * printing to console or log file.
 * Assumes that string has been null terminated.
 */
const char *safe_print(const char *str, struct gc_arena *gc);


void configure_path(void);

const char *sanitize_control_message(const char *str, struct gc_arena *gc);

/*
 * /sbin/ip path, may be overridden
 */
#ifdef ENABLE_IPROUTE
extern const char *iproute_path;
#endif

#if P2MP_SERVER
/* helper to parse peer_info received from multi client, validate
 * (this is untrusted data) and put into environment */
bool validate_peer_info_line(char *line);

void output_peer_info_env(struct env_set *es, const char *peer_info);

#endif /* P2MP_SERVER */

#endif /* ifndef MISC_H */
