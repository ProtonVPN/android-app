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

/**
   @file
   @brief Unit test for OpenVPN Protocol implementation (class ProtoContext)
*/

#include "test_common.hpp"

#include <iostream>
#include <string>
#include <sstream>
#include <deque>
#include <algorithm>
#include <cstring>
#include <limits>
#include <thread>

#include <gmock/gmock.h>
#include <openvpn/common/platform.hpp>
#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/client/cliproto.hpp>


#define OPENVPN_DEBUG

#if !defined(USE_TLS_AUTH) && !defined(USE_TLS_CRYPT)
// #define USE_TLS_AUTH
// #define USE_TLS_CRYPT
#define USE_TLS_CRYPT_V2
#endif

// Data limits for Blowfish and other 64-bit block-size ciphers
#ifndef BF
#define BF 0
#endif
#if BF == 1
#define PROTO_CIPHER "BF-CBC"
#define TLS_VER_MIN TLSVersion::UNDEF
#define HANDSHAKE_WINDOW 60
#define BECOME_PRIMARY_CLIENT 5
#define BECOME_PRIMARY_SERVER 5
#define TLS_TIMEOUT_CLIENT 1000
#define TLS_TIMEOUT_SERVER 1000
#define FEEDBACK 0
#elif BF == 2
#define PROTO_CIPHER "BF-CBC"
#define TLS_VER_MIN TLSVersion::UNDEF
#define HANDSHAKE_WINDOW 10
#define BECOME_PRIMARY_CLIENT 10
#define BECOME_PRIMARY_SERVER 10
#define TLS_TIMEOUT_CLIENT 2000
#define TLS_TIMEOUT_SERVER 1000
#define FEEDBACK 0
#elif BF == 3
#define PROTO_CIPHER "BF-CBC"
#define TLS_VER_MIN TLSVersion::UNDEF
#define HANDSHAKE_WINDOW 60
#define BECOME_PRIMARY_CLIENT 60
#define BECOME_PRIMARY_SERVER 10
#define TLS_TIMEOUT_CLIENT 2000
#define TLS_TIMEOUT_SERVER 1000
#define FEEDBACK 0
#elif BF != 0
#error unknown BF value
#endif

// TLS timeout
#ifndef TLS_TIMEOUT_CLIENT
#define TLS_TIMEOUT_CLIENT 2000
#endif
#ifndef TLS_TIMEOUT_SERVER
#define TLS_TIMEOUT_SERVER 2000
#endif

// NoisyWire
#ifndef NOERR
#define SIMULATE_OOO
#define SIMULATE_DROPPED
#define SIMULATE_CORRUPTED
#endif

// how many virtual seconds between SSL renegotiations
#ifdef PROTO_RENEG
#define RENEG PROTO_RENEG
#else
#define RENEG 900
#endif

// feedback
#ifndef FEEDBACK
#define FEEDBACK 1
#else
#define FEEDBACK 0
#endif

// number of iterations
#ifdef PROTO_ITER
#define ITER PROTO_ITER
#else
#define ITER 1000000
#endif

// number of high-level session iterations
#ifdef PROTO_SITER
#define SITER PROTO_SITER
#else
#define SITER 1
#endif

// number of retries for failed test
#ifndef N_RETRIES
#define N_RETRIES 2
#endif

// potentially, the above manifest constants can be converted to variables and modified
// within the different TEST() functions that replace main() in the original file

// abort if we reach this limit
// #define DROUGHT_LIMIT 100000

#if !defined(PROTO_VERBOSE) && !defined(QUIET) && ITER <= 10000
#define VERBOSE
#endif

#define STRINGIZE1(x) #x
#define STRINGIZE(x) STRINGIZE1(x)

// setup cipher
#ifndef PROTO_CIPHER
#ifdef PROTOv2
#define PROTO_CIPHER "AES-256-GCM"
#define TLS_VER_MIN TLSVersion::Type::V1_2
#else
#define PROTO_CIPHER "AES-128-CBC"
#define TLS_VER_MIN TLSVersion::Type::UNDEF
#endif
#endif

// setup digest
#ifndef PROTO_DIGEST
#define PROTO_DIGEST "SHA1"
#endif

// setup compressor
#ifdef PROTOv2
#ifdef HAVE_LZ4
#define COMP_METH CompressContext::LZ4v2
#else
#define COMP_METH CompressContext::COMP_STUBv2
#endif
#else
#define COMP_METH CompressContext::LZO_STUB
#endif

#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/count.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/random/mtrandapi.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/ssl/proto.hpp>
#include <openvpn/init/initprocess.hpp>

#include <openvpn/crypto/cryptodcsel.hpp>

#if defined(USE_MBEDTLS_APPLE_HYBRID)
#define USE_MBEDTLS
#endif

#if !(defined(USE_OPENSSL) || defined(USE_MBEDTLS) || defined(USE_APPLE_SSL))
#error Must define one or more of USE_OPENSSL, USE_MBEDTLS, USE_APPLE_SSL.
#endif

#if defined(USE_OPENSSL) && (defined(USE_MBEDTLS) || defined(USE_APPLE_SSL))
#undef USE_OPENSSL
#define USE_OPENSSL_SERVER
#elif !defined(USE_OPENSSL) && defined(USE_MBEDTLS)
#define USE_MBEDTLS_SERVER
#elif defined(USE_OPENSSL) && !defined(USE_MBEDTLS)
#define USE_OPENSSL_SERVER
#else
#error no server setup
#endif

#if defined(USE_OPENSSL) || defined(USE_OPENSSL_SERVER)
#include <openvpn/openssl/util/init.hpp>

#include <openvpn/openssl/crypto/api.hpp>
#include <openvpn/openssl/ssl/sslctx.hpp>
#include <openvpn/openssl/util/rand.hpp>

#endif

#if defined(USE_APPLE_SSL) || defined(USE_MBEDTLS_APPLE_HYBRID)
#include <openvpn/applecrypto/crypto/api.hpp>
#include <openvpn/applecrypto/ssl/sslctx.hpp>
#include <openvpn/applecrypto/util/rand.hpp>
#endif

#if defined(USE_MBEDTLS) || defined(USE_MBEDTLS_SERVER)
#include <openvpn/mbedtls/crypto/api.hpp>
#include <openvpn/mbedtls/ssl/sslctx.hpp>
#include <openvpn/mbedtls/util/rand.hpp>
#include <mbedtls/debug.h>
#endif

#include <openvpn/crypto/selftest.hpp>

using namespace openvpn;

// server Crypto/SSL/Rand implementation
#if defined(USE_MBEDTLS_SERVER)
typedef MbedTLSCryptoAPI ServerCryptoAPI;
typedef MbedTLSContext ServerSSLAPI;
typedef MbedTLSRandom ServerRandomAPI;
#elif defined(USE_OPENSSL_SERVER)
typedef OpenSSLCryptoAPI ServerCryptoAPI;
typedef OpenSSLContext ServerSSLAPI;
typedef OpenSSLRandom ServerRandomAPI;
#else
#error No server SSL implementation defined
#endif

