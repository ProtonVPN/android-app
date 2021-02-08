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

#pragma once

#include <string>
#include <sstream>
#include <vector>
#include <deque>
#include <memory>
#include <utility>
#include <algorithm>

#include <openvpn/common/size.hpp>
#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/time/timestr.hpp>
#include <openvpn/time/asiotimersafe.hpp>
#include <openvpn/asio/asiowork.hpp>

// include acceptors for different protocols
#include <openvpn/acceptor/base.hpp>
#include <openvpn/acceptor/tcp.hpp>
#ifdef ASIO_HAS_LOCAL_SOCKETS
#include <openvpn/acceptor/unix.hpp>
#endif

#if defined(OPENVPN_PLATFORM_WIN)
#include <openvpn/win/logutil.hpp>
#else
#include <openvpn/common/redir.hpp>
#endif

namespace openvpn {
  class OMICore : public Acceptor::ListenerBase
  {
  public:
    OPENVPN_EXCEPTION(omi_error);

    struct LogFn
    {
      LogFn(const OptionList& opt)
      {
	fn = opt.get_optional("log", 1, 256);
	if (fn.empty())
	  {
	    fn = opt.get_optional("log-append", 1, 256);
	    append = true;
	  }
	errors_to_stderr = opt.exists("errors-to-stderr");
      }

      std::string fn;
      bool append = false;
      bool errors_to_stderr = false;
    };

    void stop()
    {
      if (stop_called)
	return;
      stop_called = true;

      asio_work.reset();

      // close acceptor
      if (acceptor)
	acceptor->close();

      // Call derived class stop method and close OMI socket,
      // but if omi_stop() returns true, wait for content_out
      // to be flushed to OMI socket before closing it.
      if (!omi_stop() || content_out.empty())
	stop_omi_client(false, 250);
    }

  protected:
    struct Command
    {
      Option option;
      std::vector<std::string> extra;
      bool valid_utf8 = false;

      std::string to_string() const
      {
	std::ostringstream os;
	os << option.render(Option::RENDER_BRACKET);
	if (!valid_utf8)
	  os << " >>>!UTF8";
	os << '\n';
	for (auto &line : extra)
	  os << line << '\n';
	return os.str();
      }
    };

    class History
    {
    public:
      History(const std::string& type_arg,
	      const size_t max_size_arg)
	: type(type_arg),
	  max_size(max_size_arg)
      {
      }

      bool is_cmd(const Option& o) const
      {
	return o.get_optional(0, 0) == type;
      }

      std::string process_cmd(const Option& o)
      {
	try {
	  const std::string arg1 = o.get(1, 16);
	  if (arg1 == "on")
	    {
	      const std::string arg2 = o.get_optional(2, 16);
	      real_time = true;
	      std::string ret = real_time_status();
	      if (arg2 == "all")
		ret += show(hist.size());
	      else if (!arg2.empty())
		return error();
	      return ret;
	    }
	  else if (arg1 == "all")
	    {
	      return show(hist.size());
	    }
	  else if (arg1 == "off")
	    {
	      real_time = false;
	      return real_time_status();
	    }
	  else
	    {
	      unsigned int n;
	      if (parse_number(arg1, n))
		return show(n);
	      else
		return error();
	    }
	}
	catch (const option_error&)
	  {
	    return error();
	  }
	catch (const std::exception& e)
	  {
	    return "ERROR: " + type + " processing error: " + e.what() + "\r\n";
	  }
      }

      std::string notify(const std::string& msg)
      {
	hist.push_front(msg);
	while (hist.size() > max_size)
	  hist.pop_back();
	if (real_time)
	  return notify_prefix() + msg;
	else
	  return std::string();
      }

    private:
      std::string show(size_t n) const
      {
	std::string ret = "";
	n = std::min(n, hist.size());
	for (size_t i = 0; i < n; ++i)
	  ret += hist[n - i - 1];
	ret += "END\r\n";
	return ret;
      }

      std::string notify_prefix() const
      {
	return ">" + string::to_upper_copy(type) + ":";
      }

      std::string real_time_status() const
      {
	std::string ret = "SUCCESS: real-time " + type + " notification set to ";
	if (real_time)
	  ret += "ON";
	else
	  ret += "OFF";
	ret += "\r\n";
	return ret;
      }

