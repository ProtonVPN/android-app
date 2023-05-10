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
//

#pragma once

#include <windows.h>

#include <string>
#include <type_traits>

#include <openvpn/buffer/bufhex.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/event.hpp>

#define TUN_IOCTL_REGISTER_RINGS CTL_CODE(51820U, 0x970U, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA)
#define TUN_IOCTL_FORCE_CLOSE_HANDLES CTL_CODE(51820U, 0x971U, METHOD_NEITHER, FILE_READ_DATA | FILE_WRITE_DATA)

#define WINTUN_RING_CAPACITY 0x800000
#define WINTUN_RING_TRAILING_BYTES 0x10000
#define WINTUN_RING_FRAMING_SIZE 12
#define WINTUN_MAX_PACKET_SIZE 0xffff
#define WINTUN_PACKET_ALIGN 4

namespace openvpn {
namespace TunWin {
struct TUN_RING
{
    std::atomic_ulong head;
    std::atomic_ulong tail;
    std::atomic_long alertable;
    UCHAR data[WINTUN_RING_CAPACITY + WINTUN_RING_TRAILING_BYTES + WINTUN_RING_FRAMING_SIZE];
};

struct TUN_REGISTER_RINGS
{
    struct
    {
        ULONG ring_size;
        TUN_RING *ring;
        HANDLE tail_moved;
    } send, receive;
};

typedef openvpn_io::windows::object_handle AsioEvent;

class RingBuffer : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<RingBuffer> Ptr;

    RingBuffer(openvpn_io::io_context &io_context)
        : send_ring_hmem(CreateFileMapping(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, sizeof(TUN_RING), NULL)),
          receive_ring_hmem(CreateFileMapping(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, sizeof(TUN_RING), NULL)),
          send_tail_moved_asio_event_(io_context)
    {
        // sanity checks
        static_assert((sizeof(TUN_RING) - sizeof(TUN_RING::data)) == 12, "sizeof(TUN_RING) is expected to be 12");
#if !defined(ATOMIC_LONG_LOCK_FREE) || (ATOMIC_LONG_LOCK_FREE != 2)
#error Atomic long is expected to be always lock-free
#endif

        send_ring_ = (TUN_RING *)MapViewOfFile(send_ring_hmem(), FILE_MAP_ALL_ACCESS, 0, 0, sizeof(TUN_RING));
        receive_ring_ = (TUN_RING *)MapViewOfFile(receive_ring_hmem(), FILE_MAP_ALL_ACCESS, 0, 0, sizeof(TUN_RING));

        HANDLE handle;
        DuplicateHandle(GetCurrentProcess(),
                        send_ring_tail_moved_(),
                        GetCurrentProcess(),
                        &handle,
                        0,
                        FALSE,
                        DUPLICATE_SAME_ACCESS);
        send_tail_moved_asio_event_.assign(handle);
    }

    RingBuffer(openvpn_io::io_context &io_context,
               HANDLE client_process,
               const std::string &send_ring_hmem_hex,
               const std::string &receive_ring_hmem_hex,
               const std::string &send_ring_tail_moved_hex,
               const std::string &receive_ring_tail_moved_hex)
        : send_tail_moved_asio_event_(io_context)
    {
        HANDLE remote_handle = BufHex::parse<HANDLE>(send_ring_hmem_hex, "send_ring_hmem");
        HANDLE handle;
        DuplicateHandle(client_process, remote_handle, GetCurrentProcess(), &handle, 0, FALSE, DUPLICATE_SAME_ACCESS);
        send_ring_hmem.reset(handle);

        remote_handle = BufHex::parse<HANDLE>(receive_ring_hmem_hex, "receive_ring_hmem");
        DuplicateHandle(client_process, remote_handle, GetCurrentProcess(), &handle, 0, FALSE, DUPLICATE_SAME_ACCESS);
        receive_ring_hmem.reset(handle);

        remote_handle = BufHex::parse<HANDLE>(send_ring_tail_moved_hex, "send_ring_tail_moved");
        DuplicateHandle(client_process, remote_handle, GetCurrentProcess(), &handle, 0, FALSE, DUPLICATE_SAME_ACCESS);
        send_ring_tail_moved_.reset(handle);

        remote_handle = BufHex::parse<HANDLE>(receive_ring_tail_moved_hex, "receive_ring_tail_moved");
        DuplicateHandle(client_process, remote_handle, GetCurrentProcess(), &handle, 0, FALSE, DUPLICATE_SAME_ACCESS);
        receive_ring_tail_moved_.reset(handle);

        send_ring_ = (TUN_RING *)MapViewOfFile(send_ring_hmem(), FILE_MAP_ALL_ACCESS, 0, 0, sizeof(TUN_RING));
        receive_ring_ = (TUN_RING *)MapViewOfFile(receive_ring_hmem(), FILE_MAP_ALL_ACCESS, 0, 0, sizeof(TUN_RING));
    }

    RingBuffer(RingBuffer const &) = delete;
    RingBuffer &operator=(RingBuffer const &) = delete;

    ~RingBuffer()
    {
        UnmapViewOfFile(send_ring_);
        UnmapViewOfFile(receive_ring_);
    }

    HANDLE send_ring_tail_moved()
    {
        return send_ring_tail_moved_();
    }

    HANDLE receive_ring_tail_moved()
    {
        return receive_ring_tail_moved_();
    }

    TUN_RING *send_ring()
    {
        return send_ring_;
    }

    TUN_RING *receive_ring()
    {
        return receive_ring_;
    }

    AsioEvent &send_tail_moved_asio_event()
    {
        return send_tail_moved_asio_event_;
    }

#ifdef HAVE_JSON
    void serialize(Json::Value &json)
    {
        json["send_ring_hmem"] = BufHex::render(send_ring_hmem());
        json["receive_ring_hmem"] = BufHex::render(receive_ring_hmem());
        json["send_ring_tail_moved"] = BufHex::render(send_ring_tail_moved());
        json["receive_ring_tail_moved"] = BufHex::render(receive_ring_tail_moved());
    }
#endif

  protected:
    Win::ScopedHANDLE send_ring_hmem;
    Win::ScopedHANDLE receive_ring_hmem;
    Win::Event send_ring_tail_moved_{FALSE};
    Win::Event receive_ring_tail_moved_{FALSE};
    AsioEvent send_tail_moved_asio_event_;

    TUN_RING *send_ring_ = nullptr;
    TUN_RING *receive_ring_ = nullptr;
};
} // namespace TunWin
} // namespace openvpn