// client SSL implementation can be OpenSSL, Apple SSL, or MbedTLS
#if defined(USE_MBEDTLS)
#if defined(USE_MBEDTLS_APPLE_HYBRID)
typedef AppleCryptoAPI ClientCryptoAPI;
#else
typedef MbedTLSCryptoAPI ClientCryptoAPI;
#endif
typedef MbedTLSContext ClientSSLAPI;
typedef MbedTLSRandom ClientRandomAPI;
#elif defined(USE_APPLE_SSL)
typedef AppleCryptoAPI ClientCryptoAPI;
typedef AppleSSLContext ClientSSLAPI;
typedef AppleRandom ClientRandomAPI;
#elif defined(USE_OPENSSL)
typedef OpenSSLCryptoAPI ClientCryptoAPI;
typedef OpenSSLContext ClientSSLAPI;
typedef OpenSSLRandom ClientRandomAPI;
#else
#error No client SSL implementation defined
#endif

const char message[] = "Message _->_ 0000000000 It was a bright cold day in April, and the clocks\n"
                       "were striking thirteen. Winston Smith, his chin nuzzled\n"
                       "into his breast in an effort to escape the vile wind,\n"
                       "slipped quickly through the glass doors of Victory\n"
                       "Mansions, though not quickly enough to prevent a\n"
                       "swirl of gritty dust from entering along with him.\n"
#ifdef LARGE_MESSAGE
                       "It was a bright cold day in April, and the clocks\n"
                       "were striking thirteen. Winston Smith, his chin nuzzled\n"
                       "into his breast in an effort to escape the vile wind,\n"
                       "slipped quickly through the glass doors of Victory\n"
                       "Mansions, though not quickly enough to prevent a\n"
                       "swirl of gritty dust from entering along with him.\n"
                       "It was a bright cold day in April, and the clocks\n"
                       "were striking thirteen. Winston Smith, his chin nuzzled\n"
                       "into his breast in an effort to escape the vile wind,\n"
                       "slipped quickly through the glass doors of Victory\n"
                       "Mansions, though not quickly enough to prevent a\n"
                       "swirl of gritty dust from entering along with him.\n"
                       "It was a bright cold day in April, and the clocks\n"
                       "were striking thirteen. Winston Smith, his chin nuzzled\n"
                       "into his breast in an effort to escape the vile wind,\n"
                       "slipped quickly through the glass doors of Victory\n"
                       "Mansions, though not quickly enough to prevent a\n"
                       "swirl of gritty dust from entering along with him.\n"
                       "It was a bright cold day in April, and the clocks\n"
                       "were striking thirteen. Winston Smith, his chin nuzzled\n"
                       "into his breast in an effort to escape the vile wind,\n"
                       "slipped quickly through the glass doors of Victory\n"
                       "Mansions, though not quickly enough to prevent a\n"
                       "swirl of gritty dust from entering along with him.\n"
#endif
    ;

// A "Drought" measures the maximum period of time between
// any two successive events.  Used to measure worst-case
// packet loss.
class DroughtMeasure
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(drought_limit_exceeded);

    DroughtMeasure(const std::string &name_arg, TimePtr now_arg)
        : now(now_arg), name(name_arg)
    {
    }

    void event()
    {
        if (last_event.defined())
        {
            Time::Duration since_last = *now - last_event;
            if (since_last > drought)
            {
                drought = since_last;
#if defined(VERBOSE) || defined(DROUGHT_LIMIT)
                {
                    const unsigned int r = drought.raw();
#if defined(VERBOSE)
                    std::cout << "*** Drought " << name << " has reached " << r << std::endl;
#endif
#ifdef DROUGHT_LIMIT
                    if (r > DROUGHT_LIMIT)
                        throw drought_limit_exceeded();
#endif
                }
#endif
            }
        }
        last_event = *now;
    }

    Time::Duration operator()() const
    {
        return drought;
    }

  private:
    TimePtr now;
    Time last_event;
    Time::Duration drought;
    std::string name;
};

// test the OpenVPN protocol implementation in ProtoContext
class TestProto : public ProtoContextCallbackInterface
{
    /* Callback methods that are not used */
    void active(bool primary) override
    {
    }

    bool supports_epoch_data() override
    {
        return true;
    }

  public:
    OPENVPN_EXCEPTION(session_invalidated);

    TestProto(const ProtoContext::ProtoConfig::Ptr &config,
              const SessionStats::Ptr &stats)
        : proto_context(this, config, stats),
          control_drought("control", config->now),
          data_drought("data", config->now),
          frame(config->frame)
    {
        // zero progress value
        std::memset(progress_, 0, 11);
    }

    void reset()
    {
        net_out.clear();
        proto_context.reset();
        proto_context.conf().mss_parms.mssfix = MSSParms::MSSFIX_DEFAULT;
    }

    void initial_app_send(const char *msg)
    {
        proto_context.start();
        const size_t msglen = std::strlen(msg) + 1;
        BufferAllocated app_buf((unsigned char *)msg, msglen, BufAllocFlags::NO_FLAGS);
        copy_progress(app_buf);
        control_send(std::move(app_buf));
        proto_context.flush(true);
    }

    void app_send_templ_init(const char *msg)
    {
        proto_context.start();
        const size_t msglen = std::strlen(msg) + 1;
        templ = BufferAllocatedRc::Create((unsigned char *)msg, msglen, BufAllocFlags::NO_FLAGS);
        proto_context.flush(true);
    }

    void app_send_templ()
    {
#if !FEEDBACK
        if (bool(iteration++ & 1) == is_server())
        {
            modmsg(templ);
            BufferAllocated app_buf(*templ);
            control_send(std::move(app_buf));
            flush(true);
            ++n_control_send_;
        }
#endif
    }

    bool do_housekeeping()
    {
        if (proto_context.now() >= proto_context.next_housekeeping())
        {
            proto_context.housekeeping();
            return true;
        }
        else
            return false;
    }

    void control_send(BufferPtr &&app_bp)
    {
        app_bytes_ += app_bp->size();
        proto_context.control_send(std::move(app_bp));
    }

    void control_send(BufferAllocated &&app_buf)
    {
        app_bytes_ += app_buf.size();
        proto_context.control_send(std::move(app_buf));
    }

    BufferPtr data_encrypt_string(const char *str)
    {
        BufferPtr bp = BufferAllocatedRc::Create();
        frame->prepare(Frame::READ_LINK_UDP, *bp);
        bp->write((unsigned char *)str, std::strlen(str));
        data_encrypt(*bp);
        return bp;
    }

    void data_encrypt(BufferAllocated &in_out)
    {
        proto_context.data_encrypt(in_out);
    }

