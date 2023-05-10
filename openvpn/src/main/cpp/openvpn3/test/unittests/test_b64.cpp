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


#include "test_common.h"

#include <iostream>
#include <memory>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/base64.hpp>

using namespace openvpn;


#ifdef USE_OPENSSL
#include <openssl/bio.h>
#include <openssl/evp.h>
#include <openssl/buffer.h>
#include <stdint.h>

#include <cstdlib>

std::string ssllib_b64enc(const char *text, size_t textlen)
{
    BIO *bio, *b64;

    b64 = BIO_new(BIO_f_base64());
    bio = BIO_new(BIO_s_mem());
    bio = BIO_push(b64, bio);

    BIO_set_flags(bio, BIO_FLAGS_BASE64_NO_NL); // Ignore newlines - write everything in one line
    BIO_write(bio, text, (int)textlen);
    EXPECT_TRUE(BIO_flush(bio) == 1);
    const char *encdata;
    long len = BIO_get_mem_data(bio, &encdata);

    /* If there is nothing to encode OpenSSL gives back a nullptr */
    if (len == 0)
        encdata = "";

    std::string ret(encdata, (size_t)len);
    BIO_free_all(bio);

    return ret;
}
#else
#include <mbedtls/base64.h>

std::string ssllib_b64enc(const char *text, size_t textlen)
{
    size_t olen, outlen;

    // make a pessimistic assumption about length always calculate 3 padding bytes
    outlen = 3 + 4 * (textlen + 3) / 3;

    char *dst = new char[outlen];

    EXPECT_EQ(mbedtls_base64_encode(reinterpret_cast<unsigned char *>(dst),
                                    outlen,
                                    &olen,
                                    reinterpret_cast<const unsigned char *>(text),
                                    textlen),
              0);
    std::string ret(dst, olen);
    delete[] dst;
    return ret;
}
#endif


void b64_test(const Base64 &b64, const std::string &text)
{
    const std::string enc = b64.encode(text);
    std::string dec = b64.decode(enc);
    std::string libenc = ssllib_b64enc(text.c_str(), text.size());

    EXPECT_EQ(text, dec) << "Encode/Decode results differ";
    EXPECT_EQ(enc, libenc) << "Encode differs from Crypto lib result";
}

void b64_test_binary(const Base64 &b64, const char *data, unsigned int len)
{
    auto enc = b64.encode(data, len);

    std::unique_ptr<char[]> decdata(new char[len]);
    size_t decode_len = b64.decode(decdata.get(), len, enc);
    std::string libenc = ssllib_b64enc(data, len);

    EXPECT_EQ(enc, libenc) << "Encode differs from Crypto lib result";

    ASSERT_EQ(decode_len, len) << "Encode/decode length differs";
    ASSERT_EQ(std::vector<uint8_t>(decdata.get(), decdata.get() + decode_len),
              std::vector<uint8_t>(data, data + len))
        << "Encode/Decode results differ";
}

TEST(Base64, tooshortdest)
{
    const Base64 b64;
    auto enc = b64.encode(std::string("abc"));

    char buf[2];
    EXPECT_THROW(b64.decode(buf, 2, enc), Base64::base64_decode_out_of_bound_error);
}

void b64_test_bad_decode(const Base64 &b64, const std::string &text)
{
    std::string dec;
    EXPECT_THROW(b64.decode(dec, text), Base64::base64_decode_error);
}

TEST(Base64, baddecode)
{
    const Base64 b64;

    b64_test_bad_decode(b64, "!@#$%^&*()_");
    b64_test_bad_decode(b64, "plausible deniability");
    b64_test_bad_decode(b64, "plausible != deniability");
    b64_test_bad_decode(b64, "x");
    b64_test_bad_decode(b64, "====");
    b64_test_bad_decode(b64, "xxxx=");
    b64_test_bad_decode(b64, "01*=");
}

TEST(Base64, encode)
{
    const Base64 b64;

    b64_test(b64, "Hello world!");
    b64_test(b64, "привет!");
    b64_test(b64, "ûmbrellaûmbrella");
    b64_test(b64, "一旦在一个蓝色的月亮");
    b64_test(b64, "x");
    b64_test(b64, "one two three");
    b64_test(b64, "aa");
    b64_test(b64, "get your kicks on ... route 66");
    b64_test(b64, "fight the future");
    b64_test(b64, "");
    b64_test(b64, "I want to believe...");
    b64_test(b64, "it was a weather balloon");
    b64_test(b64, "hyperspatial bypass");
    b64_test(b64, "ode to a vogon");
    b64_test(b64, "Acme Travel");
    b64_test(b64, "there's no sunshine when she's gone");
    b64_test(b64, "??????????????????????");
    b64_test(b64, "???????????????????????");
    b64_test(b64, "????????????????????????");
    b64_test(b64, "???x>>>>>>>>>?????????????");
    b64_test(b64, "???x>>>>>>>>>??????????????");
    b64_test(b64, "???x>>>>>>>>>?????????????x>>");
}

TEST(Base64, binary_data)
{

    const Base64 b64;
    std::srand(0);
    for (unsigned int i = 0; i < 20; i++)
    {
        char *data = new char[i];
        for (unsigned int j = 0; j < i; j++)
        {
            data[j] = (char)(std::rand() & 0xff);
        }
        b64_test_binary(b64, data, i);
        delete[] data;
    }
}
