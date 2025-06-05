#include "test_common.hpp"

#include <openvpn/buffer/bufstr.hpp>

#include <cstdint>

using namespace openvpn;

struct align_test
{
    int i = 42;
};

// Test align_as
template <typename T>
void realign_test(BufferAllocated &buf, std::size_t headroom)
{
    constexpr std::size_t at_align = alignof(T);
    const std::size_t at_misalign = headroom;
    const std::size_t at_align_ex = at_misalign & ~(at_align - 1);

    buf.write_alloc(at_misalign);
    buf.read_alloc(at_misalign);
    EXPECT_EQ(buf.offset(), at_misalign);

    align_test at;
    std::memcpy(buf.write_alloc(sizeof(at)), &at, sizeof(at));
    EXPECT_EQ(buf.offset(), at_misalign);

    auto ptr = align_as<align_test>(buf); // Align the buffer contents

    EXPECT_EQ(ptr->i, 42);
    EXPECT_EQ(buf.offset(), at_align_ex); // Nearest aligned offset

    std::cout << "Aligning buffer: " << at_misalign << " -> " << at_align_ex << std::endl;
}

TEST(buffer, buffer_alignas)
{
    constexpr std::size_t test_lim = std::numeric_limits<std::size_t>::digits;
    for (auto i = std::size_t(0); i < test_lim; ++i)
    {
        BufferAllocated buf(test_lim * 2);
        realign_test<align_test>(buf, i);
    }
}

// test equality of Buffer and ConstBuffer
TEST(buffer, const_buffer_ref_1)
{
    static unsigned char hello[] = "hello world";
    Buffer buf(hello, sizeof(hello) - 1, true);
    ConstBuffer &cbuf = const_buffer_ref(buf);
    EXPECT_EQ(cbuf.size(), 11u);
    EXPECT_EQ(buf_to_string(buf), buf_to_string(cbuf));
}

// test equality of BufferAllocatedRc and ConstBuffer
TEST(buffer, const_buffer_ref_2)
{
    BufferAllocated buf(64);
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
    BufferAllocated buf(64);
    buf_append_string(buf, "hello world");
    EXPECT_EQ(buf[0], 'h');
    EXPECT_EQ(buf[10], 'd');
    EXPECT_THROW(buf[11], BufferException);
}

// Test read/write access and bounds check
TEST(buffer, alloc_buffer_access2)
{
    BufferAllocated buf(64, BufAllocFlags::CONSTRUCT_ZERO | BufAllocFlags::DESTRUCT_ZERO);
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
    BufferAllocated buf(sizeof(data));

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
    BufferAllocated buf(64);
    buf_append_string(buf, "hello world");

    while (buf.pop_front() != 'd')
        ;
    EXPECT_THROW(buf.pop_front(), BufferException);
}

// Test advance
TEST(buffer, alloc_buffer_advance1)
{
    BufferAllocated buf(64);
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
    BufferAllocated buf(64);
    buf_append_string(buf, data);

    EXPECT_THROW(buf.advance(sizeof(data)), BufferException);
}

// Test advance
TEST(buffer, alloc_buffer_advance3)
{
    constexpr char data[] = "hello world";
    BufferAllocated buf(64);
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
    BufferAllocated buf(64);

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
    BufferAllocated buf(64);

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
    BufferAllocated buf(64);

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
    BufferAllocated buf(64);

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
    BufferAllocated buf(64);
    buf_append_string(buf, data);

    char raw[sizeof(data) - 1];

    buf.read(raw, sizeof(raw));

    EXPECT_EQ(memcmp(raw, data, sizeof(raw)), 0);
}

TEST(buffer, prepend_alloc)
{
    BufferAllocated buf(64);
    buf_append_string(buf, "hello world");
    EXPECT_EQ(buf.offset(), 0u);

    buf.prepend_alloc(5);
    EXPECT_EQ(buf.size(), 16u);
    EXPECT_EQ(buf.remaining(), 48u);
}


TEST(buffer, prepend_alloc_2)
{
    BufferAllocated buf(64);
    EXPECT_EQ(buf.offset(), 0u);
    buf.init_headroom(2);
    EXPECT_EQ(buf.offset(), 2u);
    buf_append_string(buf, "hello world");
    EXPECT_EQ(buf.offset(), 2u);

    buf.prepend_alloc(5);
    EXPECT_EQ(buf.offset(), 0u);
    EXPECT_EQ(buf.size(), 16u);
    EXPECT_EQ(buf.remaining(), 48u);
}


TEST(buffer, prepend_alloc_fits)
{
    BufferAllocated buf(64);
    EXPECT_EQ(buf.offset(), 0u);
    buf.init_headroom(5);
    EXPECT_EQ(buf.offset(), 5u);
    buf_append_string(buf, "hello world");
    EXPECT_EQ(buf.offset(), 5u);

    buf.prepend_alloc(5);
    EXPECT_EQ(buf.offset(), 0u);
    EXPECT_EQ(buf.size(), 16u);
    EXPECT_EQ(buf.remaining(), 48u);
}