    void data_decrypt(const ProtoContext::PacketType &type, BufferAllocated &in_out)
    {
        proto_context.data_decrypt(type, in_out);
        if (in_out.size())
        {
            data_bytes_ += in_out.size();
            data_drought.event();
        }
    }

    size_t net_bytes() const
    {
        return net_bytes_;
    }
    size_t app_bytes() const
    {
        return app_bytes_;
    }
    size_t data_bytes() const
    {
        return data_bytes_;
    }
    size_t n_control_recv() const
    {
        return n_control_recv_;
    }
    size_t n_control_send() const
    {
        return n_control_send_;
    }

    const char *progress() const
    {
        return progress_;
    }

    void finalize()
    {
        data_drought.event();
        control_drought.event();
    }

    void check_invalidated()
    {
        if (proto_context.invalidated())
            throw session_invalidated(Error::name(proto_context.invalidation_reason()));
    }

    void disable_xmit()
    {
        disable_xmit_ = true;
    }

    ProtoContext proto_context;

    std::deque<BufferPtr> net_out;

    DroughtMeasure control_drought;
    DroughtMeasure data_drought;

  private:
    void control_net_send(const Buffer &net_buf) override
    {
        if (disable_xmit_)
            return;
        net_bytes_ += net_buf.size();
        net_out.push_back(BufferAllocatedRc::Create(net_buf, BufAllocFlags::NO_FLAGS));
    }

    void control_recv(BufferPtr &&app_bp) override
    {
        BufferPtr work;
        work.swap(app_bp);
        if (work->size() >= 23)
            std::memcpy(progress_, work->data() + 13, 10);

#ifdef VERBOSE
        {
            const ssize_t trunc = 64;
            const std::string show((char *)work->data(), trunc);
            std::cout << now().raw() << " " << mode().str() << " " << show << std::endl;
        }
#endif
#if FEEDBACK
        modmsg(work);
        control_send(std::move(work));
#endif
        control_drought.event();
        ++n_control_recv_;
    }

    void copy_progress(Buffer &buf)
    {
        if (progress_[0]) // make sure progress was initialized
            std::memcpy(buf.data() + 13, progress_, 10);
    }

    void modmsg(BufferPtr &buf)
    {
        char *msg = (char *)buf->data();
        if (proto_context.is_server())
        {
            msg[8] = 'S';
            msg[11] = 'C';
        }
        else
        {
            msg[8] = 'C';
            msg[11] = 'S';
        }

        // increment embedded number
        for (int i = 22; i >= 13; i--)
        {
            if (msg[i] != '9')
            {
                msg[i]++;
                break;
            }
            else
                msg[i] = '0';
        }
    }

    Frame::Ptr frame;
    size_t app_bytes_ = 0;
    size_t net_bytes_ = 0;
    size_t data_bytes_ = 0;
    size_t n_control_send_ = 0;
    size_t n_control_recv_ = 0;
    BufferPtr templ;
#if !FEEDBACK
    size_t iteration = 0;
#endif
    char progress_[11];
    bool disable_xmit_ = false;
};

class TestProtoClient : public TestProto
{
    typedef TestProto Base;

  public:
    TestProtoClient(const ProtoContext::ProtoConfig::Ptr &config,
                    const SessionStats::Ptr &stats)
        : TestProto(config, stats)
    {
    }

  private:
    void client_auth(Buffer &buf) override
    {
        const std::string username("foo");
        const std::string password("bar");
        ProtoContext::write_auth_string(username, buf);
        ProtoContext::write_auth_string(password, buf);
    }
};

class TestProtoServer : public TestProto
{

  public:
    void start()
    {
        proto_context.start();
    }

    OPENVPN_SIMPLE_EXCEPTION(auth_failed);


    TestProtoServer(const ProtoContext::ProtoConfig::Ptr &config,
                    const SessionStats::Ptr &stats)
        : TestProto(config, stats)
    {
    }

  private:
    void server_auth(const std::string &username,
                     const SafeString &password,
                     const std::string &peer_info,
                     const AuthCert::Ptr &auth_cert) override
    {
#ifdef VERBOSE
        std::cout << "**** AUTHENTICATE " << username << '/' << password << " PEER INFO:" << std::endl;
        std::cout << peer_info;
#endif
        if (username != "foo" || password != "bar")
            throw auth_failed();
    }
};

// Simulate a noisy transmission channel where packets can be dropped,
// reordered, or corrupted.
class NoisyWire
{
  public:
    NoisyWire(const std::string title_arg,
              TimePtr now_arg,
              RandomAPI &rand_arg,
              const unsigned int reorder_prob_arg,
              const unsigned int drop_prob_arg,
              const unsigned int corrupt_prob_arg)
        : title(title_arg),
#ifdef VERBOSE
          now(now_arg),
#endif
          random(rand_arg),
          reorder_prob(reorder_prob_arg),
          drop_prob(drop_prob_arg),
          corrupt_prob(corrupt_prob_arg)
    {
    }

    template <typename T1, typename T2>
    void xfer(T1 &a, T2 &b)
    {
        // check for errors
        a.check_invalidated();
        b.check_invalidated();

        // need to retransmit?
        if (a.do_housekeeping())
        {
#ifdef VERBOSE
            std::cout << now->raw() << " " << title << " Housekeeping" << std::endl;
#endif
        }

        // queue a control channel packet
        a.app_send_templ();

        // queue a data channel packet
        if (a.proto_context.data_channel_ready())
        {
            BufferPtr bp = a.data_encrypt_string("Waiting for godot A... Waiting for godot B... Waiting for godot C... Waiting for godot D... Waiting for godot E... Waiting for godot F... Waiting for godot G... Waiting for godot H... Waiting for godot I... Waiting for godot J...");
            wire.push_back(bp);
        }

        // transfer network packets from A -> wire
        while (!a.net_out.empty())
        {
            BufferPtr bp = a.net_out.front();
#ifdef VERBOSE
            std::cout << now->raw() << " " << title << " " << a.dump_packet(*bp) << std::endl;
#endif
            a.net_out.pop_front();
            wire.push_back(bp);
        }

        // transfer network packets from wire -> B
        while (true)
        {
            BufferPtr bp = recv();
            if (!bp)
                break;
            typename ProtoContext::PacketType pt = b.proto_context.packet_type(*bp);
            if (pt.is_control())
            {
#ifdef VERBOSE
                if (!b.control_net_validate(pt, *bp)) // not strictly necessary since control_net_recv will also validate
                    std::cout << now->raw() << " " << title << " CONTROL PACKET VALIDATION FAILED" << std::endl;
#endif
                b.proto_context.control_net_recv(pt, std::move(bp));
            }
            else if (pt.is_data())
            {
                try
                {
                    b.data_decrypt(pt, *bp);
#ifdef VERBOSE
                    if (bp->size())
                    {
                        const std::string show((char *)bp->data(), std::min(bp->size(), size_t(40)));
                        std::cout << now->raw() << " " << title << " DATA CHANNEL DECRYPT: " << show << std::endl;
                    }
#endif
                }
                catch ([[maybe_unused]] const std::exception &e)
                {
#ifdef VERBOSE
                    std::cout << now->raw() << " " << title << " Exception on data channel decrypt: " << e.what() << std::endl;
#endif
                }
            }
            else
            {
#ifdef VERBOSE
                std::cout << now->raw() << " " << title << " KEY_STATE_ERROR" << std::endl;
#endif
                b.proto_context.stat().error(Error::KEY_STATE_ERROR);
            }

#ifdef SIMULATE_UDP_AMPLIFY_ATTACK
            if (b.proto_context.is_state_client_wait_reset_ack())
            {
                b.disable_xmit();
#ifdef VERBOSE
                std::cout << now->raw() << " " << title << " SIMULATE_UDP_AMPLIFY_ATTACK disable client xmit" << std::endl;
#endif
            }
#endif
        }
        b.proto_context.flush(true);
    }

