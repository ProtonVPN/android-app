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

// Client-side code to handle pushed option list "continuations".
// This is where multiple option lists are pushed by the server,
// if an option list doesn't fit into the standard 1024 byte buffer.
// This class will aggregate the options.

#pragma once

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

    OptionListContinuation(const PushOptionsBase::Ptr &push_base_arg)
        : push_base(push_base_arg)
    {
        // Prepend from base where multiple options of the same type can aggregate,
        // so that server-pushed options will be at the end of list.
        if (push_base)
            extend(push_base->multi, nullptr);
    }

    OptionListContinuation()
    {
    }

    // call with option list fragments
    void add(const OptionList &other, OptionList::FilterBase *filt)
    {
        if (!complete_)
        {
            partial_ = true;
            try
            {
                // throws if pull-filter rejects
                extend(other, filt);
            }
            catch (const Option::RejectedException &e)
            {
                // remove all server pushed options on reject
                clear();
                if (push_base)
                    extend(push_base->multi, nullptr);
                throw;
            }
            if (!continuation(other))
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
        else
            throw olc_complete();
    }

    void finalize(const PushOptionsMerger::Ptr merger)
    {
        if (merger)
        {
            merger->merge(*this, push_base->merge);
            update_map();
        }
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

  private:
    static bool continuation(const OptionList &opt)
    {
        const Option *o = opt.get_ptr("push-continuation");
        return o && o->size() >= 2 && o->ref(1) == "2";
    }

    bool partial_ = false;
    bool complete_ = false;

    PushOptionsBase::Ptr push_base;
};

} // namespace openvpn
