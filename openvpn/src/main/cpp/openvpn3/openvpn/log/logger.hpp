//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2024- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//


#pragma once

namespace openvpn::logging {

/** log message level with the highest priority. Critical messages that should always be shown are in this category */
constexpr int LOG_LEVEL_ERROR = 0;
/** log message level with high/normal priority. These are messages that are shown in normal operation */
constexpr int LOG_LEVEL_INFO = 1;
/** log message with verbose priority. These are still part of normal operation when higher logging verbosity is
 requested */
constexpr int LOG_LEVEL_VERB = 2;
/** debug log message priority. Only messages that are useful for a debugging a feature should fall into this
 * category */
constexpr int LOG_LEVEL_DEBUG = 3;
/** trace log message priority. Message that are normally even considered too verbose for the debug level priority
 * belong to this category. Messages that are otherwise often commented out in the code, belong here. */
constexpr int LOG_LEVEL_TRACE = 4;

/**
 * A class that simplifies the logging with different verbosity. It is
 * intended to be either used as a base class or preferably as a member.
 * The member can be either a normal member or static member depending
 * if setting the loglevel should affect all instances of the class or
 * only the current one.
 *
 * e.g.:
 *
 *      static inline logging::Logger<logging::LOG_LEVEL_INFO, logging::LOG_LEVEL_VERB> log_;
 *
 * and then when logging in the class use
 *
 * @tparam DEFAULT_LOG_LEVEL      the default loglevel for this class
 * @tparam MAX_LEVEL            the maximum loglevel that will be printed. Logging with higher
 *                              verbosity will be disabled by using if constexpr expressions.
 *                              Will be ignored and set to DEFAULT_LOG_LEVEL if DEFAULT_LOG_LEVEL
 *                              is higher than MAX_LEVEL.
 *
 *                              This allows to customise compile time maximum verbosity.
 */
template <int DEFAULT_LOG_LEVEL, int MAX_LEVEL = LOG_LEVEL_DEBUG>
class Logger
{
  public:
    static constexpr int max_log_level = std::max(MAX_LEVEL, DEFAULT_LOG_LEVEL);
    static constexpr int default_log_level = DEFAULT_LOG_LEVEL;


    //! return the current logging level for all logging
    int log_level() const
    {
        return current_log_level;
    }

    //! set the log level for all loggigng
    void set_log_level(int level)
    {
        current_log_level = level;
    }

    //! return the current prefix all logging
    std::string log_prefix() const
    {
        return prefix_;
    }

    //! set the log prefix for all loggigng
    void set_log_prefix(const std::string &prefix)
    {
        prefix_ = prefix;
    }

    /**
     * Prints a log message for tracing if the log level
     * is at least LEVEL.
     * @param msg   the message to print
     */
    template <int LEVEL, typename T>
    void log(T &&msg) const
    {
        /* this ensures that the function is empty if MAX_LEVEL excludes this level */
        if constexpr (max_log_level >= LEVEL)
        {
            if (current_log_level >= LEVEL)
                OPENVPN_LOG(prefix_ << std::forward<T>(msg));
        }
    }

    /**
     * Prints a log message for tracing if the log level
     * is at least TRACE (=4)
     * @param msg   the message to print
     */
    template <typename T>
    void log_trace(T &&msg) const
    {
        log<LOG_LEVEL_TRACE>(std::forward<T>(msg));
    }

    /**
     * Prints a log message for debugging only info  if the log level
     * is at least DEBUG (=3)
     * @param msg   the message to print
     */
    template <typename T>
    void log_debug(T &&msg) const
    {
        log<LOG_LEVEL_DEBUG>(std::forward<T>(msg));
    }

    /**
     * Prints a log message for general info  if the log level
     * is at least INFO (=1)
     * @param msg   the message to print
     */
    template <typename T>
    void log_info(T &&msg) const
    {
        log<LOG_LEVEL_INFO>(std::forward<T>(msg));
    }

