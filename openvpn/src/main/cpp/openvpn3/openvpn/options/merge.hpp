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

// This class will read a standard OpenVPN config file that might contain
// references to other files, and it will merge the included files into the
// config file, using inline configuration syntax, to produce a single,
// unified config file.

#ifndef OPENVPN_OPTIONS_MERGE_H
#define OPENVPN_OPTIONS_MERGE_H

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/path.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/splitlines.hpp>

namespace openvpn {

class ProfileMerge
{
    // internal flags
    enum
    {
        F_MAY_INCLUDE_KEY_DIRECTION = (1 << 0),
        F_PKCS12 = (1 << 1),
        F_HTTP_PROXY = (1 << 2),
    };

    // limits
    enum
    {
        MAX_FN_LIST_SIZE = 16,
    };

  public:
    OPENVPN_EXCEPTION(merge_error);

    // public status values
    enum Status
    {
        MERGE_UNDEFINED,
        MERGE_SUCCESS,
        MERGE_EXCEPTION,
        MERGE_OVPN_EXT_FAIL,
        MERGE_OVPN_FILE_FAIL,
        MERGE_REF_FAIL,
        MERGE_MULTIPLE_REF_FAIL,
    };

    // merge status
    Status status() const
    {
        return status_;
    }
    const std::string &error() const
    {
        return error_;
    }

    // merge path basename
    const std::string &basename() const
    {
        return basename_;
    }

    // final unified profile
    const std::string &profile_content() const
    {
        return profile_content_;
    }

    // list of all reference paths successfully read
    const std::vector<std::string> &ref_path_list() const
    {
        return ref_succeed_list_;
    }

    // merge status as a string
    const char *status_string() const
    {
        switch (status_)
        {
        case MERGE_UNDEFINED:
            return "MERGE_UNDEFINED";
        case MERGE_SUCCESS:
            return "MERGE_SUCCESS";
        case MERGE_EXCEPTION:
            return "MERGE_EXCEPTION";
        case MERGE_OVPN_EXT_FAIL:
            return "MERGE_OVPN_EXT_FAIL";
        case MERGE_OVPN_FILE_FAIL:
            return "MERGE_OVPN_FILE_FAIL";
        case MERGE_REF_FAIL:
            return "MERGE_REF_FAIL";
        case MERGE_MULTIPLE_REF_FAIL:
            return "MERGE_MULTIPLE_REF_FAIL";
        default:
            return "MERGE_?";
        }
    }

    // allow following of external file references
    enum Follow
    {
        FOLLOW_NONE,
        FOLLOW_PARTIAL,
        FOLLOW_FULL,
    };

    ProfileMerge(const std::string &profile_path,
                 const std::string &profile_ext,
                 const std::string &profile_dir_override,
                 const Follow follow_references,
                 const size_t max_line_len,
                 const size_t max_size)
        : status_(MERGE_UNDEFINED)
    {
        try
        {
            size_t total_size = 0;

            // read the profile
            std::string orig_profile_content;
            std::string profile_dir;
            try
            {
                profile_dir = !profile_dir_override.empty() ? profile_dir_override : path::dirname(profile_path);
                basename_ = path::basename(profile_path);
                const std::string ext = path::ext(basename_);
                if (profile_ext.empty() || string::strcasecmp(ext, profile_ext) == 0)
                {
                    orig_profile_content = read_text_utf8(profile_path, max_size);
                    total_size = orig_profile_content.size();
                }
                else
                {
                    status_ = MERGE_OVPN_EXT_FAIL;
                    error_ = std::string("ERR_PROFILE_NO_OVPN_EXTENSION: ") + basename_;
                    return;
                }
            }
            catch (const file_is_binary &e)
            {
                status_ = MERGE_OVPN_FILE_FAIL;
                error_ = std::string("ERR_PROFILE_FILE_IS_BINARY: ") + e.what();
                return;
            }
            catch (const file_too_large &e)
            {
                status_ = MERGE_OVPN_FILE_FAIL;
                error_ = std::string("ERR_PROFILE_FILE_TOO_LARGE: ") + e.what();
                return;
            }
            catch (const std::exception &e)
            {
                status_ = MERGE_OVPN_FILE_FAIL;
                error_ = std::string("ERR_PROFILE_GENERIC: ") + e.what();
                return;
            }

            // expand the profile
            expand_profile(orig_profile_content, profile_dir, follow_references, max_line_len, max_size, total_size);
        }
        catch (const std::exception &e)
        {
            status_ = MERGE_EXCEPTION;
            error_ = std::string("ERR_PROFILE_GENERIC: ") + e.what();
        }
    }

    static std::string merge(const std::string &profile_path,
                             const std::string &profile_ext,
                             const std::string &profile_dir_override,
                             const Follow follow_references,
                             const size_t max_line_len,
                             const size_t max_size)
    {
        const ProfileMerge pm(profile_path,
                              profile_ext,
                              profile_dir_override,
                              follow_references,
                              max_line_len,
                              max_size);
        if (pm.status() == ProfileMerge::MERGE_SUCCESS)
            return pm.profile_content();
        else
            OPENVPN_THROW(merge_error, pm.status_string() << ": " << pm.error());
    }

