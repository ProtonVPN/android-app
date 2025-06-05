//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2025- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#include <openvpn/io/io.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn {

/**
 * @brief Wrapper for an asynchronous handle supporting cancellation and closure.
 *
 * Automatically manages lifecycle by canceling and closing the handle if not already done.
 */
class CancelableHandle
{
  public:
    /**
     * @brief Constructs with the given I/O context.
     * @param io_context I/O context for asynchronous operations.
     */
    CancelableHandle(openvpn_io::io_context &io_context)
        : handle_(io_context)
    {
    }

    /** @brief Destructor ensures handle cancellation and closure. */
    ~CancelableHandle()
    {
        cancel_and_close();
    }

    /**
     * @brief Checks if the handle's event is already signaled.
     * @throws Exception if event is signaled or abandoned, or on WaitForSingleObject failure.
     */
    void check_is_already_signalled()
    {
        DWORD status = ::WaitForSingleObject(handle_.native_handle(), 0);
        const Win::LastError err;
        switch (status)
        {
        case WAIT_TIMEOUT: // expected status
            break;
        case WAIT_OBJECT_0:
            throw Exception("CancelableHandle: destroy event is already signaled");
        case WAIT_ABANDONED:
            throw Exception("CancelableHandle: destroy event is abandoned");
        default:
            OPENVPN_THROW_EXCEPTION("CancelableHandle: WaitForSingleObject failed: " << err.message());
        }
    }

    /** @brief Cancels and closes the handle if not already closed. */
    void cancel_and_close()
    {
        if (!*is_closed_)
        {
            *is_closed_ = true;
            try
            {
                handle_.cancel();
            }
            catch (const openvpn_io::system_error &)
            {
                // this is fine, handle is likely not yet initialized
            }

            handle_.close();
        }
    }

    /**
     * @brief Assigns a native Windows handle.
     * @param handle The native HANDLE to manage.
     */
    void assign(HANDLE handle)
    {
        handle_.assign(handle);
        is_closed_ = std::make_shared<bool>(false);
    }

    /**
     * @brief Initiates an asynchronous wait on the handle.
     * @param handler Handler executed on completion, unless closed.
     */
    template <typename Handler>
    void async_wait(Handler &&handler)
    {
        handle_.async_wait([is_closed = is_closed_, handler = std::forward<Handler>(handler)](const openvpn_io::error_code &ec)
                           {
            if (!*is_closed)
            {
                handler(ec);
            } });
    }

  private:
    openvpn_io::windows::object_handle handle_;                       ///< Asynchronous Windows object handle.
    std::shared_ptr<bool> is_closed_ = std::make_shared<bool>(false); ///< Indicates if handle is closed.
};

} // namespace openvpn
