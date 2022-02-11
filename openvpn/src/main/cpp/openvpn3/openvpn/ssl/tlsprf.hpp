//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// Implement the TLS-PRF function, used by ProtoContext.

#ifndef OPENVPN_SSL_TLSPRF_H
#define OPENVPN_SSL_TLSPRF_H

#include <cstring> // for std::strlen and others

#include <string>
#include <sstream>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/buffer/bufcomplete.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/ssl/psid.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

  template <typename CRYPTO_API>
  class TLSPRF
  {
  public:
    OPENVPN_SIMPLE_EXCEPTION(tlsprf_uninitialized);
    OPENVPN_SIMPLE_EXCEPTION(tlsprf_client_server_mismatch);
    OPENVPN_SIMPLE_EXCEPTION(tlsprf_tlsprf_failed);

    TLSPRF(const bool server)
      : initialized_(false), server_(server) {}

    void randomize(RandomAPI& rng)
    {
      rng.assert_crypto();
      if (!server_)
	rng.rand_bytes(pre_master, sizeof(pre_master));
      rng.rand_bytes(random1, sizeof(random1));
      rng.rand_bytes(random2, sizeof(random2));
      initialized_ = true;
    }

    void read(Buffer& buf)
    {
      if (!server_)
	buf.read(pre_master, sizeof(pre_master));
      buf.read(random1, sizeof(random1));
      buf.read(random2, sizeof(random2));
      initialized_ = true;
    }

    bool read_complete(BufferComplete& bc)
    {
      size_t need = sizeof(random1) + sizeof(random2);
      if (!server_)
	need += sizeof(pre_master);
      if (!bc.advance(need))
	return false;
      return true;
    }

    void write(Buffer& buf)
    {
      verify_initialized();
      if (!server_)
	buf.write(pre_master, sizeof(pre_master));
      buf.write(random1, sizeof(random1));
      buf.write(random2, sizeof(random2));
    }

    void generate_key_expansion(OpenVPNStaticKey& dest, const TLSPRF& peer,
				const ProtoSessionID& psid_self, const ProtoSessionID& psid_peer) const
    {
      if (server_ == peer.server_)
	throw tlsprf_client_server_mismatch();
      if (server_)
	gen_exp(dest, peer, psid_peer, *this, psid_self);
      else
	gen_exp(dest, *this, psid_self, peer, psid_peer);
    }

    void erase()
    {
      if (initialized_)
	{
	  if (!server_)
	    std::memset(pre_master, 0, sizeof(pre_master));
	  std::memset(random1, 0, sizeof(random1));
	  std::memset(random2, 0, sizeof(random2));
	  initialized_ = false;
	}
    }

    std::string dump(const char *title)
    {
      std::ostringstream out;
      out << "*** TLSPRF " << title << " pre_master: " << render_hex(pre_master, sizeof(pre_master)) << std::endl;
      out << "*** TLSPRF " << title << " random1: " << render_hex(random1, sizeof(random1)) << std::endl;
      out << "*** TLSPRF " << title << " random2: " << render_hex(random2, sizeof(random2)) << std::endl;
      return out.str();
    }

    ~TLSPRF()
    {
      erase();
    }

    static void openvpn_PRF (const unsigned char *secret,
			     const size_t secret_len,
			     const char *label,
			     const unsigned char *client_seed,
			     const size_t client_seed_len,
			     const unsigned char *server_seed,
			     const size_t server_seed_len,
			     const ProtoSessionID* client_sid,
			     const ProtoSessionID* server_sid,
			     unsigned char *output,
			     const size_t output_len)
    {
      const size_t label_len = std::strlen(label);
      BufferAllocated seed(label_len
			   + client_seed_len
			   + server_seed_len
			   + ProtoSessionID::SIZE * 2,
			   BufferAllocated::DESTRUCT_ZERO);
      seed.write((unsigned char *)label, label_len);
      seed.write(client_seed, client_seed_len);
      seed.write(server_seed, server_seed_len);
      if (client_sid)
	client_sid->write(seed);
      if (server_sid)
	server_sid->write(seed);

      // compute PRF
      if(!CRYPTO_API::TLS1PRF::PRF(seed.data(), seed.size(), secret, secret_len, output, output_len))
	throw tlsprf_tlsprf_failed();
    }

  private:
    static void gen_exp(OpenVPNStaticKey& dest, const TLSPRF& client, const ProtoSessionID& psid_client,
			const TLSPRF& server, const ProtoSessionID& psid_server)
    {
      static const char master_secret_id[] = "OpenVPN master secret";
      static const char key_expansion_id[] = "OpenVPN key expansion";

      unsigned char master[48];

      client.verify_initialized();
      server.verify_initialized();

      // compute master secret
      openvpn_PRF (client.pre_master,
		   sizeof(client.pre_master),
		   master_secret_id,
		   client.random1,
		   sizeof(client.random1),
		   server.random1,
		   sizeof(server.random1),
		   nullptr,
		   nullptr,
		   master,
		   sizeof(master));
  
      // compute key expansion */
      openvpn_PRF (master,
		   sizeof(master),
		   key_expansion_id,
		   client.random2,
		   sizeof(client.random2),
		   server.random2,
		   sizeof(server.random2),
		   &psid_client,
		   &psid_server,
		   dest.raw_alloc(),
		   OpenVPNStaticKey::KEY_SIZE);

      std::memset(master, 0, sizeof(master));
    }

    void verify_initialized() const
    {
      if (!initialized_)
	throw tlsprf_uninitialized();
    }

    bool initialized_;
    bool server_;
    unsigned char pre_master[48]; // client generated
    unsigned char random1[32];    // generated by both client and server
    unsigned char random2[32];    // generated by both client and server
  };

  // TLSPRF wrapper API using dynamic polymorphism

  class TLSPRFInstance : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<TLSPRFInstance> Ptr;

    virtual void self_randomize(RandomAPI& rng) = 0;
    virtual void self_write(Buffer& buf) = 0;
    virtual void peer_read(Buffer& buf) = 0;
    virtual bool peer_read_complete(BufferComplete& bc) = 0;
    virtual void erase() = 0;

    virtual void generate_key_expansion(OpenVPNStaticKey& dest,
					const ProtoSessionID& psid_self,
					const ProtoSessionID& psid_peer) const = 0;
  };

  class TLSPRFFactory : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<TLSPRFFactory> Ptr;

    virtual TLSPRFInstance::Ptr new_obj(const bool self_is_server) = 0;
  };

  // TLSPRF wrapper implementation using dynamic polymorphism

  template <typename CRYPTO_API>
  class CryptoTLSPRFInstance : public TLSPRFInstance
  {
  public:
    CryptoTLSPRFInstance(const bool self_is_server)
      : self(self_is_server),
	peer(!self_is_server)
    {
    }

    virtual void self_randomize(RandomAPI& rng)
    {
      self.randomize(rng);
    }

    virtual void self_write(Buffer& buf)
    {
      self.write(buf);
    }

    virtual void peer_read(Buffer& buf)
    {
      peer.read(buf);
    }

    virtual bool peer_read_complete(BufferComplete& bc)
    {
      return peer.read_complete(bc);
    }

    virtual void erase()
    {
      self.erase();
      peer.erase();
    }

    virtual void generate_key_expansion(OpenVPNStaticKey& dest,
					const ProtoSessionID& psid_self,
					const ProtoSessionID& psid_peer) const
    {
      self.generate_key_expansion(dest, peer, psid_self, psid_peer);
    }

  private:
    TLSPRF<CRYPTO_API> self;
    TLSPRF<CRYPTO_API> peer;
  };

  template <typename CRYPTO_API>
  class CryptoTLSPRFFactory : public TLSPRFFactory
  {
  public:
    virtual TLSPRFInstance::Ptr new_obj(const bool self_is_server)
    {
      return new CryptoTLSPRFInstance<CRYPTO_API>(self_is_server);
    }
  };

} // namespace openvpn

#endif // OPENVPN_SSL_TLSPRF_H
