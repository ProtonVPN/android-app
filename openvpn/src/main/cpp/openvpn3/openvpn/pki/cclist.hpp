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

#ifndef OPENVPN_PKI_CCLIST_H
#define OPENVPN_PKI_CCLIST_H

#include <string>
#include <sstream>
#include <fstream>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/string.hpp>

namespace openvpn {

  // Parse a concatenated list of certs and CRLs (PEM format).
  // Abstracts CertList and CRLList, so can be used with any crypto lib.
  // CertList and CRLList must define Item type.
  template <typename CertList, typename CRLList>
  class CertCRLListTemplate
  {
  public:
    OPENVPN_EXCEPTION(parse_cert_crl_error);

    CertCRLListTemplate() {}

    explicit CertCRLListTemplate(const std::string& content, const std::string& title)
    {
      from_string(content, title, &certs, &crls);
    }

    void parse_pem(const std::string& content, const std::string& title)
    {
      from_string(content, title, &certs, &crls);
    }

    void parse_pem_file(const std::string& filename)
    {
      from_file(filename, &certs, &crls);
    }

    std::string render_pem() const
    {
      return certs.render_pem() + crls.render_pem();
    }

    static void from_istream(std::istream& in, const std::string& title, CertList* cert_list, CRLList* crl_list)
    {
      static const char cert_start[] = "-----BEGIN CERTIFICATE-----";
      static const char cert_end[] = "-----END CERTIFICATE-----";
      static const char crl_start[] = "-----BEGIN X509 CRL-----";
      static const char crl_end[] = "-----END X509 CRL-----";

      enum {
	S_OUTSIDE, // outside of CERT or CRL block
	S_IN_CERT, // in CERT block
	S_IN_CRL,  // in CRL block
      };

      std::string line;
      int state = S_OUTSIDE;
      std::string item = "";
      int line_num = 0;

      while (std::getline(in, line))
	{
	  line_num++;
	  string::trim(line);
	  if (state == S_OUTSIDE)
	    {
	      if (line == cert_start)
		{
		  if (!cert_list)
		    OPENVPN_THROW(parse_cert_crl_error, title << ":" << line_num << " : not expecting a CERT");
		  state = S_IN_CERT;
		}
	      else if (line == crl_start)
		{
		  if (!crl_list)
		    OPENVPN_THROW(parse_cert_crl_error, title << ":" << line_num << " : not expecting a CRL");
		  state = S_IN_CRL;
		}
	    }
	  if (state != S_OUTSIDE)
	    {
	      item += line;
	      item += "\n";
	    }
	  if (state == S_IN_CERT && line == cert_end)
	    {
	      try {
		cert_list->emplace_back(item, title);
	      }
	      catch (const std::exception& e)
		{
		  OPENVPN_THROW(parse_cert_crl_error, title << ":" << line_num << " : error parsing CERT: " << e.what());
		}
	      state = S_OUTSIDE;
	      item = "";
	    }
	  if (state == S_IN_CRL && line == crl_end)
	    {
	      try {
		crl_list->emplace_back(item);
	      }
	      catch (const std::exception& e)
		{
		  OPENVPN_THROW(parse_cert_crl_error, title << ":" << line_num << " : error parsing CRL: " << e.what());
		}
	      state = S_OUTSIDE;
	      item = "";
	    }
	}
      if (state != S_OUTSIDE)
	OPENVPN_THROW(parse_cert_crl_error, title << " : CERT/CRL content ended unexpectedly without END marker");
    }

    static void from_string(const std::string& content, const std::string& title, CertList* cert_list, CRLList* crl_list = nullptr)
    {
      std::stringstream in(content);
      from_istream(in, title, cert_list, crl_list);
    }

    static void from_file(const std::string& filename, CertList* cert_list, CRLList* crl_list = nullptr)
    {
      std::ifstream ifs(filename.c_str());
      if (!ifs)
	OPENVPN_THROW(open_file_error, "cannot open CERT/CRL file " << filename);
      from_istream(ifs, filename, cert_list, crl_list);
      if (ifs.bad())
	OPENVPN_THROW(open_file_error, "cannot read CERT/CRL file " << filename);
    }

    CertList certs;
    CRLList crls;
  };

} // namespace openvpn

#endif // OPENVPN_PKI_CCLIST_H