  private:
    BufferPtr recv()
    {
#ifdef SIMULATE_OOO
        // simulate packets being received out of order
        if (wire.size() >= 2 && !rand(reorder_prob))
        {
            const size_t i = random.randrange(wire.size() - 1) + 1;
#ifdef VERBOSE
            std::cout << now->raw() << " " << title << " Simulating packet reordering " << i << " -> 0" << std::endl;
#endif
            std::swap(wire[0], wire[i]);
        }
#endif

        if (wire.size())
        {
            BufferPtr bp = wire.front();
            wire.pop_front();

#ifdef VERBOSE
            std::cout << now->raw() << " " << title << " Received packet, size=" << bp->size() << std::endl;
#endif

#ifdef SIMULATE_DROPPED
            // simulate dropped packet
            if (!rand(drop_prob))
            {
#ifdef VERBOSE
                std::cout << now->raw() << " " << title << " Simulating a dropped packet" << std::endl;
#endif
                return BufferPtr();
            }
#endif

#ifdef SIMULATE_CORRUPTED
            // simulate corrupted packet
            if (bp->size() && !rand(corrupt_prob))
            {
#ifdef VERBOSE
                std::cout << now->raw() << " " << title << " Simulating a corrupted packet" << std::endl;
#endif
                const size_t pos = random.randrange(bp->size());
                const unsigned char value = random.randrange(std::numeric_limits<unsigned char>::max());
                (*bp)[pos] = value;
            }
#endif
            return bp;
        }

        return BufferPtr();
    }

    unsigned int rand(const unsigned int prob)
    {
        if (prob)
            return random.randrange(prob);
        else
            return 1;
    }

    std::string title;
#ifdef VERBOSE
    TimePtr now;
#endif
    RandomAPI &random;
    unsigned int reorder_prob;
    unsigned int drop_prob;
    unsigned int corrupt_prob;
    std::deque<BufferPtr> wire;
};

class MySessionStats : public SessionStats
{
  public:
    typedef RCPtr<MySessionStats> Ptr;

    MySessionStats()
    {
        std::memset(errors, 0, sizeof(errors));
    }

    void error(const size_t err_type, const std::string *text = nullptr) override
    {
        if (err_type < Error::N_ERRORS)
            ++errors[err_type];
    }

    count_t get_error_count(const Error::Type type) const
    {
        if (type < Error::N_ERRORS)
            return errors[type];
        else
            return 0;
    }

    void show_error_counts() const
    {
        for (size_t i = 0; i < Error::N_ERRORS; ++i)
        {
            count_t c = errors[i];
            if (c)
                std::cerr << Error::name(i) << " : " << c << std::endl;
        }
    }

  private:
    count_t errors[Error::N_ERRORS];
};

/**
 * Create a client ssl config for testing.
 * @return
 */
static auto create_client_ssl_config(Frame::Ptr frame, ClientRandomAPI::Ptr rng, bool tls_version_mismatch = false)
{
    const std::string client_crt = read_text(TEST_KEYCERT_DIR "client.crt");
    const std::string client_key = read_text(TEST_KEYCERT_DIR "client.key");
    const std::string ca_crt = read_text(TEST_KEYCERT_DIR "ca.crt");

    // client config
    ClientSSLAPI::Config::Ptr cc(new ClientSSLAPI::Config());
    cc->set_mode(Mode(Mode::CLIENT));
    cc->set_frame(frame);
    cc->set_rng(rng);
#ifdef USE_APPLE_SSL
    cc->load_identity("etest");
#else
    cc->load_ca(ca_crt, true);
    cc->load_cert(client_crt);
    cc->load_private_key(client_key);
#endif
    if (tls_version_mismatch)
        cc->set_tls_version_max(TLSVersion::Type::V1_2);
    else
        cc->set_tls_version_min(TLS_VER_MIN);
#ifdef VERBOSE
    cc->set_debug_level(1);
#endif
    return cc;
}

static auto create_client_proto_context(ClientSSLAPI::Config::Ptr cc, Frame::Ptr frame, ClientRandomAPI::Ptr rng, MySessionStats::Ptr cli_stats, Time &time, const std::string &tls_crypt_v2_key_fn = "")