      std::string error() const
      {
	return "ERROR: " + type + " parameter must be 'on' or 'off' or some number n or 'all'\r\n";
      }

      std::string type;
      size_t max_size;
      bool real_time = false;
      std::deque<std::string> hist;
    };

    OMICore(openvpn_io::io_context& io_context_arg)
      : io_context(io_context_arg),
	stop_timer(io_context_arg)
    {
    }

    void log_setup(const LogFn& log)
    {
      if (!log.fn.empty())
	{
#if defined(OPENVPN_PLATFORM_WIN)
	  log_handle = Win::LogUtil::create_file(log.fn, "", log.append);
#else
	  RedirectStd redir("",
			    log.fn,
			    log.append ? RedirectStd::FLAGS_APPEND : RedirectStd::FLAGS_OVERWRITE,
			    RedirectStd::MODE_ALL,
			    false);
	  redir.redirect();
#endif
	}
      errors_to_stderr = log.errors_to_stderr;
    }

    static std::string get_config(const OptionList& opt)
    {
      // get config file
      const std::string config_fn = opt.get("config", 1, 256);
      return read_config(config_fn);
    }

    void start(const OptionList& opt)
    {
      const Option& o = opt.get("management");
      const std::string addr = o.get(1, 256);
      const std::string port = o.get(2, 16);
      const std::string password_file = o.get_optional(3, 256);

      if (password_file == "stdin")
	{
	  password_defined = true;
	  std::cout << "Enter Management Password:";
	  std::cin >> password;
	}

      hold_flag = opt.exists("management-hold");

      // management-queue-limit low_water high_water
      {
	const Option* o = opt.get_ptr("management-queue-limit");
	if (o)
	  {
	    const size_t low_water = o->get_num<size_t>(1, 0, 0, 1000000);
	    const size_t high_water = o->get_num<size_t>(2, 0, 0, 1000000);
	    content_out_throttle.reset(new BufferThrottle(low_water, high_water));
	  }
      }

      // management-client-user root
      {
	const Option* o = opt.get_ptr("management-client-user");
	if (o)
	  {
	    if (o->get(1, 64) == "root")
	      management_client_root = true;
	    else
	      throw Exception("only --management-client-user root supported");
	  }
      }

      if (opt.exists("management-client"))
	{
	  if (port == "unix")
	    {
	      OPENVPN_LOG("OMI Connecting to " << addr << " [unix]");
	      connect_unix(addr);
	    }
	  else
	    {
	      OPENVPN_LOG("OMI Connecting to [" << addr << "]:" << port << " [tcp]");
	      connect_tcp(addr, port);
	    }
	}
      else
	{
	  if (port == "unix")
	    {
	      OPENVPN_LOG("OMI Listening on " << addr << " [unix]");
	      listen_unix(addr);
	    }
	  else
	    {
	      OPENVPN_LOG("OMI Listening on [" << addr << "]:" << port << " [tcp]");
	      listen_tcp(addr, port);
	    }
	}

      // don't exit Asio event loop until AsioWork object is deleted
      asio_work.reset(new AsioWork(io_context));
    }

    void start_connection_if_not_hold()
    {
      if (!hold_flag)
	omi_start_connection();
    }

    void send(BufferPtr buf)
    {
      if (!is_sock_open())
	return;
      content_out.push_back(std::move(buf));
      if (content_out_throttle)
	content_out_throttle->size_change(content_out.size());
      if (content_out.size() == 1) // send operation not currently active?
	queue_send();
    }

    void send(const std::string& str)
    {
      if (!str.empty())
	send(buf_from_string(str));
    }

    bool send_ready() const
    {
      if (content_out_throttle)
	return content_out_throttle->ready();
      else
	return true;
    }

    void async_done()
    {
      process_recv();
    }

    void log_full(const std::string& text) // logs to OMI buffer and log file
    {
      const time_t now = ::time(NULL);
      const std::string textcrlf = string::unix2dos(text, true);
      log_line(openvpn::to_string(now) + ",," + textcrlf);
#if defined(OPENVPN_PLATFORM_WIN)
      if (log_handle.defined())
	Win::LogUtil::log(log_handle(), date_time(now) + ' ' + textcrlf);
      else
#endif
      std::cout << date_time(now) << ' ' << text << std::flush;
    }

