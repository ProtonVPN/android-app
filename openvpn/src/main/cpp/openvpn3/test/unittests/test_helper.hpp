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

#pragma once

#include <openvpn/log/logbase.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/random/mtrandapi.hpp>

#include <iostream>
#include <gtest/gtest.h>
#include <fstream>
#include <mutex>

namespace openvpn {
  class LogOutputCollector : public LogBase
  {
  public:
    LogOutputCollector() : log_context(this)
    {
    }

    void log(const std::string& l) override
    {
      std::lock_guard<std::mutex> lock(mutex);

      if (output_log)
	std::cout << l;
      if (collect_log)
	out << l;
    }

    /**
     * Return the collected log out
     * @return the log output as string
     */
    std::string getOutput() const
    {
      return out.str();
    }

    /**
     * Allow to access the underlying output stream to direct
     * output from function that want to write to a stream to it
     * @return that will be captured by this log
     */
    std::ostream& getStream()
    {
      return out;
    }

    /**
     * Changes if the logging to stdout should be done
     * @param doOutput
     */
    void setPrintOutput(bool doOutput)
    {
      output_log = doOutput;
    }

    /**
     * Return current state of stdout output
     * @return current state of output
     */
    bool isStdoutEnabled() const
    {
      return output_log;
    }

    /**
     * Starts collecting log output. This will also
     * disable stdout output and clear the collected output if there is any
     */
    void startCollecting()
    {
      collect_log = true;
      output_log = false;
      // Reset our buffer
      out.str(std::string());
      out.clear();
    }

    /**
     * Stops collecting log output. Will reenable stdout output.
     * @return the output collected
     */
    std::string stopCollecting()
    {
      collect_log = false;
      output_log = true;
      return getOutput();
    }

  private:
    bool output_log = true;
    bool collect_log = false;
    std::stringstream out;
    std::mutex mutex{};
    Log::Context log_context;
  };
}

extern openvpn::LogOutputCollector* testLog;

/**
 * Overrides stdout during the run of a function. Primarly for silencing
 * log function that throw an exception when something is wrong
 * @param doLogOutput Use stdout while running
 * @param test_func function to run
 */
inline void override_logOutput(bool doLogOutput, void (* test_func)())
{
  bool previousOutputState = testLog->isStdoutEnabled();
  testLog->setPrintOutput(doLogOutput);
  test_func();
  testLog->setPrintOutput(previousOutputState);
}

/**
 * Reads the file with the expected output and returns it as a
 * string. This function delibrately does not include the
 * ASSERT_EQ call since otherwise gtest will report a the
 * assert failure in this file rather than in the right place
 * @param filename
 * @return
 */
inline std::string getExpectedOutput(const std::string& filename)
{
  auto fullpath = UNITTEST_SOURCE_DIR "/output/" + filename;
  std::ifstream f(fullpath);
  if (!f.good())
    {
      throw std::runtime_error("Error opening file " + fullpath);
    }
  std::string expected_output((std::istreambuf_iterator<char>(f)),
			      std::istreambuf_iterator<char>());
  return expected_output;
}

#ifdef WIN32
#include <windows.h>

inline std::string getTempDirPath(const std::string& fn)
{
  char buf [MAX_PATH];

  EXPECT_NE(GetTempPathA(MAX_PATH, buf), 0);
  return std::string(buf) + fn;
}
#else

inline std::string getTempDirPath(const std::string& fn)
{
  return "/tmp/" + fn;
}

#endif

/**
 * Returns a sorted string join with the delimiter
 * This function modifes the input
 * @param r the array to join
 * @param delim the delimiter to use
 * @return A string joined by delim from the sorted vector r
 */
template<class T>
inline std::string getSortedJoinedString(std::vector<T>& r, const std::string& delim = "|")
{
  std::sort(r.begin(), r.end());
  std::stringstream s;
  std::copy(r.begin(), r.end(), std::ostream_iterator<std::string>(s, delim.c_str()));
  return s.str();
}

namespace detail {
  class line
  {
    std::string data;
  public:
    friend std::istream& operator>>(std::istream& is, line& l)
    {
      std::getline(is, l.data);
      return is;
    }

    operator std::string() const
    { return data; }
  };
}

/**
 * Splits a string into lines and returns them in a sorted output string
 */
inline std::string getSortedString(const std::string& output)
{
  std::stringstream ss{output};

  std::istream_iterator<detail::line> begin{ss};
  std::istream_iterator<detail::line> end;
  std::vector<std::string> lines {begin, end};

  // sort lines
  std::sort(lines.begin(), lines.end());

  // join strings with \n
  std::stringstream s;
  std::copy(lines.begin(), lines.end(), std::ostream_iterator<std::string>(s, "\n"));
  return s.str();
}

/**
 * Predictable RNG that claims to be secure to be used in reproducable unit
 * tests
 */
class FakeSecureRand : public openvpn::RandomAPI
{
public:
  FakeSecureRand(const unsigned char initial=0)
    : next(initial)
  {
  }

  virtual std::string name() const override
  {
    return "FakeRNG";
  }

  virtual bool is_crypto() const override
  {
    return true;
  }

  virtual void rand_bytes(unsigned char *buf, size_t size) override
  {
    rand_bytes_(buf, size);
    //OPENVPN_LOG("RAND: " << openvpn::render_hex(buf, size));
  }

  virtual bool rand_bytes_noexcept(unsigned char *buf, size_t size) override
  {
    rand_bytes(buf, size);
    return true;
  }

private:
  // fake RNG -- just use an incrementing sequence
  void rand_bytes_(unsigned char *buf, size_t size)
  {
    while (size--)
      *buf++ = next++;
  }

  unsigned char next;
};

// googletest is missing the ability to test for specific
// text inside a thrown exception, so we implement it here

#define JY_EXPECT_THROW(statement, expected_exception, expected_text) \
try { \
    statement; \
    OPENVPN_THROW_EXCEPTION("JY_EXPECT_THROW: no exception was thrown " << __FILE__ << ':' << __LINE__); \
} \
catch (const expected_exception& e) \
{ \
  if (std::string(e.what()).find(expected_text) == std::string::npos) \
    OPENVPN_THROW_EXCEPTION("JY_EXPECT_THROW: did not find expected text in exception at " << __FILE__ << ':' << __LINE__); \
}

// googletest ASSERT macros can't be used inside constructors
// or non-void-returning functions, so implement workaround here

#define JY_ASSERT_TRUE(value) \
{ \
  if (!(value)) \
    OPENVPN_THROW_EXCEPTION("JY_ASSERT_TRUE: failure at " << __FILE__ << ':' << __LINE__); \
}

#define JY_ASSERT_FALSE(value) \
{ \
  if (value) \
    OPENVPN_THROW_EXCEPTION("JY_ASSERT_FALSE: failure at " << __FILE__ << ':' << __LINE__); \
}

#define JY_ASSERT_EQ(v1, v2) \
{ \
  if ((v1) != (v2)) \
    OPENVPN_THROW_EXCEPTION("JY_ASSERT_EQ: failure at " << __FILE__ << ':' << __LINE__); \
}

#define JY_ASSERT_NE(v1, v2) \
{ \
  if ((v1) == (v2)) \
    OPENVPN_THROW_EXCEPTION("JY_ASSERT_NE: failure at " << __FILE__ << ':' << __LINE__); \
}
