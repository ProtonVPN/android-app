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

// Query/Set VPC routes, requires this policy:
//
// {
//     "Version": "2012-10-17",
//     "Statement": [
//         {
//             "Sid": "Stmt1478633458000",
//             "Effect": "Allow",
//             "Action": [
//                 "ec2:CreateRoute",
//                 "ec2:DescribeNetworkInterfaceAttribute",
//                 "ec2:DescribeNetworkInterfaces",
//                 "ec2:DescribeRouteTables",
//                 "ec2:ModifyNetworkInterfaceAttribute",
//                 "ec2:ReplaceRoute",
//                 "ec2:DeleteRoute"
//             ],
//             "Resource": [
//                 "*"
//             ]
//         }
//     ]
// }

#pragma once

#include <openvpn/common/xmlhelper.hpp>
#include <openvpn/aws/awshttp.hpp>
#include <openvpn/aws/awspc.hpp>
#include <openvpn/aws/awsrest.hpp>

namespace openvpn {
  namespace AWS {
    class Route
    {
    public:
      OPENVPN_EXCEPTION(aws_route_error);

      enum class RouteTargetType
        {
	  INTERFACE_ID,
	  INSTANCE_ID
	};

      class Context
      {
      public:
	Context(PCQuery::Info instance_info_arg,
		Creds creds_arg,
		RandomAPI::Ptr rng,
		Stop* async_stop_arg,
		const int debug_level)
	  : instance_info(std::move(instance_info_arg)),
	    http_context(std::move(rng), debug_level),
	    ts(http_context.transaction_set(ec2_host(instance_info))),
	    creds(std::move(creds_arg)),
	    async_stop(async_stop_arg)
	{
	}

	void reset()
	{
	  if (ts)
	    ts->hsc.reset();
	}

	std::string instance_id() const
	{
	  return instance_info.instanceId;
	}

      private:
	friend class Route;
	PCQuery::Info instance_info;
	HTTPContext http_context;
	WS::ClientSet::TransactionSet::Ptr ts;
	Creds creds;
	Stop* async_stop;
      };

      // Query network_interface_id and route_table_id
      // from EC2 API.
      class Info
      {
      public:
	Info(std::string network_interface_id_arg,
	     std::string route_table_id_arg)
	  : network_interface_id(std::move(network_interface_id_arg)),
	    route_table_id(std::move(route_table_id_arg))
	{
	}

	Info(Context& ctx)
	{
	  // AWS IDs local to this constructor
	  std::string vpc_id;
	  std::string subnet_id;

	  // first request -- get the AWS network interface
	  {
	    // create API query
	    {
	      REST::Query q;
	      q.emplace_back("Action", "DescribeNetworkInterfaces");
	      q.emplace_back("Filter.1.Name", "attachment.instance-id");
	      q.emplace_back("Filter.1.Value.1", ctx.instance_info.instanceId);
	      q.emplace_back("Filter.2.Name", "addresses.private-ip-address");
	      q.emplace_back("Filter.2.Value.1", ctx.instance_info.privateIp);
	      add_transaction(ctx, std::move(q));
	    }

	    // do transaction
	    execute_transaction(ctx);

	    // process reply
	    {
	      // get the transaction
	      WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	      // get reply
	      const std::string reply = t.content_in_string();

	      // check the reply status
	      if (!t.http_status_success())
		OPENVPN_THROW(aws_route_error, "DescribeNetworkInterfaces: " << t.format_status(*ctx.ts));

	      // parse XML reply
	      const Xml::Document doc(reply, "DescribeNetworkInterfaces");
	      const tinyxml2::XMLElement* item = Xml::find(&doc,
							   "DescribeNetworkInterfacesResponse",
							   "networkInterfaceSet",
							   "item");
	      if (!item)
		OPENVPN_THROW(aws_route_error, "DescribeNetworkInterfaces: cannot locate <item> tag in returned XML:\n" << reply);
	      network_interface_id = Xml::find_text(item, "networkInterfaceId");
	      vpc_id = Xml::find_text(item, "vpcId");
	      subnet_id = Xml::find_text(item, "subnetId");
	      if (network_interface_id.empty() || vpc_id.empty() || subnet_id.empty())
		OPENVPN_THROW(aws_route_error, "DescribeNetworkInterfaces: cannot locate one of networkInterfaceId, vpcId, or subnetId in returned XML:\n" << reply);
	    }
	  }

	  // second request -- get the VPC routing table
	  {
	    // create API query
	    {
	      REST::Query q;
	      q.emplace_back("Action", "DescribeRouteTables");
	      q.emplace_back("Filter.1.Name", "vpc-id");
	      q.emplace_back("Filter.1.Value.1", vpc_id);
	      q.emplace_back("Filter.2.Name", "association.subnet-id");
	      q.emplace_back("Filter.2.Value.1", subnet_id);
	      add_transaction(ctx, std::move(q));
	    }

	    // do transaction
	    execute_transaction(ctx);

	    // process reply
	    {
	      // get the transaction
	      WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	      // get reply
	      const std::string reply = t.content_in_string();

	      // check the reply status
	      if (!t.http_status_success())
		OPENVPN_THROW(aws_route_error, "DescribeRouteTables: " << t.format_status(*ctx.ts) << '\n' << reply);

	      // parse XML reply
	      const Xml::Document doc(reply, "DescribeRouteTables");
	      route_table_id = Xml::find_text(&doc,
					      "DescribeRouteTablesResponse",
					      "routeTableSet",
					      "item",
					      "routeTableId");
	      if (route_table_id.empty())
		OPENVPN_THROW(aws_route_error, "DescribeRouteTables: cannot locate routeTableId in returned XML:\n" << reply);
	    }
	  }
	}

