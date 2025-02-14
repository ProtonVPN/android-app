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

// OpenVPN 3 wrapper for kovpn crypto

#ifndef OPENVPN_KOVPN_KOREKEY_H
#define OPENVPN_KOVPN_KOREKEY_H

#include <openvpn/dco/kocrypto.hpp>

namespace openvpn::KoRekey {

class Receiver : public virtual RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<Receiver> Ptr;

    virtual void rekey(const CryptoDCInstance::RekeyType type,
                       const Info &info) = 0;

    virtual void explicit_exit_notify()
    {
    }
};

class Instance : public CryptoDCInstance
{
  public:
    Instance(const Receiver::Ptr &rcv_arg,
             const CryptoDCContext::Ptr &dc_context_delegate,
             const unsigned int key_id,
             const Frame::Ptr &frame)
        : rcv(rcv_arg),
          info(dc_context_delegate, key_id, frame)
    {
    }

    // Initialization

    unsigned int defined() const override
    {
        return CIPHER_DEFINED | HMAC_DEFINED | EXPLICIT_EXIT_NOTIFY_DEFINED;
    }

    void init_cipher(StaticKey &&encrypt_key,
                     StaticKey &&decrypt_key) override
    {
        info.encrypt_cipher = std::move(encrypt_key);
        info.decrypt_cipher = std::move(decrypt_key);
    }

    void init_hmac(StaticKey &&encrypt_key,
                   StaticKey &&decrypt_key) override
    {
        info.encrypt_hmac = std::move(encrypt_key);
        info.decrypt_hmac = std::move(decrypt_key);
    }

    void init_pid(const char *recv_name,
                  const int recv_unit,
                  const SessionStats::Ptr &recv_stats_arg) override
    {
    }

    void init_remote_peer_id(const int remote_peer_id) override
    {
        info.remote_peer_id = remote_peer_id;
    }

    bool consider_compression(const CompressContext &comp_ctx) override
    {
        info.comp_ctx = comp_ctx;
        return false;
    }

    // Rekeying

    void rekey(const RekeyType type) override
    {
        rcv->rekey(type, info);
    }

    void explicit_exit_notify() override
    {
        rcv->explicit_exit_notify();
    }

    // Encrypt/Decrypt -- data channel handled by kernel, so these methods
    // should never be reached.

    // returns true if packet ID is close to wrapping
    bool encrypt(BufferAllocated &buf, const unsigned char *op32) override
    {
        throw korekey_error("encrypt");
    }

    Error::Type decrypt(BufferAllocated &buf, const std::time_t now, const unsigned char *op32) override
    {
        throw korekey_error("decrypt");
    }


  private:
    Receiver::Ptr rcv;
    Info info;
};

class Context : public CryptoDCContext
{
  public:
    Context(CryptoDCSettingsData dc_settings_data,
            CryptoDCFactory &dc_factory_delegate,
            const Receiver::Ptr &rcv_arg,
            const Frame::Ptr &frame_arg)
        : CryptoDCContext(dc_settings_data.key_derivation()),
          rcv(rcv_arg),
          dc_context_delegate(dc_factory_delegate.new_obj(dc_settings_data)),
          frame(frame_arg)
    {
        Key::validate(dc_settings_data.cipher(), dc_settings_data.digest());
    }

    CryptoDCInstance::Ptr new_obj(const unsigned int key_id) override
    {
        return new Instance(rcv, dc_context_delegate, key_id, frame);
    }

    // Info for ProtoContext::options_string
    CryptoDCSettingsData crypto_info() override
    {
        return dc_context_delegate->crypto_info();
    }

    // Info for ProtoContext::link_mtu_adjust

    size_t encap_overhead() const override
    {
        return dc_context_delegate->encap_overhead();
    }

  private:
    Receiver::Ptr rcv;
    CryptoDCContext::Ptr dc_context_delegate;
    Frame::Ptr frame;
};

class Factory : public CryptoDCFactory
{
  public:
    Factory(const CryptoDCFactory::Ptr &dc_factory_delegate_arg,
            const Receiver::Ptr &rcv_arg,
            const Frame::Ptr &frame_arg)
        : dc_factory_delegate(dc_factory_delegate_arg),
          rcv(rcv_arg),
          frame(frame_arg)
    {
    }

    CryptoDCContext::Ptr new_obj(const CryptoDCSettingsData dc_settings_data) override
    {
        return new Context(dc_settings_data, *dc_factory_delegate, rcv, frame);
    }

  private:
    CryptoDCFactory::Ptr dc_factory_delegate;
    Receiver::Ptr rcv;
    Frame::Ptr frame;
};

} // namespace openvpn::KoRekey

#endif
