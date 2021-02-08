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
//    If not, see <http://www.gnu.org/licenses/>.

// General string-splitting methods.  These methods along with lexical analyzer
// classes (such as those defined in lex.hpp and OptionList::LexComment) can be
// used as a basis for parsers.

#ifndef OPENVPN_COMMON_SPLIT_H
#define OPENVPN_COMMON_SPLIT_H

#include <string>
#include <vector>
#include <utility>

#include <openvpn/common/size.hpp>
#include <openvpn/common/lex.hpp>

namespace openvpn {
  namespace Split {
    enum {
      TRIM_LEADING_SPACES=(1<<0),
      TRIM_SPECIAL=(1<<1), // trims quotes (but respects their content)
    };

    struct NullLimit
    {
      void add_term() {}
    };

    // Split a string using a character (such as ',') as a separator.
    // Types:
    //   V : string vector of return data
    //   LEX : lexical analyzer class such as StandardLex
    //   LIM : limit class such as OptionList::Limits
    // Args:
    //   ret : return data -- a list of strings
    //   input : input string to be split
    //   split_by : separator
    //   flags : TRIM_LEADING_SPACES, TRIM_SPECIAL
    //   max_terms : the size of the returned string list will be, at most, this value + 1.  Pass
    //               ~0 to disable.
    //   lim : an optional limits object such as OptionList::Limits
    template <typename V, typename LEX, typename LIM>
    inline void by_char_void(V& ret, const std::string& input, const char split_by, const unsigned int flags=0, const unsigned int max_terms=~0, LIM* lim=nullptr)
    {
      LEX lex;
      unsigned int nterms = 0;
      std::string term;
      for (std::string::const_iterator i = input.begin(); i != input.end(); ++i)
	{
	  const char c = *i;
	  lex.put(c);
	  if (!lex.in_quote() && c == split_by && nterms < max_terms)
	    {
	      if (lim)
		lim->add_term();
	      ret.push_back(std::move(term));
	      ++nterms;
	      term = "";
	    }
	  else if ((!(flags & TRIM_SPECIAL) || lex.available())
		   && (!(flags & TRIM_LEADING_SPACES) || !term.empty() || !SpaceMatch::is_space(c)))
	    term += c;
	}
      if (lim)
	lim->add_term();
      ret.push_back(std::move(term));
    }

    // convenience method that returns data rather than modifying an in-place argument
    template <typename V, typename LEX, typename LIM>
    inline V by_char(const std::string& input, const char split_by, const unsigned int flags=0, const unsigned int max_terms=~0, LIM* lim=nullptr)
    {
      V ret;
      by_char_void<V, LEX, LIM>(ret, input, split_by, flags, max_terms, lim);
      return ret;
    }

    // Split a string using spaces as a separator.
    // Types:
    //   V : string vector of return data
    //   LEX : lexical analyzer class such as StandardLex
    //   SPACE : class that we use to differentiate between space and non-space chars
    //   LIM : limit class such as OptionList::Limits
    // Args:
    //   ret : return data -- a list of strings
    //   input : input string to be split
    //   lim : an optional limits object such as OptionList::Limits
    template <typename V, typename LEX, typename SPACE, typename LIM>
    inline void by_space_void(V& ret, const std::string& input, LIM* lim=nullptr)
    {
      LEX lex;

      std::string term;
      bool defined = false;
      for (std::string::const_iterator i = input.begin(); i != input.end(); ++i)
	{
	  const char c = *i;
	  lex.put(c);
	  if (lex.in_quote())
	    defined = true;
	  if (lex.available())
	    {
	      const char tc = lex.get();
	      if (!SPACE::is_space(tc) || lex.in_quote())
		{
		  defined = true;
		  term += tc;
		}
	      else if (defined)
		{
		  if (lim)
		    lim->add_term();
		  ret.push_back(std::move(term));
		  term = "";
		  defined = false;
		}
	    }
	}
      if (defined)
	{
	  if (lim)
	    lim->add_term();
	  ret.push_back(std::move(term));
	}
    }

    // convenience method that returns data rather than modifying an in-place argument
    template <typename V, typename LEX, typename SPACE, typename LIM>
    inline V by_space(const std::string& input, LIM* lim=nullptr)
    {
      V ret;
      by_space_void<V, LEX, SPACE, LIM>(ret, input, lim);
      return ret;
    }
  }
} // namespace openvpn

#endif // OPENVPN_COMMON_SPLIT_H