    /**
     * Prints a verbose log message if the log level is at least VERB (=2)
     * @param msg   the message to log
     */
    template <typename T>
    void log_verbose(T &&msg) const
    {
        log<LOG_LEVEL_VERB>(std::forward<T>(msg));
    }

    /**
     * Logs an error message that should almost always be logged
     * @param msg   the message to log
     */
    template <typename T>
    void log_error(T &&msg) const
    {
        log<LOG_LEVEL_ERROR>(std::forward<T>(msg));
    }

  protected:
    //! configured loglevel
    int current_log_level = DEFAULT_LOG_LEVEL;
    std::string prefix_;
};

/**
 * A mixin class that can be used as base class to expose the setting and getting of the log level publicly but not expose
 * the log methods themselves. Class parameters are the same as for \class Logger
 */
template <int DEFAULT_LOG_LEVEL, int MAX_LEVEL = LOG_LEVEL_TRACE, typename TagT = std::nullptr_t>
class LoggingMixin
{
  public:
    //! return the current logging level for all logging
    static int log_level()
    {
        return log_.log_level();
    }

    //! set the log level for all loggigng
    static void set_log_level(int level)
    {
        log_.set_log_level(level);
    }

    static constexpr int max_log_level = logging::Logger<DEFAULT_LOG_LEVEL, MAX_LEVEL>::max_log_level;
    static constexpr int default_log_level = logging::Logger<DEFAULT_LOG_LEVEL, MAX_LEVEL>::default_log_level;

  protected:
    static inline logging::Logger<DEFAULT_LOG_LEVEL, MAX_LEVEL> log_;
};


/* Log helper macros that allow to not instantiate/execute the code that builds the log messsage if the message is
 * not logged or MAX_LEVEL is not compiled. This are not as nice as using the log_ members methods but are nicer than
 * other #defines that do not use if constexpr */

/**
 * Logging macro that logs with VERB verbosity using the logger named logger
 *
 * The macro tries very hard to avoid executing the
 * code that is inside args when logging is not happening
 */
#define LOGGER_LOG(VERB, logger, args)                                                       \
    do                                                                                       \
    {                                                                                        \
        if constexpr (decltype(logger)::max_log_level >= openvpn::logging::LOG_LEVEL_##VERB) \
        {                                                                                    \
            if (logger.log_level() >= openvpn::logging::LOG_LEVEL_##VERB)                    \
            {                                                                                \
                std::ostringstream _ovpn_log_ss;                                             \
                _ovpn_log_ss << args;                                                        \
                logger.log_info(_ovpn_log_ss.str());                                         \
            }                                                                                \
        }                                                                                    \
    } while (0)

#define LOGGER_LOG_INFO(logger, args) LOGGER_LOG(INFO, logger, args)
#define LOGGER_LOG_VERBOSE(logger, args) LOGGER_LOG(VERB, logger, args)
#define LOGGER_LOG_DEBUG(logger, args) LOGGER_LOG(DEBUG, logger, args)
#define LOGGER_LOG_TRACE(logger, args) LOGGER_LOG(TRACE, logger, args)
#define LOGGER_LOG_ERROR(logger, args) LOGGER_LOG(ERROR, logger, args)

/**
 * These are convenience macros for classes that use the LoggingMixin to avoid specifying the logger
 *
 * The spelling has been chosen to avoid conflicts with syslog.h log level
 * constants LOG_INFO and LOG_DEBUG.
 */
#define OVPN_LOG_ERROR(args) LOGGER_LOG_ERROR(log_, args)
#define OVPN_LOG_INFO(args) LOGGER_LOG_INFO(log_, args)
#define OVPN_LOG_VERBOSE(args) LOGGER_LOG_VERBOSE(log_, args)
#define OVPN_LOG_DEBUG(args) LOGGER_LOG_DEBUG(log_, args)
#define OVPN_LOG_TRACE(args) LOGGER_LOG_TRACE(log_, args)

} // namespace openvpn::logging