  protected:
    ProfileMerge()
        : status_(MERGE_UNDEFINED)
    {
    }

    void expand_profile(const std::string &orig_profile_content,
                        const std::string &profile_dir,
                        const Follow follow_references,
                        const size_t max_line_len,
                        const size_t max_size,
                        size_t total_size)
    {
        if (total_size > max_size)
        {
            status_ = MERGE_EXCEPTION;
            error_ = "ERR_PROFILE_FILE_TOO_LARGE: file too large";
            return;
        }

        status_ = MERGE_SUCCESS;

        SplitLines in(orig_profile_content, max_line_len);
        bool in_multiline = false;
        bool opaque_multiline = false;
        Option multiline;

        profile_content_.reserve(orig_profile_content.length());
        while (in(true))
        {
            if (in.line_overflow())
            {
                status_ = MERGE_EXCEPTION;
                error_ = "ERR_PROFILE_LINE_TOO_LONG: line too long";
                return;
            }
            const std::string &line = in.line_ref();
            bool echo = true;
            if (in_multiline)
            {
                if (OptionList::is_close_tag(line, multiline.ref(0)))
                {
                    multiline.clear();
                    in_multiline = false;
                    opaque_multiline = false;
                }
            }
            else if (!OptionList::ignore_line(line))
            {
                Option opt = Split::by_space<Option, OptionList::LexComment, SpaceMatch, Split::NullLimit>(line);
                if (opt.size())
                {
                    if (OptionList::is_open_tag(opt.ref(0)) && opt.size() == 1)
                    {
                        OptionList::untag_open_tag(opt.ref(0));
                        multiline = opt;
                        in_multiline = true;
                        unsigned int flags = 0; // not used
                        opaque_multiline = is_fileref_directive(multiline.ref(0), flags);
                    }
                    else
                    {
                        unsigned int flags = 0;
                        bool is_fileref = (!opaque_multiline
                                           && opt.size() >= 2
                                           && is_fileref_directive(opt.ref(0), flags));
                        if (is_fileref)
                        {
                            // check if http-proxy directive references a creds file
                            if (flags & F_HTTP_PROXY)
                            {
                                is_fileref = false;
                                if (opt.size() >= 4)
                                {
                                    const std::string authfile = opt.get(3, 256);
                                    if (authfile != "auto" && authfile != "auto-nct"
                                        && authfile != "basic" && authfile != "digest"
                                        && authfile != "ntlm" && authfile != "none")
                                    {
                                        opt.ref(3) = "auto";
                                        profile_content_ += opt.escape(false);
                                        profile_content_ += '\n';
                                        opt.ref(0) = "http-proxy-user-pass";
                                        opt.ref(1) = authfile;
                                        opt.resize(2);
                                        is_fileref = true;
                                    }
                                }
                            }
                        }
                        if (is_fileref)
                        {
                            // found a directive referencing a file

                            // get basename of file and make sure that it doesn't
                            // attempt to traverse directories (unless
                            // follow_references == FOLLOW_FULL)
                            const std::string fn_str = opt.get(1, 256);
                            const std::string fn = (follow_references == FOLLOW_FULL ? fn_str : path::basename(fn_str));
                            if (fn.empty())
                            {
                                echo = false;
                                status_ = MERGE_REF_FAIL;
                                error_ = "ERR_PROFILE_NO_FILENAME: filename not provided";
                            }
                            else if (follow_references != FOLLOW_FULL && !path::is_flat(fn))
                            {
                                echo = false;
                                status_ = MERGE_REF_FAIL;
                                error_ = std::string("ERR_PROFILE_CANT_FOLLOW_LINK: ") + fn;
                                if (ref_fail_list_.size() < MAX_FN_LIST_SIZE)
                                    ref_fail_list_.push_back(fn);
                            }
                            else
                            {
                                std::string path;
                                std::string file_content;
                                bool error = false;
                                try
                                {
                                    if (follow_references == FOLLOW_NONE)
                                    {
                                        status_ = MERGE_EXCEPTION;
                                        error_ = std::string("ERR_PROFILE_CANT_FOLLOW_LINK: ") + fn + ": cannot follow file reference";
                                        return;
                                    }
                                    path = path::join(profile_dir, fn);
                                    file_content = read_text_utf8(path, max_size);
                                    total_size += file_content.size();
                                    if (total_size > max_size)
                                    {
                                        status_ = MERGE_EXCEPTION;
                                        error_ = std::string("ERR_PROFILE_FILE_TOO_LARGE: ") + fn + ": file too large";
                                        return;
                                    }
                                    OptionList::detect_multiline_breakout(file_content, opt.ref(0));
                                }
                                catch (const std::exception &e)
                                {
                                    error = true;
                                    status_ = MERGE_REF_FAIL;
                                    error_ = std::string("ERR_PROFILE_GENERIC: ") + fn + " : " + e.what();
                                    if (ref_fail_list_.size() < MAX_FN_LIST_SIZE)
                                        ref_fail_list_.push_back(fn);
                                }

                                if (!error) // succeeded in reading file?
                                {
                                    // don't echo this line, i.e. opt[], instead expand file_content into profile
                                    echo = false;

                                    // tls-auth or secret directive may include key-direction parameter
                                    if (flags & F_MAY_INCLUDE_KEY_DIRECTION)
                                    {
                                        std::string key_direction;
                                        if (opt.size() >= 3)
                                            key_direction = opt.get(2, 16);
                                        else
                                            key_direction = "bidirectional";
                                        profile_content_ += "key-direction " + key_direction + "\n";
                                    }

                                    // format file_content for appending to profile
                                    {
                                        std::ostringstream os;
                                        const std::string &tag = opt.ref(0);
                                        string::add_trailing(file_content, '\n');
                                        os << '<' << tag << ">\n"
                                           << file_content << "</" << tag << ">\n";
                                        profile_content_ += os.str();
                                    }

                                    // save file we referenced
                                    if (ref_succeed_list_.size() < MAX_FN_LIST_SIZE)
                                        ref_succeed_list_.push_back(path);
                                }
                            }
                        }
                    }
                }
            }
            if (echo)
            {
                profile_content_ += line;
                profile_content_ += '\n';
            }
        }

        // If more than 2 errors occurred, change status to
        // MERGE_MULTIPLE_REF_FAIL and enumerate each failed file.
        if (ref_fail_list_.size() >= 2)
        {
            status_ = MERGE_MULTIPLE_REF_FAIL;
            error_ = "ERR_PROFILE_GENERIC: ";
            for (size_t i = 0; i < ref_fail_list_.size(); ++i)
            {
                if (i)
                    error_ += ", ";
                error_ += ref_fail_list_[i];
            }
        }
    }

