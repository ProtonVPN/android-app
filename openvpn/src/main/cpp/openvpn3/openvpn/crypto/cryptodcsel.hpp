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

// Select appropriate OpenVPN protocol data channel implementation

#ifndef OPENVPN_CRYPTO_CRYPTODCSEL_H
#define OPENVPN_CRYPTO_CRYPTODCSEL_H

#include <openvpn/common/exception.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/crypto/crypto_chm.hpp>
#include <openvpn/crypto/crypto_aead.hpp>
#include <openvpn/crypto/crypto_aead_epoch.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

OPENVPN_EXCEPTION(crypto_dc_select);

/**
 * Implements the data channel encryption and decryption in userspace
 */
template <typename CRYPTO_API>
class CryptoDCSelect : public CryptoDCFactory
{
  public:
    typedef RCPtr<CryptoDCSelect> Ptr;

    CryptoDCSelect(SSLLib::Ctx libctx_arg,
                   const Frame::Ptr &frame_arg,
                   const SessionStats::Ptr &stats_arg,
                   const StrongRandomAPI::Ptr &rng_arg)
        : frame(frame_arg),
          stats(stats_arg),
          rng(rng_arg),
          libctx(libctx_arg)
    {
    }

    CryptoDCContext::Ptr new_obj(CryptoDCSettingsData dc_settings) override
    {
        const CryptoAlgs::Alg &alg = CryptoAlgs::get(dc_settings.cipher());
        if (alg.mode() == CryptoAlgs::CBC_HMAC)
            return new CryptoContextCHM<CRYPTO_API>(libctx, std::move(dc_settings), frame, stats, rng);
        else if (alg.mode() == CryptoAlgs::AEAD && dc_settings.useEpochKeys())
            return new AEADEpoch::CryptoContext<CRYPTO_API>(libctx, std::move(dc_settings), frame, stats);
        else if (alg.mode() == CryptoAlgs::AEAD)
            return new AEAD::CryptoContext<CRYPTO_API>(libctx, std::move(dc_settings), frame, stats);
        else
            OPENVPN_THROW(crypto_dc_select, alg.name() << ": only CBC/HMAC and AEAD cipher modes supported");
    }

  private:
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    StrongRandomAPI::Ptr rng;
    SSLLib::Ctx libctx;
};

} // namespace openvpn

#endif