    void log_timestamp(const time_t timestamp, const std::string& text) // logs to OMI buffer only
    {
      const std::string textcrlf = string::unix2dos(text, true);
      log_line(openvpn::to_string(timestamp) + ",," + textcrlf);
    }

    void log_line(const std::string& line) // logs to OMI buffer only
    {
      if (!stop_called)
	send(hist_log.notify(line));
    }

    void state_line(const std::string& line)
    {
      if (!stop_called)
	send(hist_state.notify(line));
    }

    void echo_line(const std::string& line)
    {
      if (!stop_called)
	send(hist_echo.notify(line));
    }

    bool is_errors_to_stderr() const
    {
      return errors_to_stderr;
    }

    bool is_stopping() const
    {
      return stop_called;
    }

    unsigned int get_bytecount() const
    {
      return bytecount;
    }

    virtual bool omi_command_is_multiline(const std::string& arg0, const Option& option) = 0;
    virtual bool omi_command_in(const std::string& arg0, const Command& cmd) = 0;
    virtual void omi_start_connection() = 0;
    virtual void omi_done(const bool eof) = 0;
    virtual void omi_sigterm() = 0;
    virtual bool omi_stop() = 0;

    virtual bool omi_is_sighup_implemented()
    {
      return false;
    }

    virtual void omi_sighup()
    {
    }

    openvpn_io::io_context& io_context;

  private:
    typedef RCPtr<OMICore> Ptr;

    class BufferThrottle
    {
    public:
      BufferThrottle(const size_t low_water_arg,
		     const size_t high_water_arg)
	: low_water(low_water_arg),
	  high_water(high_water_arg)
      {
	if (low_water > high_water)
	  throw Exception("bad management-queue-limit values");
      }

      void size_change(const size_t size)
      {
	if (ready_)
	  {
	    if (size > high_water)
	      ready_ = false;
	  }
	else
	  {
	    if (size <= low_water)
	      ready_ = true;
	  }
      }

      bool ready() const
      {
	return ready_;
      }

    private:
      const size_t low_water;
      const size_t high_water;
      volatile bool ready_ = true;
    };

    bool command_in(std::unique_ptr<Command> cmd)
    {
      try {
	const std::string arg0 = cmd->option.get_optional(0, 64);
	if (arg0.empty())
	  return false;
	if (!cmd->valid_utf8)
	  throw Exception("invalid UTF8");
	switch (arg0[0])
	  {
	  case 'b':
	    {
	      if (arg0 == "bytecount")
		{
		  process_bytecount_cmd(cmd->option);
		  return false;
		}
	      break;
	    }
	  case 'e':
	    {
	      if (hist_echo.is_cmd(cmd->option))
		{
		  send(hist_echo.process_cmd(cmd->option));
		  return false;
		}
	      if (arg0 == "exit")
		{
		  conditional_stop(true);
		  return false;
		}
	      break;
	    }
	  case 'h':
	    {
	      if (is_hold_cmd(cmd->option))
		{
		  bool release = false;
		  send(hold_cmd(cmd->option, release));
		  if (release)
		    hold_release();
		  return false;
		}
	      break;
	    }
	  case 'l':
	    {
	      if (hist_log.is_cmd(cmd->option))
		{
		  send(hist_log.process_cmd(cmd->option));
		  return false;
		}
	      break;
	    }
	  case 'q':
	    {
	      if (arg0 == "quit")
		{
		  conditional_stop(true);
		  return false;
		}
	      break;
	    }
	  case 's':
	    {
	      if (hist_state.is_cmd(cmd->option))
		{
		  send(hist_state.process_cmd(cmd->option));
		  return false;
		}
	      if (arg0 == "signal")
		{
		  process_signal_cmd(cmd->option);
		  return false;
		}
	      break;
	    }
	  }
	return omi_command_in(arg0, *cmd);
      }
      catch (const std::exception& e)
	{
	  std::string err_ref = "option";
	  if (cmd)
	    err_ref = cmd->option.err_ref();
	  send("ERROR: error processing " + err_ref + " : " + e.what() + "\r\n");
	}
      return false;
    }

    bool is_hold_cmd(const Option& o) const
    {
      return o.get_optional(0, 0) == "hold";
    }

