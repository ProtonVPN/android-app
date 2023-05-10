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
//

#pragma once

#include <openvpn/common/options.hpp>

namespace openvpn {

struct PeerFingerprint
{
    PeerFingerprint(const std::string &fp, const std::size_t size)
    {
        std::istringstream input(fp);
        input.setf(std::ios_base::hex, std::ios_base::basefield);
        input.unsetf(std::ios_base::skipws);
        fingerprint_.reserve(size);

        unsigned int val;
        if (input >> val && val < 256)
        {
            fingerprint_.emplace_back(val);

            while (input)
            {
                char sep;
                if (input >> sep >> val && sep == ':' && val < 256)
                    fingerprint_.emplace_back(val);
                else
                    break;
            }
        }

        if (fingerprint_.size() != fingerprint_.capacity())
            throw option_error("malformed peer-fingerprint: " + fp);
    }

    explicit PeerFingerprint(const std::vector<uint8_t> &fingerprint)
        : fingerprint_(fingerprint)
    {
    }

    bool operator==(const PeerFingerprint &that) const
    {
        return fingerprint_ == that.fingerprint_;
    }

    std::string str() const
    {
        std::ostringstream output;
        output.setf(std::ios_base::hex, std::ios_base::basefield);
        output.fill('0');
        output.width(2);

        for (const int v : fingerprint_)
            output << v << ':';

        std::string str(output.str());
        if (str.size())
            str.erase(str.size() - 1);

        return str;
    }

  protected:
    std::vector<uint8_t> fingerprint_;
};

/**
 *  Parses the --peer-fingerprint configuration option
 *  and provides the logic to validate an X.509 certificate
 *  against such an option.
 */
struct PeerFingerprints
{
    PeerFingerprints() = default;

    PeerFingerprints(const OptionList &opt, const std::size_t fp_size)
    {
        const auto indices = opt.get_index_ptr("peer-fingerprint");
        if (indices == nullptr)
            return;

        for (const auto i : *indices)
        {
            std::istringstream fps(opt[i].get(1, Option::MULTILINE));
            std::string fp;

            opt[i].touch();
            while (std::getline(fps, fp))
            {
                // Ignore empty lines and comments in fingerprint blocks
                std::string trimmed = string::trim_copy(fp);
                if (trimmed.empty()
                    || string::starts_with(trimmed, "#")
                    || string::starts_with(trimmed, ";"))
                {
                    continue;
                }

                fingerprints_.emplace_back(PeerFingerprint(fp, fp_size));
            }
        }
    }

    bool match(const PeerFingerprint &fp) const
    {
        for (const auto &fingerprint : fingerprints_)
        {
            if (fingerprint == fp)
                return true;
        }

        return false;
    }

    explicit operator bool()
    {
        return !fingerprints_.empty();
    }

  protected:
    std::vector<PeerFingerprint> fingerprints_;
};

} // namespace openvpn
