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

#ifndef OPENVPN_ASIO_ASIOCONTEXT_H
#define OPENVPN_ASIO_ASIOCONTEXT_H

#include <vector>
#include <memory>
#include <mutex>

#include <openvpn/io/io.hpp>

namespace openvpn {
class AsioContextStore
{
  public:
    openvpn_io::io_context &new_context(int concurrency_hint)
    {
        openvpn_io::io_context *ioc = new openvpn_io::io_context(concurrency_hint);
        {
            std::lock_guard lock(mutex);
            contexts.emplace_back(ioc);
        }
        return *ioc;
    }

    /**
     *  This is to be used only as a last resort. The proper way to end an
     *  io_context-driven thread is to simply stop scheduling work on the reactor
     *  and exit gracefully. DO NOT USE THIS IF THERE'S AN ALTERNATIVE!
     */
    void stop()
    {
        std::lock_guard lock(mutex);

        for (auto &context : contexts)
            context->stop();
    }

  private:
    std::mutex mutex;
    std::vector<std::unique_ptr<openvpn_io::io_context>> contexts;
};
} // namespace openvpn

#endif
