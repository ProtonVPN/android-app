//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//
//

#pragma once


#include <mbedtls/ctr_drbg.h>
#include <mbedtls/version.h>
#include <mbedtls/pem.h>

#if not defined(MBEDTLS_ERR_SSL_BAD_PROTOCOL_VERSION)
#define MBEDTLS_ERR_SSL_BAD_PROTOCOL_VERSION MBEDTLS_ERR_SSL_BAD_HS_PROTOCOL_VERSION
#endif

#if not defined(MBEDTLS_OID_X509_EXT_EXTENDED_KEY_USAGE)
#define MBEDTLS_OID_X509_EXT_EXTENDED_KEY_USAGE MBEDTLS_X509_EXT_KEY_USAGE
#endif

#if MBEDTLS_VERSION_NUMBER < 0x03000000
static inline const mbedtls_md_info_t *
mbedtls_md_info_from_ctx(const mbedtls_md_context_t *ctx)
{
    if (ctx == nullptr)
    {
        return nullptr;
    }
    return ctx->md_info;
}

static inline int
mbedtls_x509_crt_has_ext_type(const mbedtls_x509_crt *crt, int ext_type)
{
    return crt->ext_types & ext_type;
}

static inline const unsigned char *
mbedtls_pem_get_buffer(const mbedtls_pem_context *ctx, size_t *buf_size)
{
    *buf_size = ctx->buflen;
    return ctx->buf;
}
#endif