    std::string hold_cmd(const Option& o, bool& release)
    {
      try {
	const std::string arg1 = o.get_optional(1, 16);
	if (arg1.empty())
	  {
	    if (hold_flag)
	      return "SUCCESS: hold=1\r\n";
	    else
	      return "SUCCESS: hold=0\r\n";
	  }
	else if (arg1 == "on")
	  {
	    hold_flag = true;
	    return "SUCCESS: hold flag set to ON\r\n";
	  }
	else if (arg1 == "off")
	  {
	    hold_flag = false;
	    return "SUCCESS: hold flag set to OFF\r\n";
	  }
	else if (arg1 == "release")
	  {
	    release = true;
	    return "SUCCESS: hold release succeeded\r\n";
	  }
      }
      catch (const option_error&)
	{
	}
      return "ERROR: bad hold command parameter\r\n";
    }

    void hold_cycle()
    {
      hold_wait = true;
      if (hold_flag)
	send(">HOLD:Waiting for hold release\r\n");
      else
	hold_release();
    }

    void hold_release()
    {
      if (hold_wait)
	{
	  hold_wait = false;
	  omi_start_connection();
	}
    }

    void process_bytecount_cmd(const Option& o)
    {
      bytecount = o.get_num<decltype(bytecount)>(1, 0, 0, 86400);
      send("SUCCESS: bytecount interval changed\r\n");
    }

    void process_signal_cmd(const Option& o)
    {
      const std::string type = o.get(1, 16);
      if (type == "SIGTERM")
	{
	  send("SUCCESS: signal SIGTERM thrown\r\n");
	  omi_sigterm();
	}
      else if (type == "SIGHUP" && omi_is_sighup_implemented())
	{
	  send("SUCCESS: signal SIGHUP thrown\r\n");
	  omi_sighup();
	}
      else
	send("ERROR: signal not supported\r\n");
    }

    bool command_is_multiline(const Option& o)
    {
      const std::string arg0 = o.get_optional(0, 64);
      if (arg0.empty())
	return false;
      return omi_command_is_multiline(arg0, o);
    }

    bool is_sock_open() const
    {
      return socket && socket->is_open();
    }

    void conditional_stop(const bool eof)
    {
      if (acceptor || stop_called)
	stop_omi_client(eof, 250);
      else
	stop(); // if running in management-client mode, do a full stop
    }

    void stop_omi_client(const bool eof, const unsigned int milliseconds)
    {
      stop_timer.expires_after(Time::Duration::milliseconds(milliseconds));
      stop_timer.async_wait([self=Ptr(this), eof](const openvpn_io::error_code& error)
				 {
				   if (!error)
				     self->stop_omi_client(eof);
				 });
    }

    void stop_omi_client(const bool eof)
    {
      stop_timer.cancel();
      const bool is_open = is_sock_open();
      if (is_open)
	socket->close();
      content_out.clear();
      if (content_out_throttle)
	content_out_throttle->size_change(content_out.size());
      in_partial.clear();
      if (is_open)
	omi_done(eof);
    }

    void send_title_message()
    {
      send(">INFO:OpenVPN Management Interface Version 1 -- type 'help' for more info\r\n");
    }

    void send_password_prompt()
    {
      send("ENTER PASSWORD:");
    }

    void send_password_correct()
    {
      send("SUCCESS: password is correct");
    }

    bool process_password()
    {
      if (password_defined && !password_verified)
	{
	  if (password == in_partial)
	    {
	      password_verified = true;
	      send_password_correct();
	      send_title_message();
	      hold_cycle();
	    }
	  else
	    {
	      // wrong password, kick the client
	      stop_omi_client(false, 250);
	    }
	  return true;
	}

      return false;
    }

    bool process_in_line() // process incoming line in in_partial
    {
      bool ret = false;
      const bool utf8 = Unicode::is_valid_utf8(in_partial);
      string::trim_crlf(in_partial);

      if (process_password())
	return false;

      if (multiline)
	{
	  if (!command)
	    throw omi_error("process_in_line: internal error");
	  if (in_partial == "END")
	    {
	      ret = command_in(std::move(command));
	      command.reset();
	      multiline = false;
	    }
	  else
	    {
	      if (!utf8)
		command->valid_utf8 = false;
	      command->extra.push_back(std::move(in_partial));
	    }
	}
      else
	{
	  command.reset(new Command);
	  command->option = OptionList::parse_option_from_line(in_partial, nullptr);
	  command->valid_utf8 = utf8;
	  multiline = command_is_multiline(command->option);
	  if (!multiline)
	    {
	      ret = command_in(std::move(command));
	      command.reset();
	    }
	}
      return ret;
    }

