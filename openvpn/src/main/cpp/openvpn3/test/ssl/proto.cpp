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

// Unit test for OpenVPN Protocol implementation (class ProtoContext)

#include <iostream>
#include <string>
#include <sstream>
#include <deque>
#include <algorithm>
#include <cstring>
#include <limits>
#include <thread>

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_WIN
#include "protowin.h"
#endif

#define OPENVPN_DEBUG
#define OPENVPN_ENABLE_ASSERT

// EKM vs. TLS_PRF mode
// #define USE_TLS_EKM

#if !defined(USE_TLS_AUTH) && !defined(USE_TLS_CRYPT)
// #define USE_TLS_AUTH
// #define USE_TLS_CRYPT
#define USE_TLS_CRYPT_V2
#endif

#define OPENVPN_INSTRUMENTATION

// Data limits for Blowfish and other 64-bit block-size ciphers
#ifndef BF
#define BF 0
#endif
#define OPENVPN_BS64_DATA_LIMIT 50000
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
#ifndef RENEG
#define RENEG 900
#endif

// feedback
#ifndef FEEDBACK
#define FEEDBACK 1
#else
#define FEEDBACK 0
#endif

// number of threads to use for test
#ifndef N_THREADS
#define N_THREADS 1
#endif

// number of iterations
#ifndef ITER
#define ITER 1000000
#endif

// number of high-level session iterations
#ifndef SITER
#define SITER 1
#endif

// number of retries for failed test
#ifndef N_RETRIES
#define N_RETRIES 5
#endif

// abort if we reach this limit
// #define DROUGHT_LIMIT 100000

#if !defined(VERBOSE) && !defined(QUIET) && ITER <= 10000
#define VERBOSE
#endif

#ifdef VERBOSE
#define OPENVPN_DEBUG_PROTO 2
#define OPENVPN_LOG_SSL(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_SSL(x) // disable
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

#include <openvpn/log/logsimple.hpp>

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
class TestProto : public ProtoContext
{
    typedef ProtoContext Base;

    using Base::is_server;
    using Base::mode;
    using Base::now;

  public:
    using Base::flush;

    typedef Base::PacketType PacketType;

    OPENVPN_EXCEPTION(session_invalidated);

    TestProto(const Base::Config::Ptr &config,
              const SessionStats::Ptr &stats)
        : Base(config, stats),
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
        Base::reset();
    }

    void initial_app_send(const char *msg)
    {
        Base::start();
        const size_t msglen = std::strlen(msg) + 1;
        BufferAllocated app_buf((unsigned char *)msg, msglen, 0);
        copy_progress(app_buf);
        control_send(std::move(app_buf));
        flush(true);
    }

    void app_send_templ_init(const char *msg)
    {
        Base::start();
        const size_t msglen = std::strlen(msg) + 1;
        templ.reset(new BufferAllocated((unsigned char *)msg, msglen, 0));
        flush(true);
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
        if (now() >= Base::next_housekeeping())
        {
            Base::housekeeping();
            return true;
        }
        else
            return false;
    }

    void control_send(BufferPtr &&app_bp)
    {
        app_bytes_ += app_bp->size();
        Base::control_send(std::move(app_bp));
    }

    void control_send(BufferAllocated &&app_buf)
    {
        app_bytes_ += app_buf.size();
        Base::control_send(std::move(app_buf));
    }

    BufferPtr data_encrypt_string(const char *str)
    {
        BufferPtr bp = new BufferAllocated();
        frame->prepare(Frame::READ_LINK_UDP, *bp);
        bp->write((unsigned char *)str, std::strlen(str));
        data_encrypt(*bp);
        return bp;
    }

    void data_encrypt(BufferAllocated &in_out)
    {
        Base::data_encrypt(in_out);
    }

    void data_decrypt(const PacketType &type, BufferAllocated &in_out)
    {
        Base::data_decrypt(type, in_out);
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
        if (Base::invalidated())
            throw session_invalidated(Error::name(Base::invalidation_reason()));
    }

    bool is_state_client_wait_reset_ack() const
    {
        return primary_state() == C_WAIT_RESET_ACK;
    }

    void disable_xmit()
    {
        disable_xmit_ = true;
    }

    std::deque<BufferPtr> net_out;

    DroughtMeasure control_drought;
    DroughtMeasure data_drought;

  private:
    virtual void control_net_send(const Buffer &net_buf)
    {
        if (disable_xmit_)
            return;
        net_bytes_ += net_buf.size();
        net_out.push_back(BufferPtr(new BufferAllocated(net_buf, 0)));
    }

    virtual void control_recv(BufferPtr &&app_bp)
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
        if (is_server())
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
    size_t iteration = 0;
    char progress_[11];
    bool disable_xmit_ = false;
};

