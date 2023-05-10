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

// These are our fundamental Time and Time Duration classes.
// We normally deal with time in units of 1/1024 of a second.
// This allows us to use 32-bit values to represent most time
// and time duration values, but still gives us reasonable
// accuracy.  Using units of 1/1024 of a second vs. straight
// milliseconds gives us an advantage of not needing to do
// very much integer multiplication and division which can
// help us on platforms such as ARM that lack integer division
// instructions.  Note that the data type used to store the time
// is an oulong, so it will automatically expand to 64 bits on
// 64-bit machines (see olong.hpp).  Using a 32-bit
// data type for time durations is normally fine for clients,
// but imposes a wraparound limit of ~ 48 days.  Servers
// should always use a 64-bit data type to avoid this
// limitation.

// This code was originally designed to be efficient on 32-bit
// processors.  On 64-bit processors, define OPENVPN_TIME_NO_BASE
// to optimize out the base_ variable.  This also has the benefit
// of allowing Time to represent any arbitrary time_t value.

#ifndef OPENVPN_TIME_TIME_H
#define OPENVPN_TIME_TIME_H

#include <limits>
#include <cstdint> // for std::uint32_t, uint64_t

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/olong.hpp>
#include <openvpn/common/to_string.hpp>

#ifdef OPENVPN_PLATFORM_WIN
#include <time.h>    // for ::time() on Windows
#include <windows.h> // for GetTickCount
#else
#include <sys/time.h> // for ::time() and ::gettimeofday()
#endif

namespace openvpn {
OPENVPN_SIMPLE_EXCEPTION(get_time_error);

template <typename T>
class TimeType
{
  public:
    enum
    {
        prec = 1024
    };
    typedef ::time_t base_type;
    typedef T type;

    class Duration
    {
        friend class TimeType;

      public:
        static Duration seconds(const T v)
        {
            return Duration(v * prec);
        }
        static Duration binary_ms(const T v)
        {
            return Duration(v);
        }
        static Duration infinite()
        {
            return Duration(std::numeric_limits<T>::max());
        }

        static Duration milliseconds(const T v)
        {
            // NOTE: assumes that prec == 1024
            // Also note that this might wrap if v is larger than 1/3 of max size of T
            return Duration(v + (v * T(3) / T(128)));
        }

        Duration() noexcept
            : duration_(T(0))
        {
        }

        bool defined() const
        {
            return duration_ != T(0);
        }
        bool operator!() const
        {
            return duration_ == T(0);
        }
        bool is_infinite() const
        {
            return duration_ == std::numeric_limits<T>::max();
        }
        bool enabled() const
        {
            return defined() && !is_infinite();
        }
        void set_infinite()
        {
            duration_ = std::numeric_limits<T>::max();
        }
        void set_zero()
        {
            duration_ = T(0);
        }

        Duration operator+(const Duration &d) const
        {
            if (is_infinite() || d.is_infinite())
                return infinite();
            else
                return Duration(duration_ + d.duration_);
        }

        Duration operator+(const int delta) const
        {
            T duration = duration_;
            if (delta >= 0)
                duration += delta;
            else
            {
                const unsigned int ndelta = -delta;
                if (duration_ >= ndelta)
                    duration -= ndelta;
                else
                    duration = 0;
            }
            return Duration(duration);
        }

        Duration operator*(const unsigned int mult) const
        {
            return Duration(duration_ * mult);
        }

        Duration &operator+=(const Duration &d)
        {
            if (is_infinite() || d.is_infinite())
                set_infinite();
            else
                duration_ += d.duration_;
            return *this;
        }

        void min(const Duration &d)
        {
            if (d.duration_ < duration_)
                duration_ = d.duration_;
        }

        void max(const Duration &d)
        {
            if (d.duration_ > duration_)
                duration_ = d.duration_;
        }

        Duration operator-(const Duration &d) const
        {
            if (d.duration_ >= duration_)
                return Duration(0);
            else if (is_infinite())
                return Duration::infinite();
            else
                return Duration(duration_ - d.duration_);
        }

        Duration &operator-=(const Duration &d)
        {
            if (d.duration_ >= duration_)
                set_zero();
            else if (!is_infinite())
                duration_ -= d.duration_;
            return *this;
        }

        T to_seconds() const
        {
            return duration_ / prec;
        }
        T to_binary_ms() const
        {
            return duration_;
        }

        T to_milliseconds() const
        {
            // NOTE: assumes that prec == 1024
            // Also note that this might wrap if duration_ is larger than 1/3 of max size of T
            return duration_ - (duration_ * T(3) / T(128));
        }

        double to_double() const
        {
            return double(duration_) / double(prec);
        }

        T raw() const
        {
            return duration_;
        }

#define OPENVPN_DURATION_REL(OP)              \
    bool operator OP(const Duration &d) const \
    {                                         \
        return duration_ OP d.duration_;      \
    }
        OPENVPN_DURATION_REL(==)
        OPENVPN_DURATION_REL(!=)
        OPENVPN_DURATION_REL(>)
        OPENVPN_DURATION_REL(<)
        OPENVPN_DURATION_REL(>=)
        OPENVPN_DURATION_REL(<=)
#undef OPENVPN_DURATION_REL

      private:
        explicit Duration(const T duration)
            : duration_(duration)
        {
        }

        T duration_;
    };

    TimeType() noexcept
        : time_(T(0))
    {
    }

    static TimeType zero()
    {
        return TimeType(T(0));
    }
    static TimeType infinite()
    {
        return TimeType(std::numeric_limits<T>::max());
    }

    static TimeType from_seconds_since_epoch(const time_t t)
    {
        if (t <= base_)
            return TimeType(T(1));
        else
            return TimeType(T((t - base_) * prec));
    }