{
    const std::string tls_auth_key = read_text(TEST_KEYCERT_DIR "tls-auth.key");
    const std::string tls_crypt_v2_client_key = tls_crypt_v2_key_fn.empty()
                                                    ? read_text(TEST_KEYCERT_DIR "tls-crypt-v2-client.key")
                                                    : read_text(TEST_KEYCERT_DIR + tls_crypt_v2_key_fn);

    // client ProtoContext config
    typedef ProtoContext ClientProtoContext;
    ClientProtoContext::ProtoConfig::Ptr cp(new ClientProtoContext::ProtoConfig);
    cp->ssl_factory = cc->new_factory();
    CryptoAlgs::allow_default_dc_algs<ClientCryptoAPI>(cp->ssl_factory->libctx(), false, false);
    cp->dc.set_factory(new CryptoDCSelect<ClientCryptoAPI>(cp->ssl_factory->libctx(), frame, cli_stats, rng));
    cp->tlsprf_factory.reset(new CryptoTLSPRFFactory<ClientCryptoAPI>());
    cp->frame = std::move(frame);
    cp->now = &time;
    cp->rng = rng;
    cp->prng = rng;
    cp->protocol = Protocol(Protocol::UDPv4);
    cp->layer = Layer(Layer::OSI_LAYER_3);
#ifdef PROTOv2
    cp->enable_op32 = true;
    cp->remote_peer_id = 100;
#endif
    cp->comp_ctx = CompressContext(COMP_METH, false);
    cp->dc.set_cipher(CryptoAlgs::lookup(PROTO_CIPHER));
    cp->dc.set_digest(CryptoAlgs::lookup(PROTO_DIGEST));

#ifdef USE_TLS_AUTH
    cp->tls_auth_factory.reset(new CryptoOvpnHMACFactory<ClientCryptoAPI>());
    cp->tls_auth_key.parse(tls_auth_key);
    cp->set_tls_auth_digest(CryptoAlgs::lookup(PROTO_DIGEST));
    cp->key_direction = 0;
#endif
#ifdef USE_TLS_CRYPT
    cp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
    cp->tls_crypt_key.parse(tls_auth_key);
    cp->set_tls_crypt_algs();
    cp->tls_crypt_ = ProtoContext::ProtoConfig::TLSCrypt::V1;
#endif
#ifdef USE_TLS_CRYPT_V2
    cp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
    cp->set_tls_crypt_algs();
    {
        TLSCryptV2ClientKey tls_crypt_v2_key(cp->tls_crypt_context);
        tls_crypt_v2_key.parse(tls_crypt_v2_client_key);
        tls_crypt_v2_key.extract_key(cp->tls_crypt_key);
        tls_crypt_v2_key.extract_wkc(cp->wkc);
    }
    cp->tls_crypt_ = ProtoContext::ProtoConfig::TLSCrypt::V2;
#endif
#if defined(HANDSHAKE_WINDOW)
    cp->handshake_window = Time::Duration::seconds(HANDSHAKE_WINDOW);
#elif SITER > 1
    cp->handshake_window = Time::Duration::seconds(30);
#else
    cp->handshake_window = Time::Duration::seconds(18); // will cause a small number of handshake failures
#endif
#ifdef BECOME_PRIMARY_CLIENT
    cp->become_primary = Time::Duration::seconds(BECOME_PRIMARY_CLIENT);
#else
    cp->become_primary = cp->handshake_window;
#endif
    cp->tls_timeout = Time::Duration::milliseconds(TLS_TIMEOUT_CLIENT);
#if defined(CLIENT_NO_RENEG)
    cp->renegotiate = Time::Duration::infinite();
#else
    cp->renegotiate = Time::Duration::seconds(RENEG);
#endif
    cp->expire = cp->renegotiate + cp->renegotiate;
    cp->keepalive_ping = Time::Duration::seconds(5);
    cp->keepalive_timeout = Time::Duration::seconds(60);
    cp->keepalive_timeout_early = cp->keepalive_timeout;

#ifdef VERBOSE
    std::cout << "CLIENT OPTIONS: " << cp->options_string() << std::endl;
    std::cout << "CLIENT PEER INFO:" << std::endl;
    std::cout << cp->peer_info_string();
#endif
    return cp;
}

