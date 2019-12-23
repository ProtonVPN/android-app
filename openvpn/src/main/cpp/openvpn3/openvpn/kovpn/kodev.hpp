//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

// OpenVPN 3 kovpn-based tun interface

#ifndef OPENVPN_KOVPN_KODEV_H
#define OPENVPN_KOVPN_KODEV_H

#include <sys/ioctl.h>

#include <string>
#include <cerrno>
#include <cstring>
#include <memory>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/common/valgrind.hpp>
#include <openvpn/time/timestr.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/tun/tunio.hpp>
#include <openvpn/kovpn/kovpn.hpp>
#include <openvpn/kovpn/koroute.hpp>
#include <openvpn/kovpn/kostats.hpp>
#include <openvpn/linux/procfs.hpp>

namespace openvpn {
  namespace KoTun {

    OPENVPN_EXCEPTION(kotun_error);

    struct DevConf
    {
      DevConf()
      {
	std::memset(&dc, 0, sizeof(dc));
      }

      void set_dev_name(const std::string& name)
      {
	if (name.length() < IFNAMSIZ)
	  ::strcpy(dc.dev_name, name.c_str());
	else
	  OPENVPN_THROW(kotun_error, "ovpn dev name too long");
      }

      struct ovpn_dev_init dc;
    };

    // kovpn API methods
    namespace API {

