#include "test_common.h"

#include <openvpn/buffer/bufstr.hpp>

using namespace openvpn;

// test equality of Buffer and ConstBuffer
TEST(buffer, const_buffer_ref_1)
{
    static unsigned char hello[] = "hello world";
    Buffer buf(hello, sizeof(hello) - 1, true);
    ConstBuffer &cbuf = const_buffer_ref(buf);
    EXPECT_EQ(cbuf.size(), 11u);
    EXPECT_EQ(buf_to_string(buf), buf_to_string(cbuf));
}

// test equality of BufferAllocated and ConstBuffer
TEST(buffer, const_buffer_ref_2)
{
    BufferAllocated buf(64, 0);
    buf_append_string(buf, "hello world");
    ConstBuffer &cbuf = const_buffer_ref(buf);
    EXPECT_EQ(cbuf.size(), 11u);
    EXPECT_EQ(buf_to_string(buf), buf_to_string(cbuf));
}

// test ConstBufferType with an explicitly const type
TEST(buffer, my_const_buffer_1)
{
    typedef ConstBufferType<const char> MyConstBuffer;
    static const char hello[] = "hello world";
    MyConstBuffer cbuf(hello, sizeof(hello) - 1, true);
    EXPECT_EQ(cbuf.size(), 11u);
    EXPECT_EQ(std::string(cbuf.c_data(), cbuf.size()), "hello world");
}

// Test read access and bounds check on ConstBufferType
TEST(buffer, const_buffer_access1)
{
    constexpr char data[] = "hello world";
    ConstBufferType<char> cbuf(data, sizeof(data) - 1, true);
    EXPECT_EQ(cbuf[0], 'h');
    EXPECT_EQ(cbuf[10], 'd');
    EXPECT_THROW(cbuf[11], BufferException);
}

// Test read access and bounds check on ConstBufferType
TEST(buffer, const_buffer_access2)
{
    constexpr char data[] = "hello world";
    ConstBufferType<char> cbuf(data, sizeof(data) - 1, true);

    while (cbuf.empty() == false)
    {
        auto back = cbuf[cbuf.size() - 1];
        EXPECT_EQ(cbuf.pop_back(), back);
    }

    EXPECT_THROW(cbuf.pop_back(), BufferException);
    EXPECT_THROW(cbuf[0], BufferException);
    EXPECT_THROW(cbuf[1], BufferException);
    EXPECT_THROW(cbuf[11], BufferException);
    EXPECT_THROW(cbuf[12], BufferException);
}

// Test read access and bounds check on ConstBufferType
TEST(buffer, const_buffer_access3)
{
    constexpr char data[] = "hello world";
    ConstBufferType<char> cbuf(data, sizeof(data) - 1, true);

    while (cbuf.empty() == false)
    {
        auto front = cbuf[0];
        EXPECT_EQ(cbuf.pop_front(), front);
    }

    EXPECT_THROW(cbuf.pop_front(), BufferException);
    EXPECT_THROW(cbuf[0], BufferException);
    EXPECT_THROW(cbuf[1], BufferException);
    EXPECT_THROW(cbuf[11], BufferException);
    EXPECT_THROW(cbuf[12], BufferException);
}

// Test read access and bounds check
TEST(buffer, buffer_access1)
{
    char data[] = "hello world";
    BufferType<char> buf(data, sizeof(data) - 1, true);
    EXPECT_EQ(buf[0], 'h');
    EXPECT_EQ(buf[10], 'd');
    EXPECT_THROW(buf[11], BufferException);
}

// Test read/write access and bounds check
TEST(buffer, buffer_access2)
{
    char data[] = "hello world";
    BufferType<char> buf(data, sizeof(data) - 1, true);
    buf[0] = 'j';
    EXPECT_EQ(buf[0], 'j');
    EXPECT_EQ(buf[4], 'o');
    EXPECT_THROW(buf[-1], BufferException);
    EXPECT_THROW(buf[20], BufferException);
}

// Test push/pop and bounds check
TEST(buffer, buffer_access3)
{
    char data1[] = "hello world";
    char data2[sizeof(data1)];
    BufferType<char> buf1(data1, sizeof(data1) - 1, true);
    BufferType<char> buf2(data2, sizeof(data2) - 1, false);

    auto items = buf1.size();
    while (items--)
    {
        buf2.push_back(buf1.pop_front());
    }

    buf2[0] = 'j';
    EXPECT_EQ(buf2[0], 'j');
    EXPECT_EQ(buf2[4], 'o');
    EXPECT_THROW(buf2[-1], BufferException);
    EXPECT_THROW(buf2[20], BufferException);
}

// Test push/pop and bounds check
TEST(buffer, buffer_access4)
{
    char data1[] = "hello world";
    char data2[sizeof(data1)];
    BufferType<char> buf1(data1, sizeof(data1) - 1, true);
    BufferType<char> buf2(data2, sizeof(data2) - 1, false);

    auto items = buf1.size();
    while (items--)
    {
        buf2.push_back(buf1.pop_front());
    }

    items = buf2.size();
    while (items--)
    {
        buf1.push_front(buf2.pop_back());
    }

    buf1[0] = 'j';
    EXPECT_EQ(buf1[0], 'j');
    EXPECT_EQ(buf1[4], 'o');
    EXPECT_THROW(buf1[-1], BufferException);
    EXPECT_THROW(buf1[20], BufferException);
}

// Test read access and bounds check
TEST(buffer, alloc_buffer_access1)
{
    BufferAllocated buf(64, 0);
    buf_append_string(buf, "hello world");
    EXPECT_EQ(buf[0], 'h');
    EXPECT_EQ(buf[10], 'd');
    EXPECT_THROW(buf[11], BufferException);
}