    static std::string read_config(const std::string& fn)
    {
      if (fn == "stdin")
	return read_stdin();
      else
	return read_text_utf8(fn);
    }

    void listen_tcp(const std::string& addr, const std::string& port)
    {
      // init TCP acceptor
      Acceptor::TCP::Ptr a(new Acceptor::TCP(io_context));

      // parse address/port of local endpoint
      const IP::Addr ip_addr = IP::Addr::from_string(addr);
      a->local_endpoint.address(ip_addr.to_asio());
      a->local_endpoint.port(HostPort::parse_port(port, "OMI TCP listen"));

      // open socket
      a->acceptor.open(a->local_endpoint.protocol());

      // set options
      a->set_socket_options(0);

      // bind to local address
      a->acceptor.bind(a->local_endpoint);

      // listen for incoming client connections
      a->acceptor.listen();

      // save acceptor
      acceptor = a;

      // dispatch accepts to handle_except()
      queue_accept();
    }

    void listen_unix(const std::string& socket_path)
    {
#ifdef ASIO_HAS_LOCAL_SOCKETS
      // init unix socket acceptor
      Acceptor::Unix::Ptr a(new Acceptor::Unix(io_context));

      // set endpoint
      a->pre_listen(socket_path);
      a->local_endpoint.path(socket_path);

      // open socket
      a->acceptor.open(a->local_endpoint.protocol());

      // bind to local address
      a->acceptor.bind(a->local_endpoint);

      // set socket permissions in filesystem
      a->set_socket_permissions(socket_path, 0777);

      // listen for incoming client connections
      a->acceptor.listen();

      // save acceptor
      acceptor = a;

      // dispatch accepts to handle_except()
      queue_accept();
#else
      throw Exception("unix sockets not supported on this platform");
#endif
    }

    void queue_accept()
    {
      if (acceptor)
	acceptor->async_accept(this, 0, io_context);
    }

    void verify_sock_peer(AsioPolySock::Base& sock)
    {
#ifdef ASIO_HAS_LOCAL_SOCKETS
      SockOpt::Creds cr;
      if (management_client_root && sock.peercreds(cr))
	{
	  if (!cr.root_uid())
	    throw Exception("peer must be root");
	}
#endif
    }

    // despite its name, this method handles both accept and connect events
    virtual void handle_accept(AsioPolySock::Base::Ptr sock, const openvpn_io::error_code& error) override
    {
      if (stop_called)
	return;

      try {
	if (error)
	  throw Exception("accept/connect failed: " + error.message());
	if (is_sock_open())
	  throw Exception("client already connected");

	verify_sock_peer(*sock);

	sock->non_blocking(true);
	sock->set_cloexec();
	socket = std::move(sock);

	password_verified = false;

	if (password_defined)
	  send_password_prompt();
	else
	  send_title_message();

	queue_recv();

	if (!password_defined)
	  hold_cycle();
      }
      catch (const std::exception& e)
	{
	  const std::string msg = "exception in accept/connect handler: " + std::string(e.what()) + '\n';
	  if (errors_to_stderr)
	    std::cerr << msg << std::flush;
	  OPENVPN_LOG_STRING(msg);
	}
      queue_accept();
    }

    void connect_tcp(const std::string& addr, const std::string& port)
    {
      openvpn_io::ip::tcp::endpoint ep(IP::Addr::from_string(addr).to_asio(),
				 HostPort::parse_port(port, "OMI TCP connect"));
      AsioPolySock::TCP* s = new AsioPolySock::TCP(io_context, 0);
      AsioPolySock::Base::Ptr sock(s);
      s->socket.async_connect(ep,
			      [self=Ptr(this), sock](const openvpn_io::error_code& error) mutable
			      {
				// this is a connect, but we reuse the accept method
				self->handle_accept(std::move(sock), error);
			      });
    }