    bool is_infinite() const
    {
        return time_ == std::numeric_limits<T>::max();
    }

    void reset()
    {
        time_ = 0;
    }
    void set_infinite()
    {
        time_ = std::numeric_limits<T>::max();
    }

    bool defined() const
    {
        return time_ != 0;
    }
    bool operator!() const
    {
        return time_ == 0;
    }

    base_type seconds_since_epoch() const
    {
        return base_ + time_ / prec;
    }
    std::uint64_t nanoseconds_since_epoch() const
    {
        return base_ * 1000000000ull + time_ * (1000000000ull / prec);
    }
    T fractional_binary_ms() const
    {
        return time_ % prec;
    }

    static TimeType now()
    {
        return TimeType(now_());
    }

    void update()
    {
        time_ = now_();
    }

    TimeType operator+(const Duration &d) const
    {
        if (is_infinite() || d.is_infinite())
            return infinite();
        else
            return TimeType(time_ + d.duration_);
    }

    TimeType &operator+=(const Duration &d)
    {
        if (is_infinite() || d.is_infinite())
            set_infinite();
        else
            time_ += d.duration_;
        return *this;
    }

    Duration operator-(const TimeType &t) const
    {
        if (t.time_ >= time_)
            return Duration(0);
        else if (is_infinite())
            return Duration::infinite();
        else
            return Duration(time_ - t.time_);
    }

    void min(const TimeType &t)
    {
        if (t.time_ < time_)
            time_ = t.time_;
    }

    void max(const TimeType &t)
    {
        if (t.time_ > time_)
            time_ = t.time_;
    }

    long delta_prec(const TimeType &t) const
    {
        return long(time_) - long(t.time_);
    }

    long delta(const TimeType &t) const
    {
        return delta_prec(t) / long(prec);
    }

    double delta_float(const TimeType &t) const
    {
        return (double(time_) - double(t.time_)) / double(prec);
    }

    // Return a human-readable number of seconds that *this is ahead of t.
    // t is usually now().
    std::string delta_str(const TimeType &t) const
    {
        if (!defined())
            return "UNDEF-TIME";
        if (is_infinite())
            return "INF";
        const double df = delta_float(t);
        std::string ret;
        if (df >= 0.0)
            ret += '+';
        ret += openvpn::to_string(df);
        return ret;
    }

#define OPENVPN_TIME_REL(OP)                  \
    bool operator OP(const TimeType &t) const \
    {                                         \
        return time_ OP t.time_;              \
    }
    OPENVPN_TIME_REL(==)
    OPENVPN_TIME_REL(!=)
    OPENVPN_TIME_REL(>)
    OPENVPN_TIME_REL(<)
    OPENVPN_TIME_REL(>=)
    OPENVPN_TIME_REL(<=)
#undef OPENVPN_TIME_REL

    T raw() const
    {
        return time_;
    }

    static void reset_base_conditional()
    {
        // on 32-bit systems, reset time base after 30 days
        if (sizeof(T) == 4)
        {
            const base_type newbase = ::time(0);
            if (newbase - base_ >= (60 * 60 * 24 * 30))
                reset_base();
        }
    }

    static void reset_base()
    {
#ifdef OPENVPN_TIME_NO_BASE
        static_assert(sizeof(base_type) >= 8, "OPENVPN_TIME_NO_BASE requires time_t to be 64 bits");
#else
        base_ = ::time(0);
#ifdef OPENVPN_PLATFORM_WIN
#if (_WIN32_WINNT >= 0x0600)
        win_recalibrate((DWORD)::GetTickCount64());
#else
        win_recalibrate(::GetTickCount());
#endif
#endif
#endif
    }

    // number of tenths of a microsecond since January 1, 1601.
    static uint64_t win_time()
    {
        // NOTE: assumes that prec == 1024
        return ((11644473600ULL * uint64_t(prec)) + (uint64_t(base_) * uint64_t(prec)) + uint64_t(now_())) * 78125ULL / 8ULL;
    }

  private:
    explicit TimeType(const T time)
        : time_(time)
    {
    }

#ifdef OPENVPN_PLATFORM_WIN

    static void win_recalibrate(const DWORD gtc)
    {
        gtc_last = gtc;
        gtc_base = ::time(0) - gtc_last / 1000;
    }

    static T now_()
    {
#if (_WIN32_WINNT >= 0x0600)
        const DWORD gtc = (DWORD)::GetTickCount64();
#else
        const DWORD gtc = ::GetTickCount();
#endif
        if (gtc < gtc_last)
            win_recalibrate(gtc);
        const time_t sec = gtc_base + gtc / 1000;
        const unsigned int msec = gtc % 1000;
        return T((sec - base_) * prec + msec * prec / 1000);
    }

    static DWORD gtc_last;
    static time_t gtc_base;

#else

    static T now_()
    {
        ::timeval tv;
        if (::gettimeofday(&tv, nullptr) != 0)
            throw get_time_error();
        return T((tv.tv_sec - base_) * prec + tv.tv_usec * prec / 1000000);
    }

#endif

#ifdef OPENVPN_TIME_NO_BASE
    static constexpr base_type base_ = 0;
#else
    static base_type base_;
#endif

    T time_;
};

#ifdef OPENVPN_PLATFORM_WIN
template <typename T>
DWORD TimeType<T>::gtc_last;
template <typename T>
time_t TimeType<T>::gtc_base;
#endif

#ifndef OPENVPN_TIME_NO_BASE
template <typename T>
typename TimeType<T>::base_type TimeType<T>::base_;
#endif

typedef TimeType<oulong> Time;

typedef Time *TimePtr;

} // namespace openvpn

#endif // OPENVPN_TIME_TIME_H
