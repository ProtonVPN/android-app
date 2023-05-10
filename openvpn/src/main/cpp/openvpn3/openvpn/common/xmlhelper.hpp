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

#pragma once

#include <string>

#include <tinyxml2.h>

namespace openvpn {

class Xml
{
  public:
    OPENVPN_EXCEPTION(xml_parse);

    struct Document : public tinyxml2::XMLDocument
    {
        Document(const std::string &str,
                 const std::string &title)
        {
            if (tinyxml2::XMLDocument::Parse(str.c_str()))
                OPENVPN_THROW(xml_parse, title << " : " << format_error(*this));
        }
    };

    static std::string to_string(const tinyxml2::XMLDocument &doc)
    {
        tinyxml2::XMLPrinter printer;
        doc.Print(&printer);
        return printer.CStr();
    }

    static std::string format_error(const tinyxml2::XMLDocument &doc)
    {
#if OVPN_TINYXML2_HAS_ERROR_NAME
        std::string ret = doc.ErrorName();
#else
        tinyxml2::XMLError error = doc.ErrorID();
        std::string ret{"XMLError " + error};
#endif
#if OVPN_TINYXML2_HAS_ERROR_STR
        const char *es = doc.ErrorStr();
        if (es)
        {
            ret += ' ';
            ret += es;
        }
#else
        const char *es1 = doc.GetErrorStr1();
        const char *es2 = doc.GetErrorStr2();
        if (es1)
        {
            ret += ' ';
            ret += es1;
        }
        if (es2)
        {
            ret += ' ';
            ret += es2;
        }
#endif
        return ret;
    }

    template <typename T, typename... Args>
    static std::string find_text(const tinyxml2::XMLNode *node,
                                 const T &first,
                                 Args... args)
    {
        const tinyxml2::XMLElement *e = find(node, first, args...);
        if (e)
            return e->GetText();
        else
            return std::string();
    }

    template <typename T, typename... Args>
    static const tinyxml2::XMLElement *find(const tinyxml2::XMLNode *node,
                                            const T &first,
                                            Args... args)
    {
        const tinyxml2::XMLElement *e = find(node, first);
        if (e)
            e = find(e, args...);
        return e;
    }

    static const tinyxml2::XMLElement *find(const tinyxml2::XMLNode *node,
                                            const std::string &first)
    {
        return node->FirstChildElement(first.c_str());
    }

    static const tinyxml2::XMLElement *find(const tinyxml2::XMLNode *node,
                                            const char *first)
    {
        return node->FirstChildElement(first);
    }

    static const tinyxml2::XMLElement *find(const tinyxml2::XMLElement *elem)
    {
        return elem;
    }

    static const tinyxml2::XMLElement *next_sibling(const tinyxml2::XMLNode *node,
                                                    const std::string &name)
    {
        return node->NextSiblingElement(name.c_str());
    }

    static const tinyxml2::XMLElement *next_sibling(const tinyxml2::XMLNode *node,
                                                    const char *name)
    {
        return node->NextSiblingElement(name);
    }

    static const tinyxml2::XMLElement *next_sibling(const tinyxml2::XMLNode *node)
    {
        return node->NextSiblingElement();
    }
};
} // namespace openvpn