// execute the unit test in one thread
int test(const int thread_num,
         bool use_tls_ekm,
         bool tls_version_mismatch,
         const std::string &tls_crypt_v2_key_fn = "",
         bool use_tls_auth_with_tls_crypt_v2 = false)
{
    try
    {
        // frame
        Frame::Ptr frame(new Frame(Frame::Context(128, 378, 128, 0, 16, BufAllocFlags::NO_FLAGS)));

        // RNG
        ClientRandomAPI::Ptr prng_cli(new ClientRandomAPI());
        ServerRandomAPI::Ptr prng_serv(new ServerRandomAPI());
        MTRand rng_noncrypto;

        // init simulated time
        Time time;
        const Time::Duration time_step = Time::Duration::binary_ms(100);

        // config files
        const std::string ca_crt = read_text(TEST_KEYCERT_DIR "ca.crt");
        const std::string server_crt = read_text(TEST_KEYCERT_DIR "server.crt");
        const std::string server_key = read_text(TEST_KEYCERT_DIR "server.key");
        const std::string dh_pem = read_text(TEST_KEYCERT_DIR "dh.pem");
        const std::string tls_auth_key = read_text(TEST_KEYCERT_DIR "tls-auth.key");
        const std::string tls_crypt_v2_server_key = tls_crypt_v2_key_fn.empty()
                                                        ? read_text(TEST_KEYCERT_DIR "tls-crypt-v2-server.key")
                                                        : "";

        // client config
        ClientSSLAPI::Config::Ptr cc = create_client_ssl_config(frame, prng_cli, tls_version_mismatch);
        MySessionStats::Ptr cli_stats(new MySessionStats);

        auto cp = create_client_proto_context(std::move(cc), frame, prng_cli, cli_stats, time, tls_crypt_v2_key_fn);
        if (use_tls_ekm)
            cp->dc.set_key_derivation(CryptoAlgs::KeyDerivation::TLS_EKM);

        // server config
        MySessionStats::Ptr serv_stats(new MySessionStats);

        ServerSSLAPI::Config::Ptr sc(new ClientSSLAPI::Config());
        sc->set_mode(Mode(Mode::SERVER));
        sc->set_frame(frame);
        sc->set_rng(prng_serv);
        sc->load_ca(ca_crt, true);
        sc->load_cert(server_crt);
        sc->load_private_key(server_key);
        sc->load_dh(dh_pem);
        sc->set_tls_version_min(tls_version_mismatch ? TLSVersion::Type::V1_3 : TLS_VER_MIN);
#ifdef VERBOSE
        sc->set_debug_level(1);
#endif

        // server ProtoContext config
        typedef ProtoContext ServerProtoContext;
        ServerProtoContext::ProtoConfig::Ptr sp(new ServerProtoContext::ProtoConfig);
        sp->ssl_factory = sc->new_factory();
        sp->dc.set_factory(new CryptoDCSelect<ServerCryptoAPI>(sp->ssl_factory->libctx(), frame, serv_stats, prng_serv));
        sp->tlsprf_factory.reset(new CryptoTLSPRFFactory<ServerCryptoAPI>());
        sp->frame = frame;
        sp->now = &time;
        sp->rng = prng_serv;
        sp->prng = prng_serv;
        sp->protocol = Protocol(Protocol::UDPv4);
        sp->layer = Layer(Layer::OSI_LAYER_3);
#ifdef PROTOv2
        sp->enable_op32 = true;
        sp->remote_peer_id = 101;
#endif
        sp->comp_ctx = CompressContext(COMP_METH, false);
        sp->dc.set_cipher(CryptoAlgs::lookup(PROTO_CIPHER));
        sp->dc.set_digest(CryptoAlgs::lookup(PROTO_DIGEST));
        if (use_tls_ekm)
            sp->dc.set_key_derivation(CryptoAlgs::KeyDerivation::TLS_EKM);
#ifdef USE_TLS_AUTH
        sp->tls_auth_factory.reset(new CryptoOvpnHMACFactory<ServerCryptoAPI>());
        sp->tls_auth_key.parse(tls_auth_key);
        sp->set_tls_auth_digest(CryptoAlgs::lookup(PROTO_DIGEST));
        sp->key_direction = 1;
#endif
#if defined(USE_TLS_CRYPT)
        sp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
        sp->tls_crypt_key.parse(tls_auth_key);
        sp->set_tls_crypt_algs();
        cp->tls_crypt_ = ProtoContext::ProtoConfig::TLSCrypt::V1;
#endif
#ifdef USE_TLS_CRYPT_V2
        sp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());

        if (tls_crypt_v2_key_fn.empty())
        {
            TLSCryptV2ServerKey tls_crypt_v2_key;
            tls_crypt_v2_key.parse(tls_crypt_v2_server_key);
            tls_crypt_v2_key.extract_key(sp->tls_crypt_key);
        }

        sp->set_tls_crypt_algs();
        sp->tls_crypt_metadata_factory.reset(new CryptoTLSCryptMetadataFactory());
        sp->tls_crypt_ = ProtoContext::ProtoConfig::TLSCrypt::V2;
        sp->tls_crypt_v2_serverkey_id = !tls_crypt_v2_key_fn.empty();
        sp->tls_crypt_v2_serverkey_dir = TEST_KEYCERT_DIR;

        if (use_tls_auth_with_tls_crypt_v2)
        {
            sp->tls_auth_factory.reset(new CryptoOvpnHMACFactory<ServerCryptoAPI>());
            sp->tls_auth_key.parse(tls_auth_key);
            sp->set_tls_auth_digest(CryptoAlgs::lookup(PROTO_DIGEST));
            sp->key_direction = 1;
        }
#endif
#if defined(HANDSHAKE_WINDOW)
        sp->handshake_window = Time::Duration::seconds(HANDSHAKE_WINDOW);
#elif SITER > 1
        sp->handshake_window = Time::Duration::seconds(30);
#else
        sp->handshake_window = Time::Duration::seconds(17) + Time::Duration::binary_ms(512);
#endif
#ifdef BECOME_PRIMARY_SERVER
        sp->become_primary = Time::Duration::seconds(BECOME_PRIMARY_SERVER);
#else
        sp->become_primary = sp->handshake_window;
#endif
        sp->tls_timeout = Time::Duration::milliseconds(TLS_TIMEOUT_SERVER);
#if defined(SERVER_NO_RENEG)
        sp->renegotiate = Time::Duration::infinite();
#else
        // NOTE: if we don't add sp->handshake_window, both client and server reneg-sec (RENEG)
        // will be equal and will therefore occasionally collide.  Such collisions can sometimes
        // produce this OpenSSL error:
        // OpenSSLContext::SSL::read_cleartext: BIO_read failed, cap=400 status=-1: error:140E0197:SSL routines:SSL_shutdown:shutdown while in init
        // The issue was introduced by this patch in OpenSSL:
        //   https://github.com/openssl/openssl/commit/64193c8218540499984cd63cda41f3cd491f3f59
        sp->renegotiate = Time::Duration::seconds(RENEG) + sp->handshake_window;
#endif
        sp->expire = sp->renegotiate + sp->renegotiate;
        sp->keepalive_ping = Time::Duration::seconds(5);
        sp->keepalive_timeout = Time::Duration::seconds(60);
        sp->keepalive_timeout_early = Time::Duration::seconds(10);

#ifdef VERBOSE
        std::cout << "SERVER OPTIONS: " << sp->options_string() << std::endl;
        std::cout << "SERVER PEER INFO:" << std::endl;
        std::cout << sp->peer_info_string();
#endif

        TestProtoClient cli_proto(cp, cli_stats);
        TestProtoServer serv_proto(sp, serv_stats);

        for (int i = 0; i < SITER; ++i)
        {
#ifdef VERBOSE
            std::cout << "***** SITER " << i << std::endl;
#endif
            cli_proto.reset();
            serv_proto.reset();

            NoisyWire client_to_server("Client -> Server", &time, rng_noncrypto, 8, 16, 32); // last value: 32
            NoisyWire server_to_client("Server -> Client", &time, rng_noncrypto, 8, 16, 32); // last value: 32

            int j = -1;
            try
            {
#if FEEDBACK
                // start feedback loop
                cli_proto.initial_app_send(message);
                serv_proto.start();
#else
                cli_proto.app_send_templ_init(message);
                serv_proto.app_send_templ_init(message);
#endif

                // message loop
                for (j = 0; j < ITER; ++j)
                {
                    client_to_server.xfer(cli_proto, serv_proto);
                    server_to_client.xfer(serv_proto, cli_proto);
                    time += time_step;
                }
            }
            catch (const std::exception &e)
            {
                std::cerr << "Exception[" << i << '/' << j << "]: " << e.what() << std::endl;
                return 1;
            }
        }

        cli_proto.finalize();
        serv_proto.finalize();

        const size_t ab = cli_proto.app_bytes() + serv_proto.app_bytes();
        const size_t nb = cli_proto.net_bytes() + serv_proto.net_bytes();
        const size_t db = cli_proto.data_bytes() + serv_proto.data_bytes();

        std::cerr << "*** app bytes=" << ab
                  << " net_bytes=" << nb
                  << " data_bytes=" << db
                  << " prog=" << cli_proto.progress() << '/' << serv_proto.progress()
#if !FEEDBACK
                  << " CTRL=" << cli_proto.n_control_recv() << '/' << cli_proto.n_control_send() << '/' << serv_proto.n_control_recv() << '/' << serv_proto.n_control_send()
#endif
                  << " D=" << cli_proto.control_drought().raw() << '/' << cli_proto.data_drought().raw() << '/' << serv_proto.control_drought().raw() << '/' << serv_proto.data_drought().raw()
                  << " N=" << cli_proto.proto_context.negotiations() << '/' << serv_proto.proto_context.negotiations()
                  << " SH=" << cli_proto.proto_context.slowest_handshake().raw() << '/' << serv_proto.proto_context.slowest_handshake().raw()
                  << " HE=" << cli_stats->get_error_count(Error::HANDSHAKE_TIMEOUT) << '/' << serv_stats->get_error_count(Error::HANDSHAKE_TIMEOUT)
                  << std::endl;

#ifdef STATS
        std::cerr << "-------- CLIENT STATS --------" << std::endl;
        cli_stats->show_error_counts();
        std::cerr << "-------- SERVER STATS --------" << std::endl;
        serv_stats->show_error_counts();
#endif
#ifdef OPENVPN_MAX_DATALIMIT_BYTES
        std::cerr << "------------------------------" << std::endl;
        std::cerr << "MAX_DATALIMIT_BYTES=" << DataLimit::max_bytes() << std::endl;
#endif
    }
    catch (const std::exception &e)
    {
        std::cerr << "Exception: " << e.what() << std::endl;
        return 1;
    }
    return 0;
}