TEST(buffer, prepend_alloc_fail)
{
    BufferAllocated buf(11);
    buf_append_string(buf, "hello world");

    EXPECT_THROW(buf.prepend_alloc(5), std::exception);
    EXPECT_EQ(buf.size(), 11u);
    EXPECT_EQ(buf.remaining(), 0u);
}

TEST(buffer, prepend_alloc_fail2)
{
    BufferAllocated buf(14);
    buf_append_string(buf, "hello world");

    EXPECT_THROW(buf.prepend_alloc(5), std::exception);
    EXPECT_EQ(buf.size(), 11u);
    EXPECT_EQ(buf.remaining(), 3u);
}

TEST(buffer, realign)
{
    BufferAllocated buf(64);
    buf_append_string(buf, "hello world");

    buf.advance(5);
    EXPECT_EQ(buf.c_data_raw()[0], 'h');

    buf.realign(0);

    EXPECT_EQ(buf[0], ' ');
    EXPECT_EQ(buf[5], 'd');
    EXPECT_THROW(buf[6], BufferException);
    EXPECT_EQ(buf.size(), 6u);
    EXPECT_EQ(buf.c_data_raw()[0], ' ');
}

TEST(buffer, realign2)
{
    BufferAllocated buf(64);
    buf_append_string(buf, "hello world");

    EXPECT_EQ(buf.c_data_raw()[0], 'h');

    buf.realign(5);

    EXPECT_EQ(buf.c_data_raw()[5], 'h');
    EXPECT_EQ(buf[0], 'h');
    EXPECT_EQ(buf.size(), 11u);
}

TEST(buffer, realign3)
{
    BufferAllocated buf(11);
    buf_append_string(buf, "hello world");

    EXPECT_EQ(buf.c_data_raw()[0], 'h');

    buf.realign(5);

    EXPECT_EQ(buf.c_data_raw()[5], 'h');
    EXPECT_EQ(buf[0], 'h');
    EXPECT_EQ(buf.size(), 11u);
    EXPECT_EQ(buf.offset(), 5u);
}

TEST(buffer, realign4)
{
    BufferAllocated buf(32);
    buf.realign(7u);
    buf_append_string(buf, "hello world");
    EXPECT_EQ(buf.offset(), 7u);
    buf.realign(0);

    EXPECT_EQ(buf.c_data_raw()[0], 'h');
    EXPECT_EQ(buf[0], 'h');
    EXPECT_EQ(buf.offset(), 0);
}

/*
   We need to be sure the object is in a useable state after a move operation. This
   reflects implied expectations in our codebase and does not violate the standard. The
   following tests are to ensure that the object is in a usable state after a move
   operation. The invariants of the object will be checked in unit tests post-move and
   the PR and relevant notes will be noted in Coverity and the codebase. The goal is
   to ensure such use cases do not lead to undefined behavior. The preexisting move
   implementation is correct and the object is in a valid state post-move. This set
   of tests seeks to ensure that stays true.

   The standard says:

   C++11 Standard, Section 12.8/32: "If the parameter is a non-const lvalue reference
   to a non-volatile object type or a non-const rvalue reference to a non-volatile
   object type, the implicit move constructor ([class.copy.ctor], [class.copy.ctor]/2)
   and the implicit move assignment operator ([class.copy.assign], [class.copy.assign]/2)
   are invoked to initialize the parameter object or to assign to it, respectively. The
   object referred to by the rvalue expression is guaranteed to be left in a valid but
   unspecified state."
*/

TEST(buffer, invariants_after_move_safe)
{
    BufferAllocated buf(32);
    buf_append_string(buf, "hello world");

    BufferAllocated buf2(std::move(buf));

    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.size(), 0u);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.capacity(), 0u);
    // coverity[USE_AFTER_MOVE]
    EXPECT_THROW(buf[0], BufferException);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.c_data(), nullptr);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.c_data_raw(), nullptr);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.data(), nullptr);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.data_raw(), nullptr);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.offset(), 0u);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf.remaining(), 0u);
}

TEST(buffer, push_back_after_move_safe)
{
    BufferAllocated buf(32);
    buf_append_string(buf, "hello world");

    BufferAllocated buf2(std::move(buf));
    buf.realloc(11);
    buf.push_back('X');

    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf2.size(), 11u);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf2[0], 'h');
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf2[10], 'd');
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf[0], 'X');
}

TEST(buffer, append_after_move_safe)
{
    BufferAllocated buf(32);
    buf_append_string(buf, "hello world");

    BufferAllocated buf2(std::move(buf));
    auto buf3 = BufferAllocated(32);
    buf_append_string(buf3, "hello again");
    buf = buf3;

    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf2.size(), 11u);
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf2[0], 'h');
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf2[10], 'd');
    // coverity[USE_AFTER_MOVE]
    EXPECT_EQ(buf, buf3);
}