    static bool is_fileref_directive(const std::string &d, unsigned int &flags)
    {
        if (d.length() > 0)
        {
            switch (d[0])
            {
            case 'a':
                return d == "auth-user-pass";
            case 'c':
                return d == "ca" || d == "cert" || d == "crl-verify";
            case 'd':
                return d == "dh";
            case 'e':
                return d == "extra-certs";
            case 'h':
                if (d == "http-proxy")
                {
                    flags |= F_HTTP_PROXY;
                    return true;
                }
                return false;
            case 'k':
                return d == "key";
#if 0 // define when we have capability to parse out pkcs12 from profile and add to Keychain (fixme)
	    case 'p':
	      if (d == "pkcs12")
		{
		  flags |= F_PKCS12;
		  return true;
		}
	      return false;
#endif
            case 'r':
                if (d == "relay-extra-ca")
                    return true;
                if (d == "relay-tls-auth")
                {
                    flags |= F_MAY_INCLUDE_KEY_DIRECTION;
                    return true;
                }
                return false;
            case 's':
                if (d == "static-key")
                    return true;
                return false;
            case 't':
                if (d == "tls-auth")
                {
                    flags |= F_MAY_INCLUDE_KEY_DIRECTION;
                    return true;
                }
                if (d == "tls-crypt")
                    return true;
                if (d == "tls-crypt-v2")
                    return true;
                return false;
            }
        }
        return false;
    }

    Status status_;
    std::string profile_content_;
    std::string basename_;
    std::string error_;
    std::vector<std::string> ref_fail_list_;
    std::vector<std::string> ref_succeed_list_;
};

class ProfileMergeFromString : public ProfileMerge
{
  public:
    ProfileMergeFromString(const std::string &profile_content,
                           const std::string &ref_dir,
                           const Follow follow_references,
                           const size_t max_line_len,
                           const size_t max_size)
    {
        try
        {
            // expand the profile
            expand_profile(profile_content,
                           ref_dir,
                           follow_references,
                           max_line_len,
                           max_size,
                           profile_content.size());
        }
        catch (const std::exception &e)
        {
            status_ = MERGE_EXCEPTION;
            error_ = std::string("ERR_PROFILE_GENERIC: ") + e.what();
        }
    }

    static std::string merge(const std::string &profile_content,
                             const std::string &ref_dir,
                             const Follow follow_references,
                             const size_t max_line_len,
                             const size_t max_size)
    {
        const ProfileMergeFromString pm(profile_content,
                                        ref_dir,
                                        follow_references,
                                        max_line_len,
                                        max_size);
        if (pm.status() == ProfileMerge::MERGE_SUCCESS)
            return pm.profile_content();
        else
            OPENVPN_THROW(merge_error, pm.status_string() << ": " << pm.error());
    }
};
} // namespace openvpn

#endif