int test_retry(const int thread_num,
               const int n_retries,
               bool use_tls_ekm,
               bool tls_version_mismatch = false,
               const std::string &tls_crypt_v2_key_fn = "",
               bool use_tls_auth_with_tls_crypt_v2 = false)
{
    int ret = 1;
    for (int i = 0; i < n_retries; ++i)
    {
        ret = test(thread_num, use_tls_ekm, tls_version_mismatch, tls_crypt_v2_key_fn, use_tls_auth_with_tls_crypt_v2);
        if (!ret)
            return 0;
        std::cout << "Retry " << (i + 1) << '/' << n_retries << std::endl;
    }
    std::cout << "Failed" << std::endl;
    return ret;
}

class ProtoUnitTest : public testing::Test
{
    // Sets up the test fixture.
    void SetUp() override
    {
#if defined(USE_MBEDTLS)
        mbedtls_debug_set_threshold(1);
#endif

        openvpn::Compress::set_log_level(0);

#ifdef PROTO_VERBOSE
        openvpn::ProtoContext::set_log_level(2);
#else
        openvpn::ProtoContext::set_log_level(0);
#endif
    }

    // Tears down the test fixture.
    void TearDown() override
    {
#if defined(USE_MBEDTLS)
        mbedtls_debug_set_threshold(4);
#endif
        openvpn::Compress::set_log_level(openvpn::Compress::default_log_level);
        openvpn::ProtoContext::set_log_level(openvpn::ProtoContext::default_log_level);
    }
};

TEST_F(ProtoUnitTest, base_single_thread_tls_ekm)
{
    if (!openvpn::SSLLib::SSLAPI::support_key_material_export())
        GTEST_SKIP_("our mbed TLS implementation does not support TLS EKM");

    int ret = 0;

    ret = test_retry(1, N_RETRIES, true);

    EXPECT_EQ(ret, 0);
}

TEST_F(ProtoUnitTest, base_single_thread_no_tls_ekm)
{
    int ret = 0;

    ret = test_retry(1, N_RETRIES, false);

    EXPECT_EQ(ret, 0);
}

// Our mbedtls currently has a no-op set_tls_version_max() implementation,
// so we can't set mismatched client and server TLS versions.
// For now, just test this for OPENSSL which is full-featured.
#ifdef USE_OPENSSL
TEST_F(ProtoUnitTest, base_single_thread_tls_version_mismatch)
{
    int ret = test(1, false, true);
    EXPECT_NE(ret, 0);
}
#endif

#ifdef USE_TLS_CRYPT_V2
TEST_F(ProtoUnitTest, base_single_thread_tls_crypt_v2_with_embedded_serverkey)
{
    int ret = test_retry(1, N_RETRIES, false, false, "tls-crypt-v2-client-with-serverkey.key");
    EXPECT_EQ(ret, 0);
}

TEST_F(ProtoUnitTest, base_single_thread_tls_crypt_v2_with_missing_embedded_serverkey)
{
    int ret = test(1, false, false, "tls-crypt-v2-client-with-missing-serverkey.key");
    EXPECT_NE(ret, 0);
}

TEST_F(ProtoUnitTest, base_single_thread_tls_crypt_v2_with_tls_auth_also_active)
{
    int ret = test_retry(1, N_RETRIES, false, false, "tls-crypt-v2-client-with-serverkey.key", true);
    EXPECT_EQ(ret, 0);
}
#endif

TEST_F(ProtoUnitTest, base_multiple_thread)
{
    unsigned int num_threads = std::thread::hardware_concurrency();
#if defined(PROTO_N_THREADS) && PROTO_N_THREADS >= 1
    num_threads = PROTO_N_THREADS;
#endif

    std::vector<std::thread> running_threads{};
    std::vector<int> results(num_threads, -777);

    for (unsigned int i = 0; i < num_threads; ++i)
    {
        running_threads.emplace_back([i, &results]()
                                     {
            /* Use ekm on odd threads */
            const bool use_ekm = openvpn::SSLLib::SSLAPI::support_key_material_export() && (i % 2 == 0);
            results[i] = test_retry(static_cast<int>(i), N_RETRIES, use_ekm); });
    }
    for (unsigned int i = 0; i < num_threads; ++i)
    {
        running_threads[i].join();
    }


    // expect 1 for all threads
    const std::vector<int> expected_results(num_threads, 0);

    EXPECT_THAT(expected_results, ::testing::ContainerEq(results));
}

TEST(proto, iv_ciphers_aead)
{
    CryptoAlgs::allow_default_dc_algs<SSLLib::CryptoAPI>(nullptr, true, false);

    auto protoConf = openvpn::ProtoContext::ProtoConfig();

    auto infostring = protoConf.peer_info_string(false);

    auto ivciphers = infostring.substr(infostring.find("IV_CIPHERS="));
    ivciphers = ivciphers.substr(0, ivciphers.find("\n"));


    std::string expectedstr{"IV_CIPHERS=AES-128-GCM:AES-192-GCM:AES-256-GCM"};
    if (SSLLib::CryptoAPI::CipherContextAEAD::is_supported(nullptr, openvpn::CryptoAlgs::CHACHA20_POLY1305))
        expectedstr += ":CHACHA20-POLY1305";

    EXPECT_EQ(ivciphers, expectedstr);
}

TEST(proto, iv_ciphers_non_preferred)
{
    CryptoAlgs::allow_default_dc_algs<SSLLib::CryptoAPI>(nullptr, false, false);

    auto protoConf = openvpn::ProtoContext::ProtoConfig();

    auto infostring = protoConf.peer_info_string(true);

    auto ivciphers = infostring.substr(infostring.find("IV_CIPHERS="));
    ivciphers = ivciphers.substr(0, ivciphers.find("\n"));


    std::string expectedstr{"IV_CIPHERS=AES-128-CBC:AES-192-CBC:AES-256-CBC:AES-128-GCM:AES-192-GCM:AES-256-GCM"};
    if (SSLLib::CryptoAPI::CipherContextAEAD::is_supported(nullptr, openvpn::CryptoAlgs::CHACHA20_POLY1305))
        expectedstr += ":CHACHA20-POLY1305";

    EXPECT_EQ(ivciphers, expectedstr);
}