	std::string to_string() const
	{
	  return '[' + network_interface_id + '/' + route_table_id + ']';
	}

	std::string network_interface_id;
	std::string route_table_id;
      };

      // Set sourceDestCheck flag on AWS network interface.
      static void set_source_dest_check(Context& ctx,
					const std::string& network_interface_id,
					const bool source_dest_check)
      {
	const std::string sdc = source_dest_check ? "true" : "false";

	// first get the attribute and see if it is already set
	// the way we want it
	{
	  REST::Query q;
	  q.emplace_back("Action", "DescribeNetworkInterfaceAttribute");
	  q.emplace_back("NetworkInterfaceId", network_interface_id);
	  q.emplace_back("Attribute", "sourceDestCheck");
	  add_transaction(ctx, std::move(q));
	}

	// do transaction
	execute_transaction(ctx);

	// process reply
	{
	  // get the transaction
	  WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	  // get reply
	  const std::string reply = t.content_in_string();

	  // check the reply status
	  if (!t.http_status_success())
	    OPENVPN_THROW(aws_route_error, "DescribeNetworkInterfaceAttribute: " << t.format_status(*ctx.ts) << '\n' << reply);

	  // parse XML reply
	  const Xml::Document doc(reply, "DescribeNetworkInterfaceAttribute");
	  const std::string retval = Xml::find_text(&doc,
						    "DescribeNetworkInterfaceAttributeResponse",
						    "sourceDestCheck",
						    "value");
	  // already set to desired value?
	  if (retval == sdc)
	    return;
	}

	// create API query
	{
	  REST::Query q;
	  q.emplace_back("Action", "ModifyNetworkInterfaceAttribute");
	  q.emplace_back("NetworkInterfaceId", network_interface_id);
	  q.emplace_back("SourceDestCheck.Value", sdc);
	  add_transaction(ctx, std::move(q));
	}

	// do transaction
	execute_transaction(ctx);

	// process reply
	{
	  // get the transaction
	  WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	  // get reply
	  const std::string reply = t.content_in_string();

	  // check the reply status
	  if (!t.http_status_success())
	    OPENVPN_THROW(aws_route_error, "ModifyNetworkInterfaceAttribute: " << t.format_status(*ctx.ts) << '\n' << reply);

	  // parse XML reply
	  const Xml::Document doc(reply, "ModifyNetworkInterfaceAttribute");
	  const std::string retval = Xml::find_text(&doc,
						    "ModifyNetworkInterfaceAttributeResponse",
						    "return");
	  if (retval != "true")
	    OPENVPN_THROW(aws_route_error, "ModifyNetworkInterfaceAttribute: returned failure status: " << '\n' << reply);

	  OPENVPN_LOG("AWS EC2 ModifyNetworkInterfaceAttribute " << network_interface_id << " SourceDestCheck.Value=" << sdc);
	}
      }

      static void delete_route(Context& ctx,
      			       const std::string& route_table_id,
			       const std::string& cidr,
			       bool ipv6)
      {
	{
	  REST::Query q;
	  q.emplace_back("Action", "DeleteRoute");
	  q.emplace_back(ipv6 ? "DestinationIpv6CidrBlock" : "DestinationCidrBlock", cidr);
	  q.emplace_back("RouteTableId", route_table_id);
	  add_transaction(ctx, std::move(q));
	}

	// do transaction
	execute_transaction(ctx);

	// process reply
	{
	  // get the transaction
	  WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	  // get reply
	  const std::string reply = t.content_in_string();

	  // check the reply status
	  if (!t.http_status_success())
	    OPENVPN_THROW(aws_route_error, "DeleteRoute: " << t.format_status(*ctx.ts) << '\n' << reply);

	  // parse XML reply
	  const Xml::Document doc(reply, "DeleteRoute");
	  const std::string retval = Xml::find_text(&doc,
						    "DeleteRouteResponse",
						    "return");
	  if (retval != "true")
	    OPENVPN_THROW(aws_route_error, "DeleteRoute: returned failure status: " << '\n' << reply);

	  OPENVPN_LOG("AWS EC2 DeleteRoute " << cidr << " -> table " << route_table_id);
	}
      }

