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

#ifndef OPENVPN_TUN_PERSIST_TUNPERSIST_H
#define OPENVPN_TUN_PERSIST_TUNPERSIST_H

#include <openvpn/common/size.hpp>
#include <openvpn/tun/persist/tunwrap.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/tun/builder/capture.hpp>

namespace openvpn {

// TunPersistTemplate adds persistence capabilities onto TunWrapTemplate,
// in order to implement logic for the persist-tun directive.
template <typename SCOPED_OBJ, typename STATE = TunProp::State::Ptr>
class TunPersistTemplate : public TunWrapTemplate<SCOPED_OBJ>
{
  public:
    typedef RCPtr<TunPersistTemplate> Ptr;

    TunPersistTemplate(const bool enable_persistence, const TunWrapObjRetain retain_obj, TunBuilderBase *tb)
        : TunWrapTemplate<SCOPED_OBJ>(retain_obj),
          enable_persistence_(enable_persistence),
          tb_(tb),
          use_persisted_tun_(false),
          disconnect(false)
    {
    }

    // Current persisted state
    const STATE &state() const
    {
        return state_;
    }

    virtual ~TunPersistTemplate()
    {
        close_local();
    }

    void invalidate()
    {
        options_.clear();
    }

    void close()
    {
        close_local();
        TunWrapTemplate<SCOPED_OBJ>::close();
    }

    void set_disconnect()
    {
        disconnect = true;
    }

    // Current persisted options
    const std::string &options()
    {
        return options_;
    }

    // Return true if we should use previously persisted
    // tun socket descriptor/handle
    bool use_persisted_tun(const IP::Addr server_addr,
                           const TunProp::Config &tun_prop,
                           const OptionList &opt)
    {
#if OPENVPN_DEBUG_TUN_BUILDER > 0
        {
            TunBuilderCapture::Ptr capture = new TunBuilderCapture();
            try
            {
                TunProp::configure_builder(capture.get(), nullptr, nullptr, server_addr, tun_prop, opt, nullptr, true);
                OPENVPN_LOG("*** TUN BUILDER CAPTURE" << std::endl
                                                      << capture->to_string());
            }
            catch (const std::exception &e)
            {
                OPENVPN_LOG("*** TUN BUILDER CAPTURE exception: " << e.what());
            }
        }
#endif

        // In tun_persist mode, capture tun builder settings so we can
        // compare them to previous persisted settings.
        if (enable_persistence_)
        {
            copt_.reset(new TunBuilderCapture());
            try
            {
                TunProp::configure_builder(copt_.get(), nullptr, nullptr, server_addr, tun_prop, opt, nullptr, true);
            }
            catch (const std::exception &)
            {
                copt_.reset();
            }
        }

        // Check if previous tun session matches properties of to-be-created session
        use_persisted_tun_ = (TunWrapTemplate<SCOPED_OBJ>::obj_defined()
                              && copt_
                              && !options_.empty()
                              && options_ == copt_->to_string()
                              && (tb_ ? tb_->tun_builder_persist() : true));
        return use_persisted_tun_;
    }

    // Possibly save tunnel fd/handle, state, and options.
    bool persist_tun_state(const typename SCOPED_OBJ::base_type obj,
                           const STATE &state,
                           bool save_replace_sock = true)
    {
        if ((!enable_persistence_ || !use_persisted_tun_) && save_replace_sock)
        {
            TunWrapTemplate<SCOPED_OBJ>::save_replace_sock(obj);
        }
        if (enable_persistence_ && copt_ && !use_persisted_tun_)
        {
            state_ = state;
            options_ = copt_->to_string();
            return true;
        }
        else
            return false;
    }

  private:
    void close_local()
    {
        if (tb_)
            tb_->tun_builder_teardown(disconnect);
        state_.reset();
        options_ = "";
    }

    const bool enable_persistence_;
    TunBuilderBase *const tb_;
    STATE state_;
    std::string options_;

    TunBuilderCapture::Ptr copt_;
    bool use_persisted_tun_;

    bool disconnect;
};

} // namespace openvpn
#endif