TEST(proto, iv_ciphers_legacy)
{

    /* Need to a whole lot of things to enable legacy provider/OpenSSL context */
    SSLLib::SSLAPI::Config::Ptr config = new SSLLib::SSLAPI::Config;
    EXPECT_TRUE(config);

    StrongRandomAPI::Ptr rng(new SSLLib::RandomAPI());
    config->set_rng(rng);

    config->set_mode(Mode(Mode::CLIENT));
    config->set_flags(SSLConfigAPI::LF_ALLOW_CLIENT_CERT_NOT_REQUIRED);
    config->set_local_cert_enabled(false);
    config->enable_legacy_algorithms(true);

    auto factory_client = config->new_factory();
    EXPECT_TRUE(factory_client);

    auto client = factory_client->ssl();
    auto libctx = factory_client->libctx();


    CryptoAlgs::allow_default_dc_algs<SSLLib::CryptoAPI>(libctx, false, true);

    auto protoConf = openvpn::ProtoContext::ProtoConfig();

    auto infostring = protoConf.peer_info_string(false);

    auto ivciphers = infostring.substr(infostring.find("IV_CIPHERS="));
    ivciphers = ivciphers.substr(0, ivciphers.find("\n"));



    std::string expectedstr{"IV_CIPHERS=none:AES-128-CBC:AES-192-CBC:AES-256-CBC:DES-CBC:DES-EDE3-CBC"};

    if (SSLLib::CryptoAPI::CipherContext::is_supported(libctx, openvpn::CryptoAlgs::BF_CBC))
        expectedstr += ":BF-CBC";

    expectedstr += ":AES-128-GCM:AES-192-GCM:AES-256-GCM";

    if (SSLLib::CryptoAPI::CipherContextAEAD::is_supported(nullptr, openvpn::CryptoAlgs::CHACHA20_POLY1305))
        expectedstr += ":CHACHA20-POLY1305";

    EXPECT_EQ(ivciphers, expectedstr);
}

TEST(proto, controlmessage_invalidchar)
{
    std::string valid_auth_fail{"AUTH_FAILED: go away"};
    std::string valid_auth_fail_newline_end{"AUTH_FAILED: go away\n"};
    std::string invalid_auth_fail{"AUTH_FAILED: go\n away\n"};
    std::string lot_of_whitespace{"AUTH_FAILED: a lot of white space\n\n\r\n\r\n\r\n"};
    std::string only_whitespace{"\n\n\r\n\r\n\r\n"};
    std::string empty{""};

    BufferAllocated valid_auth_fail_buf{reinterpret_cast<const unsigned char *>(valid_auth_fail.c_str()), valid_auth_fail.size(), BufAllocFlags::GROW};
    BufferAllocated valid_auth_fail_newline_end_buf{reinterpret_cast<const unsigned char *>(valid_auth_fail_newline_end.c_str()), valid_auth_fail_newline_end.size(), BufAllocFlags::GROW};
    BufferAllocated invalid_auth_fail_buf{reinterpret_cast<const unsigned char *>(invalid_auth_fail.c_str()), invalid_auth_fail.size(), BufAllocFlags::GROW};
    BufferAllocated lot_of_whitespace_buf{reinterpret_cast<const unsigned char *>(lot_of_whitespace.c_str()), lot_of_whitespace.size(), BufAllocFlags::GROW};
    BufferAllocated only_whitespace_buf{reinterpret_cast<const unsigned char *>(only_whitespace.c_str()), only_whitespace.size(), BufAllocFlags::GROW};
    BufferAllocated empty_buf{reinterpret_cast<const unsigned char *>(empty.c_str()), empty.size(), BufAllocFlags::GROW};

    auto msg = ProtoContext::read_control_string<std::string>(valid_auth_fail_buf);
    EXPECT_EQ(msg, valid_auth_fail);
    EXPECT_TRUE(Unicode::is_valid_utf8(msg, Unicode::UTF8_NO_CTRL));

    auto msg2 = ProtoContext::read_control_string<std::string>(valid_auth_fail_newline_end_buf);
    EXPECT_EQ(msg2, valid_auth_fail);
    EXPECT_TRUE(Unicode::is_valid_utf8(msg2, Unicode::UTF8_NO_CTRL));

    auto msg3 = ProtoContext::read_control_string<std::string>(invalid_auth_fail_buf);
    EXPECT_EQ(msg3, "AUTH_FAILED: go\n away");
    EXPECT_FALSE(Unicode::is_valid_utf8(msg3, Unicode::UTF8_NO_CTRL));

    auto msg4 = ProtoContext::read_control_string<std::string>(lot_of_whitespace_buf);
    EXPECT_EQ(msg4, "AUTH_FAILED: a lot of white space");
    EXPECT_TRUE(Unicode::is_valid_utf8(msg4, Unicode::UTF8_NO_CTRL));

    auto msg5 = ProtoContext::read_control_string<std::string>(only_whitespace_buf);
    EXPECT_EQ(msg5, "");
    EXPECT_TRUE(Unicode::is_valid_utf8(msg5, Unicode::UTF8_NO_CTRL));

    auto msg6 = ProtoContext::read_control_string<std::string>(empty_buf);
    EXPECT_EQ(msg6, "");
    EXPECT_TRUE(Unicode::is_valid_utf8(msg5, Unicode::UTF8_NO_CTRL));
}

class MockCallback : public openvpn::ClientProto::NotifyCallback
{
    void client_proto_terminate()
    {
    }
};

class EventQueueVector : public openvpn::ClientEvent::Queue
{
  public:
    void add_event(openvpn::ClientEvent::Base::Ptr event) override
    {
        events.push_back(event);
    }

    std::vector<openvpn::ClientEvent::Base::Ptr> events;
};

TEST(proto, client_proto_check_cc_msg)
{
    asio::io_context io_context;
    ClientRandomAPI::Ptr rng_cli(new ClientRandomAPI());
    Frame::Ptr frame(new Frame(Frame::Context(128, 378, 128, 0, 16, BufAllocFlags::NO_FLAGS)));
    MySessionStats::Ptr cli_stats(new MySessionStats);
    Time time;

    openvpn::ClientEvent::Queue::Ptr eqv_ptr = new EventQueueVector{};
    /* keep a reference to the right class to avoid repeated casted */
    EventQueueVector *eqv = dynamic_cast<EventQueueVector *>(eqv_ptr.get());
    /* check that the cast worked */
    ASSERT_TRUE(eqv);

    MockCallback mockCB;
    openvpn::ClientProto::Session::Config clisessconf{};
    clisessconf.proto_context_config = create_client_proto_context(create_client_ssl_config(frame, rng_cli),
                                                                   frame,
                                                                   rng_cli,
                                                                   std::move(cli_stats),
                                                                   time);
    clisessconf.cli_events = std::move(eqv_ptr);
    openvpn::ClientProto::Session::Ptr clisession = new ClientProto::Session{io_context, clisessconf, &mockCB};

    clisession->validate_and_post_cc_msg("valid message");


    EXPECT_TRUE(eqv->events.empty());

    clisession->validate_and_post_cc_msg("invalid\nmessage");
    EXPECT_EQ(eqv->events.size(), 1);
    auto ev = eqv->events.back();
    auto uf = dynamic_cast<openvpn::ClientEvent::UnsupportedFeature *>(ev.get());
    /* check that the cast worked */
    ASSERT_TRUE(uf);
    EXPECT_EQ(uf->name, "Invalid chars in control message");
    EXPECT_EQ(uf->reason, "Control channel message with invalid characters not allowed to be send with post_cc_msg");
}