    void connect_unix(const std::string& socket_path)
    {
#ifdef ASIO_HAS_LOCAL_SOCKETS
      openvpn_io::local::stream_protocol::endpoint ep(socket_path);
      AsioPolySock::Unix* s = new AsioPolySock::Unix(io_context, 0);
      AsioPolySock::Base::Ptr sock(s);
      s->socket.async_connect(ep,
			      [self=Ptr(this), sock](const openvpn_io::error_code& error) mutable
			      {
				// this is a connect, but we reuse the accept method
				self->handle_accept(std::move(sock), error);
			      });
#else
      throw Exception("unix sockets not supported on this platform");
#endif
    }

    void queue_recv()
    {
      if (!is_sock_open() || recv_queued)
	return;
      BufferPtr buf(new BufferAllocated(256, 0));
      socket->async_receive(buf->mutable_buffer_clamp(),
			    [self=Ptr(this), sock=socket, buf](const openvpn_io::error_code& error, const size_t bytes_recvd)
			    {
			      self->handle_recv(error, bytes_recvd, std::move(buf), sock.get());
			    });
      recv_queued = true;
    }

    void handle_recv(const openvpn_io::error_code& error, const size_t bytes_recvd,
		     BufferPtr buf, const AsioPolySock::Base* queued_socket)
    {
      recv_queued = false;
      if (!is_sock_open() || socket.get() != queued_socket)
	return;
      if (error)
	{
	  const bool eof = (error == openvpn_io::error::eof);
	  if (!eof)
	    OPENVPN_LOG("client socket recv error: " << error.message());
	  conditional_stop(eof);
	  return;
	}
      buf->set_size(bytes_recvd);
      in_buf = std::move(buf);
      process_recv();
    }

    void process_recv()
    {
      while (in_buf->size())
	{
	  const char c = (char)in_buf->pop_front();
	  in_partial += c;
	  if (c == '\n')
	    {
	      bool defer = false;
	      try {
		defer = process_in_line();
	      }
	      catch (const std::exception& e)
		{
		  send("ERROR: in OMI command: " + std::string(e.what()) + "\r\n");
		}
	      in_partial.clear();
	      if (defer)
		return;
	    }
	}

      queue_recv();
    }

    void queue_send()
    {
      if (!is_sock_open())
	return;
      BufferAllocated& buf = *content_out.front();
      socket->async_send(buf.const_buffer_clamp(),
			 [self=Ptr(this), sock=socket](const openvpn_io::error_code& error, const size_t bytes_sent)
			 {
			   self->handle_send(error, bytes_sent, sock.get());
			 });
    }

    void handle_send(const openvpn_io::error_code& error, const size_t bytes_sent,
		     const AsioPolySock::Base* queued_socket)
    {
      if (!is_sock_open() || socket.get() != queued_socket)
	return;

      if (error)
	{
	  OPENVPN_LOG("client socket send error: " << error.message());
	  conditional_stop(false);
	  return;
	}

      BufferPtr buf = content_out.front();
      if (bytes_sent == buf->size())
	{
	  content_out.pop_front();
	  if (content_out_throttle)
	    content_out_throttle->size_change(content_out.size());
	}
      else if (bytes_sent < buf->size())
	buf->advance(bytes_sent);
      else
	{
	  OPENVPN_LOG("client socket unexpected send size: " << bytes_sent << '/' << buf->size());
	  conditional_stop(false);
	  return;
	}

      if (!content_out.empty())
	queue_send();
      else if (stop_called)
	conditional_stop(false);
    }

    // I/O
    Acceptor::Base::Ptr acceptor;
    AsioPolySock::Base::Ptr socket;
    std::unique_ptr<AsioWork> asio_work;
    std::deque<BufferPtr> content_out;
    std::string in_partial;
    std::unique_ptr<Command> command;
    BufferPtr in_buf;
    bool management_client_root = false;
    bool multiline = false;
    bool errors_to_stderr = false;
    bool recv_queued = false;
    bool password_defined = false;
    bool password_verified = false;
    std::string password;

    // stopping
    volatile bool stop_called = false;
    AsioTimerSafe stop_timer;

    // hold
    bool hold_wait = false;
    bool hold_flag = false;

    // bandwidth stats
    unsigned int bytecount = 0;

    // histories
    History hist_log   {"log",   100};
    History hist_state {"state", 100};
    History hist_echo  {"echo",  100};

    // throttling
    std::unique_ptr<BufferThrottle> content_out_throttle;

#if defined(OPENVPN_PLATFORM_WIN)
    Win::ScopedHANDLE log_handle;
#endif
  };
}