      // Create/replace a VPC route
      static void replace_create_route(Context& ctx,
				       const std::string& route_table_id,
				       const std::string& route,
				       RouteTargetType target_type,
				       const std::string& target_value,
				       bool ipv6)
      {
	std::string target_type_str;

	switch (target_type)
	{
	case RouteTargetType::INSTANCE_ID:
	  target_type_str = "InstanceId";
	  break;

	case RouteTargetType::INTERFACE_ID:
	  target_type_str = "NetworkInterfaceId";
	  break;

	default:
	  OPENVPN_THROW(aws_route_error,
	  		"replace_create_route: unknown RouteTargetType " << (int)target_type);
	}

	const std::string dest_cidr_block_name = ipv6 ?
		"DestinationCidrIpv6Block" : "DestinationCidrBlock";

	// create API query
	{
	  REST::Query q;
	  q.emplace_back("Action", "ReplaceRoute");
	  q.emplace_back(dest_cidr_block_name, route);
	  q.emplace_back(target_type_str, target_value);
	  q.emplace_back("RouteTableId", route_table_id);
	  add_transaction(ctx, std::move(q));
	}

	// do transaction
	execute_transaction(ctx);

	// process reply
	{
	  // get the transaction
	  WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	  // get reply
	  const std::string reply = t.content_in_string();

	  // Check the reply status.  We only throw on communication failure,
	  // since ReplaceRoute will legitimately fail if the route doesn't
	  // exist yet.
	  if (!t.comm_status_success())
	    OPENVPN_THROW(aws_route_error, "ReplaceRoute: " << t.format_status(*ctx.ts) << '\n' << reply);

	  // ReplaceRoute succeeded?
	  if (t.request_status_success())
	    {
	      // parse XML reply
	      const Xml::Document doc(reply, "ReplaceRoute");
	      const std::string retval = Xml::find_text(&doc,
							"ReplaceRouteResponse",
							"return");
	      if (retval == "true")
		{
		  OPENVPN_LOG("AWS EC2 ReplaceRoute " << route << " -> table " << route_table_id);
		  return;
		}
	    }
	}

	// Now try CreateRoute
	{
	  REST::Query q;
	  q.emplace_back("Action", "CreateRoute");
	  q.emplace_back(dest_cidr_block_name, route);
	  q.emplace_back(target_type_str, target_value);
	  q.emplace_back("RouteTableId", route_table_id);
	  add_transaction(ctx, std::move(q));
	}

	// do transaction
	execute_transaction(ctx);

	// process reply
	{
	  // get the transaction
	  WS::ClientSet::Transaction& t = ctx.ts->first_transaction();

	  // get reply
	  const std::string reply = t.content_in_string();

	  // check the reply status
	  if (!t.http_status_success())
	    OPENVPN_THROW(aws_route_error, "CreateRoute: " << t.format_status(*ctx.ts) << '\n' << reply);

	  // parse XML reply
	  const Xml::Document doc(reply, "CreateRoute");
	  const std::string retval = Xml::find_text(&doc,
						    "CreateRouteResponse",
						    "return");
	  if (retval != "true")
	    OPENVPN_THROW(aws_route_error, "CreateRoute: returned failure status: " << '\n' << reply);

	  OPENVPN_LOG("AWS EC2 CreateRoute " << route << " -> table " << route_table_id);
	}
      }

    private:
      static void execute_transaction(Context& ctx)
      {
	WS::ClientSet::new_request_synchronous(ctx.ts, ctx.async_stop, ctx.http_context.rng(), true);
      }

      static void add_transaction(const Context& ctx, REST::Query&& q)
      {
	std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
	t->req.uri = ec2_uri(ctx, std::move(q));
	t->req.method = "GET";
	t->ci.keepalive = true;
	ctx.ts->transactions.clear();
	ctx.ts->transactions.push_back(std::move(t));
      }

      static std::string ec2_uri(const Context& ctx, REST::Query&& q)
      {
	REST::QueryBuilder qb;
	qb.date = REST::amz_date();
	qb.expires = 300;
	qb.region = ctx.instance_info.region;
	qb.service = "ec2";
	qb.method = "GET";
	qb.host = ec2_host(ctx.instance_info);
	qb.uri = "/";
	qb.parms = std::move(q);
	qb.parms.emplace_back("Version", "2015-10-01");
	qb.add_amz_parms(ctx.creds);
	qb.sort_parms();
	qb.add_amz_signature(ctx.http_context.digest_factory(), ctx.creds);
	return qb.uri_query();
      }

      static std::string ec2_host(const PCQuery::Info& instance_info)
      {
	return "ec2." + instance_info.region + ".amazonaws.com";
      }
    };
  }
}