class TestProtoClient : public TestProto
{
    typedef TestProto Base;

  public:
    TestProtoClient(const Base::Config::Ptr &config,
                    const SessionStats::Ptr &stats)
        : Base(config, stats)
    {
    }

  private:
    virtual void client_auth(Buffer &buf)
    {
        const std::string username("foo");
        const std::string password("bar");
        Base::write_auth_string(username, buf);
        Base::write_auth_string(password, buf);
    }
};

class TestProtoServer : public TestProto
{
    typedef TestProto Base;

  public:
    OPENVPN_SIMPLE_EXCEPTION(auth_failed);

    TestProtoServer(const Base::Config::Ptr &config,
                    const SessionStats::Ptr &stats)
        : Base(config, stats)
    {
    }

  private:
    virtual void server_auth(const std::string &username,
                             const SafeString &password,
                             const std::string &peer_info,
                             const AuthCert::Ptr &auth_cert)
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
          now(now_arg),
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
        if (a.data_channel_ready())
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
            typename T2::PacketType pt = b.packet_type(*bp);
            if (pt.is_control())
            {
#ifdef VERBOSE
                if (!b.control_net_validate(pt, *bp)) // not strictly necessary since control_net_recv will also validate
                    std::cout << now->raw() << " " << title << " CONTROL PACKET VALIDATION FAILED" << std::endl;
#endif
                b.control_net_recv(pt, std::move(bp));
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
                catch (const std::exception &e)
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
                b.stat().error(Error::KEY_STATE_ERROR);
            }

#ifdef SIMULATE_UDP_AMPLIFY_ATTACK
            if (b.is_state_client_wait_reset_ack())
            {
                b.disable_xmit();
#ifdef VERBOSE
                std::cout << now->raw() << " " << title << " SIMULATE_UDP_AMPLIFY_ATTACK disable client xmit" << std::endl;
#endif
            }
#endif
        }
        b.flush(true);
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
                const unsigned char value = random.randrange(256);
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
    TimePtr now;
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

    virtual void error(const size_t err_type, const std::string *text = nullptr)
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