      // Attach UDP socket to ovpn instance
      inline void socket_attach_udp(const int kovpn_fd,
				    const int sock_fd)
      {
	struct ovpn_socket_attach_udp asock;
	asock.fd = sock_fd;
	if (::ioctl(kovpn_fd, OVPN_SOCKET_ATTACH_UDP, &asock) < 0)
	  {
	    const int eno = errno;
	    OPENVPN_THROW(kotun_error, "OVPN_SOCKET_ATTACH_UDP failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	  }
      }

      // New UDP client
      inline int peer_new_udp_client(const int kovpn_fd,
				     int fd,
				     const __u64 notify_per,
				     const unsigned int notify_seconds)
      {
	int peer_id = -1;

	// attach UDP socket fd
	{
	  struct ovpn_socket_attach_udp asock;
	  asock.fd = fd;
	  if (::ioctl(kovpn_fd, OVPN_SOCKET_ATTACH_UDP, &asock) < 0)
	    {
	      const int eno = errno;
	      OPENVPN_THROW(kotun_error, "OVPN_SOCKET_ATTACH_UDP failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	    }
	}

	// get a new Peer ID
	{
	  struct ovpn_peer_new opn;
	  opn.peer_float = OVPN_PF_DISABLED;
	  opn.ovpn_file_bind = true;
	  opn.notify_per = notify_per;
	  opn.notify_seconds = notify_seconds;
	  peer_id = ::ioctl(kovpn_fd, OVPN_PEER_NEW, &opn);
	  if (peer_id < 0)
	    {
	      const int eno = errno;
	      OPENVPN_THROW(kotun_error, "OVPN_PEER_NEW failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	    }
	}

	// set up endpoints for peer
	{
	  struct ovpn_peer_sockaddr_reset psr;
	  std::memset(&psr, 0, sizeof(psr));
	  psr.peer_id = peer_id;
	  psr.fd = fd;
	  if (::ioctl(kovpn_fd, OVPN_PEER_SOCKADDR_RESET, &psr) < 0)
	    {
	      const int eno = errno;
	      OPENVPN_THROW(kotun_error, "OVPN_PEER_SOCKADDR_RESET failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	    }
	}

	return peer_id;
      }

      // Send explicit-exit-notify message to peer
      inline void peer_xmit_explicit_exit_notify(const int kovpn_fd,
						 const int peer_id)
      {
	if (::ioctl(kovpn_fd, OVPN_PEER_XMIT_EXPLICIT_EXIT_NOTIFY, peer_id) < 0)
	  {
	    const int eno = errno;
	    OPENVPN_LOG("kotun: OVPN_PEER_XMIT_EXPLICIT_EXIT_NOTIFY failed, id=" << peer_id << " errno=" << eno << ' ' << KovpnStats::errstr(eno));
	  }
      }

      // Set peer crypto keys
      inline void peer_keys_reset(const int kovpn_fd,
				  const struct ovpn_peer_keys_reset *opk)
      {
	if (::ioctl(kovpn_fd, OVPN_PEER_KEYS_RESET, opk) < 0)
	  {
	    const int eno = errno;
	    OPENVPN_THROW(kotun_error, "OVPN_PEER_KEYS_RESET failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	  }
      }

      // Set keepalive
      inline void peer_set_keepalive(const int kovpn_fd,
				     const struct ovpn_peer_keepalive *ka)
      {
	if (::ioctl(kovpn_fd, OVPN_PEER_KEEPALIVE, ka) < 0)
	  {
	    const int eno = errno;
	    OPENVPN_THROW(kotun_error, "OVPN_PEER_KEEPALIVE failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	  }
      }

      // Add routes
      inline void peer_add_routes(const int kovpn_fd,
				  const int peer_id,
				  const std::vector<IP::Route>& rtvec)
      {
	std::unique_ptr<struct ovpn_route[]> routes(KoRoute::from_routes(rtvec));
	struct ovpn_peer_routes_add r;
	r.peer_id = peer_id;
	r.usurp = true;
	r.n_routes = rtvec.size();
	r.routes = routes.get();
	if (::ioctl(kovpn_fd, OVPN_PEER_ROUTES_ADD, &r) < 0)
	  {
	    const int eno = errno;
	    OPENVPN_THROW(kotun_error, "OVPN_PEER_ROUTES_ADD failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	  }
      }

      // Get status info
      inline bool peer_get_status(const int kovpn_fd,
				  const struct ovpn_peer_status* ops)
      {
	if (::ioctl(kovpn_fd, OVPN_PEER_STATUS, ops) >= 0)
	  {
	    OPENVPN_MAKE_MEM_DEFINED(ops, sizeof(*ops));
	    return true;
	  }
	else
	  {
	    const int eno = errno;
	    OPENVPN_LOG("kotun: OVPN_PEER_STATUS failed, errno=" << eno << ' ' << KovpnStats::errstr(eno));
	    return false;
	  }
      }
    }

    struct PacketFrom
    {
      typedef std::unique_ptr<PacketFrom> SPtr;
      BufferAllocated buf;
    };

    class KovpnBase
    {
    public:
      static ScopedFD open_kovpn(DevConf& devconf,
				 KovpnStats* kovpn_stats,
				 bool* first)
      {
	if (first)
	  *first = false;

	// Open kovpn device
	static const char node[] = "/dev/net/ovpn";
	ScopedFD fd(open(node, O_RDWR));
	if (!fd.defined())
	  {
	    const int eno = errno;
	    OPENVPN_THROW(kotun_error, "error opening ovpn tunnel device " << node << ": " << strerror_str(eno));
	  }

	// Check kovpn version
	const int ver_packed = ::ioctl(fd(), OVPN_GET_VERSION, nullptr);
	if (ver_packed < 0)
	  OPENVPN_THROW(kotun_error, "OVPN_GET_VERSION failed");
	if (ver_major(ver_packed) != OVPN_VER_MAJOR
	    || ver_minor(ver_packed) != OVPN_VER_MINOR) 
	  OPENVPN_THROW(kotun_error, "version mismatch, pg=" << ver_string() << " installed=" << ver_string(ver_packed));

	// Configure tun
	const int status = ::ioctl(fd(), OVPN_DEV_INIT, &devconf.dc);
	if (status < 0)
	  {
	    const int eno = errno;
	    OPENVPN_THROW(kotun_error, "OVPN_DEV_INIT failed: " << KovpnStats::errstr(eno));
	  }

	if (devconf.dc.expire)
	  OPENVPN_LOG("NOTE: this evaluation build expires on " << date_time(devconf.dc.expire));

	if (status == 1)
	  {
	    if (kovpn_stats)
	      kovpn_stats->set_fd(fd());
	    if (first)
	      *first = true;
	    OPENVPN_LOG("KVER pg=" << ver_string() << " installed=" << ver_string(ver_packed));
	    OPENVPN_LOG("IE_NAT=" << devconf.dc.ie_nat);
	  }

	return fd;
      }

      static void set_rps_xps(const std::string& dev_name, const unsigned int dev_queue_index, Stop* async_stop)
      {
	// set RPS/XPS on iface
	ProcFS::write_sys(fmt_qfn(dev_name, "rx", dev_queue_index, "rps_cpus"), "ffffffff\n", async_stop);
	ProcFS::write_sys(fmt_qfn(dev_name, "rx", dev_queue_index, "rps_cpus"), "ffffffff\n", async_stop);
	ProcFS::write_sys(fmt_qfn(dev_name, "rx", dev_queue_index, "rps_flow_cnt"), "1024\n", async_stop);
	ProcFS::write_sys(fmt_qfn(dev_name, "tx", dev_queue_index, "xps_cpus"), "0\n", async_stop);
      }

      static void disable_reverse_path_filter(const std::string& dev_name, Stop* async_stop)
      {
	// disable reverse path filter on iface
	IPv4ReversePathFilter::write(dev_name, 0, async_stop);
      }

    protected:
      static int ver_major(const int ver_packed)
      {
	return (ver_packed >> 16) & 0xFF;
      }

      static int ver_minor(const int ver_packed)
      {
	return (ver_packed >> 8) & 0xFF;
      }

      static int ver_build(const int ver_packed)
      {
	return ver_packed & 0xFF;
      }

      static std::string ver_string(const int major, const int minor, const int build)
      {
	return std::to_string(major) + '.' + std::to_string(minor) + '.' + std::to_string(build);
      }

      static std::string ver_string(const int ver_packed)
      {
	return ver_string(ver_major(ver_packed), ver_minor(ver_packed), ver_build(ver_packed));
      }

      static std::string ver_string()
      {
	return ver_string(OVPN_VER_MAJOR, OVPN_VER_MINOR, OVPN_VER_BUILD);
      }

      static std::string fmt_qfn(const std::string& dev, const std::string& type, int qnum, const std::string& bn)
      {
	std::ostringstream os;
	os << "/sys/class/net/" << dev << "/queues/" << type << "-" << qnum << '/' << bn;
	return os.str();
      }
    };

    template <typename ReadHandler>
    struct TunClient : public TunIO<ReadHandler, PacketFrom, openvpn_io::posix::stream_descriptor>, public virtual KovpnBase
    {
      typedef TunIO<ReadHandler, PacketFrom, openvpn_io::posix::stream_descriptor> Base;
      typedef RCPtr<TunClient> Ptr;

      // constructed by start() in in koudp.c/kotcp.c
      TunClient(openvpn_io::io_context& io_context,
	  DevConf& devconf,
	  ReadHandler read_handler,
	  const Frame::Ptr& frame,
	  KovpnStats* kovpn_stats, // not persisted
	  bool *first)
	: Base(read_handler, frame, SessionStats::Ptr())
      {
	ScopedFD fd(open_kovpn(devconf, kovpn_stats, first));
	Base::name_ = devconf.dc.dev_name;
	Base::stream = new openvpn_io::posix::stream_descriptor(io_context, fd.release());
      }

      // Attach UDP socket to ovpn instance
      void socket_attach_udp(const int sock_fd)
      {
	API::socket_attach_udp(native_handle(), sock_fd);
      }

      // New UDP client (used by dcocli)
      int peer_new_udp_client(int fd,
			      const __u64 notify_per,
			      const unsigned int notify_seconds)
      {
	return API::peer_new_udp_client(native_handle(), fd, notify_per, notify_seconds);
      }

      // Add routes (used by dcocli)
      void peer_add_routes(const int peer_id,
			   const std::vector<IP::Route>& rtvec)
      {
	API::peer_add_routes(native_handle(), peer_id, rtvec);
      }

      // Send explicit-exit-notify message to peer
      void peer_xmit_explicit_exit_notify(const int peer_id)
      {
	API::peer_xmit_explicit_exit_notify(native_handle(), peer_id);
      }

      // Set peer crypto keys
      void peer_keys_reset(const struct ovpn_peer_keys_reset *opk)
      {
	API::peer_keys_reset(native_handle(), opk);
      }

      // Set keepalive
      void peer_set_keepalive(const struct ovpn_peer_keepalive *ka)
      {
	API::peer_set_keepalive(native_handle(), ka);
      }

      // Get status info
      bool peer_get_status(struct ovpn_peer_status* ops)
      {
	return API::peer_get_status(native_handle(), ops);
      }

      // Return kovpn fd
      int native_handle() const
      {
	return Base::stream->native_handle();
      }
    };

  }
}

#endif