// Test read/write access and bounds check
TEST(buffer, alloc_buffer_access2)
{
    BufferAllocated buf(64, BufferAllocated::CONSTRUCT_ZERO | BufferAllocated::DESTRUCT_ZERO);
    buf_append_string(buf, "hello world");

    buf[0] = 'j';
    EXPECT_EQ(buf[0], 'j');
    EXPECT_EQ(buf[4], 'o');
    EXPECT_THROW(buf[-1], BufferException);
    EXPECT_THROW(buf[20], BufferException);
}

// Test read/write access and bounds check
TEST(buffer, alloc_buffer_access3)
{
    char data[] = "hello world";
    BufferType<char> buf1(data, sizeof(data) - 1, true);
    BufferAllocated buf(sizeof(data), 0);

    auto items = buf1.size();
    while (items--)
    {
        buf.push_back(buf1.pop_front());
    }

    buf[0] = 'j';
    EXPECT_EQ(buf[0], 'j');
    EXPECT_EQ(buf[4], 'o');
    EXPECT_THROW(buf[-20], BufferException);
    EXPECT_THROW(buf[20], BufferException);
}

// Test pop_front
TEST(buffer, alloc_buffer_pop_front)
{
    BufferAllocated buf(64, 0);
    buf_append_string(buf, "hello world");

    while (buf.pop_front() != 'd')
        ;
    EXPECT_THROW(buf.pop_front(), BufferException);
}

// Test advance
TEST(buffer, alloc_buffer_advance1)
{
    BufferAllocated buf(64, 0);
    buf_append_string(buf, "hello world");

    do
    {
        buf.advance(1);
    } while (buf.front() != 'd');

    EXPECT_EQ(buf[0], 'd');
    EXPECT_EQ(buf.back(), 'd');
    EXPECT_EQ(buf.pop_front(), 'd');
    EXPECT_THROW(buf.pop_front(), BufferException);
}

// Test advance
TEST(buffer, alloc_buffer_advance2)
{
    constexpr char data[] = "hello world";
    BufferAllocated buf(64, 0);
    buf_append_string(buf, data);

    EXPECT_THROW(buf.advance(sizeof(data)), BufferException);
}

// Test advance
TEST(buffer, alloc_buffer_advance3)
{
    constexpr char data[] = "hello world";
    BufferAllocated buf(64, 0);
    buf_append_string(buf, data);

    buf.advance(sizeof(data) - 2);

    EXPECT_EQ(buf[0], 'd');
    EXPECT_EQ(buf.back(), 'd');
    EXPECT_EQ(buf.pop_front(), 'd');
    EXPECT_THROW(buf.pop_front(), BufferException);
}

// Test remaining()
TEST(buffer, alloc_buffer_remaining)
{
    BufferAllocated buf(64, 0);

    for (auto remaining = buf.remaining();
         remaining > 0;
         --remaining)
    {
        buf.push_back('X');
        EXPECT_EQ(remaining - 1, buf.remaining());
        EXPECT_EQ(buf.back(), 'X');
    }
    EXPECT_THROW(buf.push_back('X'), BufferException);
}

// Test init_headroom()
TEST(buffer, alloc_buffer_init_headroom)
{
    BufferAllocated buf(64, 0);

    EXPECT_EQ(buf.remaining(), 64);
    buf.init_headroom(32);
    EXPECT_EQ(buf.remaining(), 32);

    for (auto remaining = buf.remaining();
         remaining > 0;
         --remaining)
    {
        buf.push_back('X');
        EXPECT_EQ(remaining - 1, buf.remaining());
        EXPECT_EQ(buf.back(), 'X');
    }
    EXPECT_THROW(buf.push_back('X'), BufferException);
}

// Test reset_offset()
TEST(buffer, alloc_buffer_reset_offset)
{
    BufferAllocated buf(64, 0);

    EXPECT_EQ(buf.remaining(), 64);

    for (auto remaining = buf.remaining();
         remaining > 0;
         --remaining)
    {
        buf.push_back('X');
        EXPECT_EQ(remaining - 1, buf.remaining());
        EXPECT_EQ(buf.back(), 'X');
    }
    EXPECT_THROW(buf.push_back('X'), BufferException);

    buf.reset_offset(32);
    EXPECT_EQ(0, buf.remaining());

    buf.reset_offset(16);
    EXPECT_EQ(0, buf.remaining());
}

// Test reset_size()
TEST(buffer, alloc_buffer_reset_size)
{
    BufferAllocated buf(64, 0);

    EXPECT_EQ(buf.remaining(), 64);

    for (auto remaining = buf.remaining();
         remaining > 0;
         --remaining)
    {
        buf.push_back('X');
        EXPECT_EQ(remaining - 1, buf.remaining());
        EXPECT_EQ(buf.back(), 'X');
    }
    EXPECT_THROW(buf.push_back('X'), BufferException);
    buf.reset_size();
    EXPECT_THROW(buf.back(), BufferException);
    buf.push_back('X');
    EXPECT_EQ(buf.back(), 'X');
}

// Test read()
TEST(buffer, alloc_buffer_read1)
{
    constexpr char data[] = "hello world";
    BufferAllocated buf(64, 0);
    buf_append_string(buf, data);

    char raw[sizeof(data) - 1];

    buf.read(raw, sizeof(raw));

    EXPECT_EQ(memcmp(raw, data, sizeof(raw)), 0);
}