// execute the unit test in one thread
int test(const int thread_num)
{
    try
    {
        // frame
        Frame::Ptr frame(new Frame(Frame::Context(128, 378, 128, 0, 16, 0)));

        // RNG
        ClientRandomAPI::Ptr rng_cli(new ClientRandomAPI(false));
        ClientRandomAPI::Ptr prng_cli(new ClientRandomAPI(true));
        ServerRandomAPI::Ptr rng_serv(new ServerRandomAPI(false));
        ServerRandomAPI::Ptr prng_serv(new ServerRandomAPI(true));
        MTRand rng_noncrypto;

        // init simulated time
        Time time;
        const Time::Duration time_step = Time::Duration::binary_ms(100);

        // client config files
        const std::string ca_crt = read_text("ca.crt");
        const std::string client_crt = read_text("client.crt");
        const std::string client_key = read_text("client.key");
        const std::string server_crt = read_text("server.crt");
        const std::string server_key = read_text("server.key");
        const std::string dh_pem = read_text("dh.pem");
        const std::string tls_auth_key = read_text("tls-auth.key");
        const std::string tls_crypt_v2_server_key = read_text("tls-crypt-v2-server.key");
        const std::string tls_crypt_v2_client_key = read_text("tls-crypt-v2-client.key");

        // client config
        ClientSSLAPI::Config::Ptr cc(new ClientSSLAPI::Config());
        cc->set_mode(Mode(Mode::CLIENT));
        cc->set_frame(frame);
#ifdef USE_APPLE_SSL
        cc->load_identity("etest");
#else
        cc->load_ca(ca_crt, true);
        cc->load_cert(client_crt);
        cc->load_private_key(client_key);
#endif
        cc->set_tls_version_min(TLS_VER_MIN);
#ifdef VERBOSE
        cc->set_debug_level(1);
#endif
        cc->set_rng(rng_cli);

        // stats
        MySessionStats::Ptr cli_stats(new MySessionStats);
        MySessionStats::Ptr serv_stats(new MySessionStats);

        // client ProtoContext config
        typedef ProtoContext ClientProtoContext;
        ClientProtoContext::Config::Ptr cp(new ClientProtoContext::Config);
        cp->ssl_factory = cc->new_factory();
        CryptoAlgs::allow_default_dc_algs<ClientCryptoAPI>(cp->ssl_factory->libctx(), false, false);
        cp->dc.set_factory(new CryptoDCSelect<ClientCryptoAPI>(cp->ssl_factory->libctx(), frame, cli_stats, prng_cli));
        cp->tlsprf_factory.reset(new CryptoTLSPRFFactory<ClientCryptoAPI>());
        cp->frame = frame;
        cp->now = &time;
        cp->rng = rng_cli;
        cp->prng = prng_cli;
        cp->protocol = Protocol(Protocol::UDPv4);
        cp->layer = Layer(Layer::OSI_LAYER_3);
#ifdef PROTOv2
        cp->enable_op32 = true;
        cp->remote_peer_id = 100;
#endif
        cp->comp_ctx = CompressContext(COMP_METH, false);
        cp->dc.set_cipher(CryptoAlgs::lookup(PROTO_CIPHER));
        cp->dc.set_digest(CryptoAlgs::lookup(PROTO_DIGEST));
#ifdef USE_TLS_EKM
        cp->dc.set_key_derivation(CryptoAlgs::KeyDerivation::TLS_EKM);
#endif
#ifdef USE_TLS_AUTH
        cp->tls_auth_factory.reset(new CryptoOvpnHMACFactory<ClientCryptoAPI>());
        cp->tls_key.parse(tls_auth_key);
        cp->set_tls_auth_digest(CryptoAlgs::lookup(PROTO_DIGEST));
        cp->key_direction = 0;
#endif
#ifdef USE_TLS_CRYPT
        cp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
        cp->tls_key.parse(tls_auth_key);
        cp->set_tls_crypt_algs();
        cp->tls_crypt_ = ClientProtoContext::Config::TLSCrypt::V1;
#endif
#ifdef USE_TLS_CRYPT_V2
        cp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
        cp->set_tls_crypt_algs();
        {
            TLSCryptV2ClientKey tls_crypt_v2_key(cp->tls_crypt_context);
            tls_crypt_v2_key.parse(tls_crypt_v2_client_key);
            tls_crypt_v2_key.extract_key(cp->tls_key);
            tls_crypt_v2_key.extract_wkc(cp->wkc);
        }
        cp->tls_crypt_ = ClientProtoContext::Config::TLSCrypt::V2;
#endif
        cp->pid_mode = PacketIDReceive::UDP_MODE;
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

        // server config
        ClientSSLAPI::Config::Ptr sc(new ClientSSLAPI::Config());
        sc->set_mode(Mode(Mode::SERVER));
        sc->set_frame(frame);
        sc->load_ca(ca_crt, true);
        sc->load_cert(server_crt);
        sc->load_private_key(server_key);
        sc->load_dh(dh_pem);
        sc->set_tls_version_min(TLS_VER_MIN);
        sc->set_rng(rng_serv);
#ifdef VERBOSE
        sc->set_debug_level(1);
#endif

        // server ProtoContext config
        typedef ProtoContext ServerProtoContext;
        ServerProtoContext::Config::Ptr sp(new ServerProtoContext::Config);
        sp->ssl_factory = sc->new_factory();
        sp->dc.set_factory(new CryptoDCSelect<ServerCryptoAPI>(sp->ssl_factory->libctx(), frame, serv_stats, prng_serv));
        sp->tlsprf_factory.reset(new CryptoTLSPRFFactory<ServerCryptoAPI>());
        sp->frame = frame;
        sp->now = &time;
        sp->rng = rng_serv;
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
#ifdef USE_TLS_EKM
        sp->dc.set_key_derivation(CryptoAlgs::KeyDerivation::TLS_EKM);
#endif
#ifdef USE_TLS_AUTH
        sp->tls_auth_factory.reset(new CryptoOvpnHMACFactory<ServerCryptoAPI>());
        sp->tls_key.parse(tls_auth_key);
        sp->set_tls_auth_digest(CryptoAlgs::lookup(PROTO_DIGEST));
        sp->key_direction = 1;
#endif
#if defined(USE_TLS_CRYPT)
        sp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
        sp->tls_key.parse(tls_auth_key);
        sp->set_tls_crypt_algs();
        cp->tls_crypt_ = ClientProtoContext::Config::TLSCrypt::V1;
#endif
#ifdef USE_TLS_CRYPT_V2
        sp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<ClientCryptoAPI>());
        {
            TLSCryptV2ServerKey tls_crypt_v2_key;
            tls_crypt_v2_key.parse(tls_crypt_v2_server_key);
            tls_crypt_v2_key.extract_key(sp->tls_key);
        }
        sp->set_tls_crypt_algs();
        sp->tls_crypt_metadata_factory.reset(new CryptoTLSCryptMetadataFactory());
        sp->tls_crypt_ = ClientProtoContext::Config::TLSCrypt::V2;
#endif
        sp->pid_mode = PacketIDReceive::UDP_MODE;
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
                  << " N=" << cli_proto.negotiations() << '/' << serv_proto.negotiations()
                  << " SH=" << cli_proto.slowest_handshake().raw() << '/' << serv_proto.slowest_handshake().raw()
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

int test_retry(const int thread_num)
{
    const int n_retries = N_RETRIES;
    int ret = 1;
    for (int i = 0; i < n_retries; ++i)
    {
        ret = test(thread_num);
        if (!ret)
            return 0;
        std::cout << "Retry " << (i + 1) << '/' << n_retries << std::endl;
    }
    std::cout << "Failed" << std::endl;
    return ret;
}

int main(int argc, char *argv[])
{
    int ret = 0;
    // process-wide initialization
    InitProcess::Init init;

    // set global MbedTLS debug level
#if defined(USE_MBEDTLS)
    mbedtls_debug_set_threshold(1);
#endif

    if (argc >= 2 && !strcmp(argv[1], "test"))
    {
        const std::string out = SelfTest::crypto_self_test();
        OPENVPN_LOG(out);
        goto out;
    }

#if N_THREADS >= 2
    std::thread *threads[N_THREADS];
    int i;
    for (i = 0; i < N_THREADS; ++i)
    {
        threads[i] = new std::thread([i]()
                                     { test_retry(i); });
    }
    for (i = 0; i < N_THREADS; ++i)
    {
        threads[i]->join();
        delete threads[i];
    }
#else
    ret = test_retry(1);
#endif

out:
    return ret;
}
