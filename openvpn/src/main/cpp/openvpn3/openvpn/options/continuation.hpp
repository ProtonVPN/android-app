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

// Client-side code to handle pushed option list "continuations".
// This is where multiple option lists are pushed by the server,
// if an option list doesn't fit into the standard 1024 byte buffer.
// This class will aggregate the options.

#pragma once

#include <unordered_set>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn {

struct PushOptionsBase : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<PushOptionsBase> Ptr;

    OptionList merge;
    OptionList multi;
    OptionList singleton;
};

// Used by OptionListContinuation::finalize() to merge static and pushed options
struct PushOptionsMerger : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<PushOptionsMerger> Ptr;
    virtual void merge(OptionList &pushed, const OptionList &config) const = 0;
};

// Aggregate pushed option continuations into a singular option list.
// Note that map is not updated until list is complete.
class OptionListContinuation : public OptionList
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(olc_complete); // add called when object is already complete
    OPENVPN_EXCEPTION(push_update_unsupported_option);

    OptionListContinuation(const PushOptionsBase::Ptr &push_base_arg)
        : push_base(push_base_arg)
    {
        // Prepend from base where multiple options of the same type can aggregate,
        // so that server-pushed options will be at the end of list.
        if (push_base)
            extend(push_base->multi, nullptr);
    }

    OptionListContinuation() = default;

    /**
     * Processes pushed list of options from PUSH_REPLY or PUSH_UPDATE.
     *
     * For PUSH_REPLY, all incoming options are added subject to filter.
     *
     * For PUSH_UPDATE, incoming options prefixed with "-" do remove current options.
     * If some options cannot be updated, exception is thrown. Incoming options
     * prefixed with "?" are considered optional and might be ignored if update is not
     * supported.
     *
     * @param other Incoming list of options
     * @param filt Options filter
     * @param push_update true if this is PUSH_UPDATE, false if PUSH_REPLY
     */
    void add(const OptionList &other, OptionList::FilterBase *filt, bool push_update = false)
    {
        if (complete_)
        {
            throw olc_complete();
        }

        OptionList opts{other};
        if (push_update)
        {
            update(opts);
        }

        partial_ = true;
        try
        {
            // throws if pull-filter rejects
            extend(opts, filt);
        }
        catch (const Option::RejectedException &)
        {
            // remove all server pushed options on reject
            clear();
            if (push_base)
                extend(push_base->multi, nullptr);
            throw;
        }
        if (!continuation(opts))
        {
            if (push_base)
            {
                // Append from base where only a single instance of each option makes sense,
                // provided that option wasn't already pushed by server.
                update_map();
                extend_nonexistent(push_base->singleton);
            }
            update_map();
            complete_ = true;
        }
    }

    void finalize(const PushOptionsMerger::Ptr merger)
    {
        if (merger)
        {
            merger->merge(*this, push_base->merge);
            update_map();
        }

        update_list.clear();
    }

    // returns true if add() was called at least once
    bool partial() const
    {
        return partial_;
    }

    // returns true if option list is complete
    bool complete() const
    {
        return complete_;
    }

    /**
     * @brief Resets completion flag. Intended to use by PUSH_UPDATE.
     *
     */
    void reset_completion()
    {
        complete_ = false;
    }

  private:
    /**
     * Handles PUSH_UPDATE options
     *
     * This method:
     * - throws an exception if an option in the list doesn't support PUSH_UPDATE
     * - removes an original option which is prefixed with "-" in incoming options list
     * - removes an original option with the same name as incoming options list
     *
     * Note that options prefixed with "-" are removed from incoming options list
     *
     * @param opts A list of PUSH_UPDATE options.
     */
    void update(OptionList &opts)
    {
        std::unordered_set<std::string> opts_to_remove;
        std::unordered_set<std::string> unsupported_mandatory_options;
        std::unordered_set<std::string> unsupported_optional_options;

        for (auto it = opts.begin(); it != opts.end();)
        {
            std::string &name = it->ref(0);

            // option prefixed with "-" should be removed
            bool remove = string::starts_with(name, "-");
            if (remove)
            {
                name.erase(name.begin());
            }

            // option prefixed with "?" is considered "optional"
            bool optional = string::starts_with(name, "?");
            if (optional)
            {
                name.erase(name.begin());
            }

            if (updatable_options.find(name) == updatable_options.end())
            {
                if (optional)
                {
                    unsupported_optional_options.insert(name);
                }
                else
                {
                    unsupported_mandatory_options.insert(name);
                }
            }

            if (remove)
            {
                // remove current option if it is prefixed with "-" in update list
                opts_to_remove.insert(name);
                it = opts.erase(it);
            }
            else
            {
                // if upcoming updated option is not in update list, it should be removed from current options
                if (update_list.find(name) == update_list.end())
                {
                    opts_to_remove.insert(name);
                }

                ++it;
            }
        }
        opts.update_map();

        erase(std::remove_if(begin(), end(), [&opts_to_remove](const Option &o)
                             {
            const std::string &name = o.ref(0);
            return opts_to_remove.find(name) != opts_to_remove.end(); }),
              end());

        // we need to remove only original options, not the ones from ongoing PUSH_UPDATE
        // make sure that options are considered for removal only once
        update_list.insert(opts_to_remove.begin(), opts_to_remove.end());

        if (!unsupported_mandatory_options.empty())
        {
            throw push_update_unsupported_option(string::join(unsupported_mandatory_options, ","));
        }

        if (!unsupported_optional_options.empty())
        {
            OPENVPN_LOG("Unsupported optional options: " << string::join(unsupported_optional_options, ","));
        }
    }

    static bool continuation(const OptionList &opt)
    {
        const Option *o = opt.get_ptr("push-continuation");
        return o && o->size() >= 2 && o->ref(1) == "2";
    }

    bool partial_ = false;
    bool complete_ = false;

    PushOptionsBase::Ptr push_base;

    /**
     * @brief A list of options to be updated or deleted during the update process.
     * Existing options with the same name as in PUSH_UPDATE are replaced, and the ones
     * prefixed with "-" in PUSH_UPDATE are deleted.
     */
    std::unordered_set<std::string> update_list;

    inline static std::unordered_set<std::string> updatable_options = {
        "block-ipv4",
        "block-ipv6",
        "block-outside-dns",
        "dhcp-options",
        "dns",
        "ifconfig",
        "ifconfig-ipv6",
        "push-continuation",
        "redirect-gateway",
        "redirect-private",
        "route",
        "route-gateway",
        "route-ipv6",
        "route-metric",
        "topology",
        "tun-mtu"};
};

} // namespace openvpn
