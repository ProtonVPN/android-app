//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//    Copyright (C) 2020-2020 Lev Stipakov <lev@openvpn.net>
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

#include <openvpn/buffer/buffer.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/dco/key.hpp>

#include <linux/ovpn_dco.h>
#include <netlink/genl/ctrl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/genl.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>

#include <memory>

namespace openvpn {

#define nla_nest_start(_msg, _type) nla_nest_start(_msg, (_type) | NLA_F_NESTED)

typedef int (*ovpn_nl_cb)(struct nl_msg *msg, void *arg);

/**
 * Implements asynchronous communication with ovpn-dco kernel module
 * via generic netlink protocol.
 *
 * Before using this class, caller should create ovpn-dco network device.
 *
 * @tparam ReadHandler class which implements
 * <tt>tun_read_handler(BufferAllocated &buf)</tt> method. \n
 *
 * buf has following layout:
 *  \li first byte - command type ( \p OVPN_CMD_PACKET, \p OVPN_CMD_DEL_PEER or
 * -1 for error)
 * \li following bytes - command-specific payload
 */
template <typename ReadHandler> class GeNL : public RC<thread_unsafe_refcount> {
  OPENVPN_EXCEPTION(netlink_error);

  typedef std::unique_ptr<nl_msg, decltype(&nlmsg_free)> NlMsgPtr;
  typedef std::unique_ptr<nl_sock, decltype(&nl_socket_free)> NlSockPtr;
  typedef std::unique_ptr<nl_cb, decltype(&nl_cb_put)> NlCbPtr;

public:
  typedef RCPtr<GeNL> Ptr;

  /**
   * Construct a new GeNL object
   *
   * @param io_context reference to io_context
   * @param ifindex_arg index of ovpn-dco network device
   * @param read_handler_arg instance of \p ReadHandler
   * @throws netlink_error thrown if error occurs during initialization
   */
  explicit GeNL(openvpn_io::io_context &io_context, unsigned int ifindex_arg,
                ReadHandler read_handler_arg)
      : sock_ptr(nl_socket_alloc(), nl_socket_free),
        cb_ptr(nl_cb_alloc(NL_CB_DEFAULT), nl_cb_put), sock(sock_ptr.get()),
        cb(cb_ptr.get()), ifindex(ifindex_arg), read_handler(read_handler_arg),
        halt(false) {
    nl_socket_set_buffer_size(sock, 8192, 8192);

    int ret = genl_connect(sock);
    if (ret != 0)
      OPENVPN_THROW(netlink_error,
                    " cannot connect to generic netlink: " << nl_geterror(ret));

    int mcast_id = get_mcast_id();
    if (mcast_id < 0)
      OPENVPN_THROW(netlink_error,
                    " cannot get multicast group: " << nl_geterror(mcast_id));

    ret = nl_socket_add_membership(sock, mcast_id);
    if (ret)
      OPENVPN_THROW(netlink_error, "failed to join mcast group: " << ret);

    ovpn_dco_id = genl_ctrl_resolve(sock, OVPN_NL_NAME);
    if (ovpn_dco_id < 0)
      OPENVPN_THROW(netlink_error,
                    " cannot find ovpn_dco netlink component: " << ovpn_dco_id);

    // set callback to handle control channel messages
    nl_cb_set(cb, NL_CB_VALID, NL_CB_CUSTOM, message_received, this);

    nl_cb_set(
        cb, NL_CB_SEQ_CHECK, NL_CB_CUSTOM,
        [](struct nl_msg *, void *) { return (int)NL_OK; }, NULL);
    nl_socket_set_cb(sock, cb);

    register_packet();

    // wrap netlink socket into ASIO primitive for async read
    stream.reset(new openvpn_io::posix::stream_descriptor(
        io_context, nl_socket_get_fd(sock)));

    nl_socket_set_nonblocking(sock);

    queue_genl_read();
  }

  /**
   * Start VPN connection
   *
   * At the moment, only client mode and ipv4/udp transport is supported.
   *
   * Note: this and many other methods have \p nla_put_failure label, which
   * must be defined when using NLA_PUT_XXX macros.
   *
   * @param socket file descriptor of transport socket, created by client
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  void start_vpn(int socket) {
    auto msg_ptr = create_msg(OVPN_CMD_START_VPN);
    auto* msg = msg_ptr.get();

    NLA_PUT_U32(msg, OVPN_ATTR_SOCKET, socket);
    NLA_PUT_U8(msg, OVPN_ATTR_PROTO, OVPN_PROTO_UDP4);
    NLA_PUT_U8(msg, OVPN_ATTR_MODE, OVPN_MODE_CLIENT);

    send_netlink_message(msg);
    return;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " start_vpn() nla_put_failure");
  }

  /**
   * Add peer information to kernel module
   *
   * @tparam T ASIO's transport socket endpoint type
   * @param local_endpoint local endpoint
   * @param remote_endpoint remote endpoiont
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  template <typename T> void new_peer(T local_endpoint, T remote_endpoint) {
    auto msg_ptr = create_msg(OVPN_CMD_NEW_PEER);
    auto* msg = msg_ptr.get();

    struct in_addr laddr;
    std::memcpy(&laddr.s_addr,
                local_endpoint.address().to_v4().to_bytes().data(), 4);
    struct in_addr raddr;
    std::memcpy(&raddr.s_addr,
                remote_endpoint.address().to_v4().to_bytes().data(), 4);

    struct nlattr *addr = nla_nest_start(msg, OVPN_ATTR_SOCKADDR_REMOTE);
    NLA_PUT(msg, OVPN_SOCKADDR_ATTR_ADDRESS, 4, &raddr);
    NLA_PUT_U16(msg, OVPN_SOCKADDR_ATTR_PORT, remote_endpoint.port());
    nla_nest_end(msg, addr);

    addr = nla_nest_start(msg, OVPN_ATTR_SOCKADDR_LOCAL);
    NLA_PUT(msg, OVPN_SOCKADDR_ATTR_ADDRESS, 4, &laddr);
    NLA_PUT_U16(msg, OVPN_SOCKADDR_ATTR_PORT, local_endpoint.port());
    nla_nest_end(msg, addr);

    send_netlink_message(msg);
    return;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " new_peer() nla_put_failure");
  }

  /**
   * Send data to kernel module, which is then sent to remote.
   * Used for sending control channel packets.
   *
   * @param data binary blob
   * @param len length of binary blob
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  void send_data(const void *data, size_t len) {
    auto msg_ptr = create_msg(OVPN_CMD_PACKET);
    auto* msg = msg_ptr.get();

    NLA_PUT(msg, OVPN_ATTR_PACKET, len, data);

    send_netlink_message(msg);
    return;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " send_data() nla_put_failure");
  }

  /**
   * Inject new key into kernel module
   *
   * @param key_slot OVPN_KEY_SLOT_PRIMARY or OVPN_KEY_SLOT_SECONDARY
   * @param kc pointer to KeyConfig struct which contains key data
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  void new_key(unsigned int key_slot, const KoRekey::KeyConfig *kc) {
    auto msg_ptr = create_msg(OVPN_CMD_NEW_KEY);
    auto* msg = msg_ptr.get();

    const int NONCE_LEN = 12;

    struct nlattr *key_dir;

    NLA_PUT_U32(msg, OVPN_ATTR_REMOTE_PEER_ID, kc->remote_peer_id);
    NLA_PUT_U8(msg, OVPN_ATTR_KEY_SLOT, key_slot);
    NLA_PUT_U16(msg, OVPN_ATTR_KEY_ID, kc->key_id);
    NLA_PUT_U16(msg, OVPN_ATTR_CIPHER_ALG, kc->cipher_alg);
    if (kc->cipher_alg == OVPN_CIPHER_ALG_AES_CBC) {
      NLA_PUT_U16(msg, OVPN_ATTR_HMAC_ALG, kc->hmac_alg);
    }

    key_dir = nla_nest_start(msg, OVPN_ATTR_ENCRYPT_KEY);
    NLA_PUT(msg, OVPN_KEY_DIR_ATTR_CIPHER_KEY, kc->encrypt.cipher_key_size,
            kc->encrypt.cipher_key);
    if (kc->cipher_alg == OVPN_CIPHER_ALG_AES_GCM) {
      NLA_PUT(msg, OVPN_KEY_DIR_ATTR_NONCE_TAIL, NONCE_LEN,
              kc->encrypt.nonce_tail);
    } else {
      NLA_PUT(msg, OVPN_KEY_DIR_ATTR_HMAC_KEY, kc->encrypt.hmac_key_size,
              kc->encrypt.hmac_key);
    }
    nla_nest_end(msg, key_dir);

    key_dir = nla_nest_start(msg, OVPN_ATTR_DECRYPT_KEY);
    NLA_PUT(msg, OVPN_KEY_DIR_ATTR_CIPHER_KEY, kc->decrypt.cipher_key_size,
            kc->decrypt.cipher_key);
    if (kc->cipher_alg == OVPN_CIPHER_ALG_AES_GCM) {
      NLA_PUT(msg, OVPN_KEY_DIR_ATTR_NONCE_TAIL, NONCE_LEN,
              kc->decrypt.nonce_tail);
    } else {
      NLA_PUT(msg, OVPN_KEY_DIR_ATTR_HMAC_KEY, kc->decrypt.hmac_key_size,
              kc->decrypt.hmac_key);
    }
    nla_nest_end(msg, key_dir);

    send_netlink_message(msg);
    return;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " set_keys() nla_put_failure");
  }

  /**
   * Swap keys between primary and secondary slots. Called
   * by client as part of rekeying logic to promote and demote keys.
   *
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  void swap_keys() {
    auto msg_ptr = create_msg(OVPN_CMD_SWAP_KEYS);
    auto* msg = msg_ptr.get();

    send_netlink_message(msg);
  }

  /**
   * Remove key from key slot.
   *
   * @param key_slot OVPN_KEY_SLOT_PRIMARY or OVPN_KEY_SLOT_SECONDARY
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  void del_key(unsigned int key_slot) {
    auto msg_ptr = create_msg(OVPN_CMD_DEL_KEY);
    auto* msg = msg_ptr.get();

    NLA_PUT_U8(msg, OVPN_ATTR_KEY_SLOT, key_slot);

    send_netlink_message(msg);
    return;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " del_key() nla_put_failure");
  }

  /**
   * Set peer properties. Currently used for keepalive settings.
   *
   * @param keepalive_interval how often to send ping packet in absence of
   * traffic
   * @param keepalive_timeout when to trigger keepalive_timeout in absence of
   * traffic
   * @throws netlink_error thrown if error occurs during sending netlink message
   */
  void set_peer(unsigned int keepalive_interval,
                unsigned int keepalive_timeout) {
    auto msg_ptr = create_msg(OVPN_CMD_SET_PEER);
    auto* msg = msg_ptr.get();

    NLA_PUT_U32(msg, OVPN_ATTR_KEEPALIVE_INTERVAL, keepalive_interval);
    NLA_PUT_U32(msg, OVPN_ATTR_KEEPALIVE_TIMEOUT, keepalive_timeout);

    send_netlink_message(msg);
    return;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " set_peer() nla_put_failure");
  }

  void stop() {
    if (!halt) {
      halt = true;

      try {
        stream->cancel();
        stream->close();
      } catch (...) {
        // ASIO might throw transport exceptions which I found is safe to ignore
      }

      // contrary to what ASIO doc says, stream->close() doesn't
      // cancel async read on netlink socket, explicitly close it here
      sock_ptr.reset();
      cb_ptr.reset();
    }
  }

private:
  void register_packet() {
    auto msg_ptr = create_msg(OVPN_CMD_REGISTER_PACKET);
    auto* msg = msg_ptr.get();

    send_netlink_message(msg);
  }

  struct mcast_handler_args {
    const char *group;
    int id;
  };

  /**
   * This callback is called by libnl. Here we enumerate netlink
   * multicast groups and find id of the one which name matches
   * ovpn-dco multicast group.
   *
   * @param msg netlink message to be processed
   * @param arg arguments passed by nl_cb_set() call
   * @return int id of ovpn-dco multicast group
   */
  static int mcast_family_handler(struct nl_msg *msg, void *arg) {
    struct mcast_handler_args *grp = static_cast<mcast_handler_args *>(arg);
    struct nlattr *tb[CTRL_ATTR_MAX + 1];
    struct genlmsghdr *gnlh = static_cast<genlmsghdr *>(
        nlmsg_data(reinterpret_cast<const nlmsghdr *>(nlmsg_hdr(msg))));
    struct nlattr *mcgrp;
    int rem_mcgrp;

    nla_parse(tb, CTRL_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
              genlmsg_attrlen(gnlh, 0), NULL);

    if (!tb[CTRL_ATTR_MCAST_GROUPS])
      return NL_SKIP;

    nla_for_each_nested(mcgrp, tb[CTRL_ATTR_MCAST_GROUPS], rem_mcgrp) {
      struct nlattr *tb_mcgrp[CTRL_ATTR_MCAST_GRP_MAX + 1];

      nla_parse(tb_mcgrp, CTRL_ATTR_MCAST_GRP_MAX,
                static_cast<nlattr *>(nla_data(mcgrp)), nla_len(mcgrp), NULL);

      if (!tb_mcgrp[CTRL_ATTR_MCAST_GRP_NAME] ||
          !tb_mcgrp[CTRL_ATTR_MCAST_GRP_ID])
        continue;
      if (strncmp((const char *)nla_data(tb_mcgrp[CTRL_ATTR_MCAST_GRP_NAME]),
                  grp->group, nla_len(tb_mcgrp[CTRL_ATTR_MCAST_GRP_NAME])))
        continue;
      grp->id = nla_get_u32(tb_mcgrp[CTRL_ATTR_MCAST_GRP_ID]);
      break;
    }

    return NL_SKIP;
  }

  /**
   * Return id of multicast group which ovpn-dco uses to
   * broadcast OVPN_CMD_DEL_PEER message
   *
   * @return int multicast group id
   */
  int get_mcast_id() {
    int ret = 1;
    struct mcast_handler_args grp = {
        .group = OVPN_NL_MULTICAST_GROUP_PEERS,
        .id = -ENOENT,
    };
    NlMsgPtr msg_ptr(nlmsg_alloc(), nlmsg_free);
    auto* msg = msg_ptr.get();

    NlCbPtr mcast_cb_ptr(nl_cb_alloc(NL_CB_DEFAULT), nl_cb_put);
    auto* mcast_cb = mcast_cb_ptr.get();

    int ctrlid = genl_ctrl_resolve(sock, "nlctrl");

    genlmsg_put(msg, 0, 0, ctrlid, 0, 0, CTRL_CMD_GETFAMILY, 0);
    NLA_PUT_STRING(msg, CTRL_ATTR_FAMILY_NAME, OVPN_NL_NAME);

    send_netlink_message(msg);

    nl_cb_err(
        mcast_cb, NL_CB_CUSTOM,
        [](struct sockaddr_nl *nla, struct nlmsgerr *err, void *arg) {
          int *ret = static_cast<int *>(arg);
          *ret = err->error;
          return (int)NL_STOP;
        },
        &ret);
    nl_cb_set(
        mcast_cb, NL_CB_ACK, NL_CB_CUSTOM,
        [](struct nl_msg *msg, void *arg) {
          int *ret = static_cast<int *>(arg);
          *ret = 0;
          return (int)NL_STOP;
        },
        &ret);

    nl_cb_set(mcast_cb, NL_CB_VALID, NL_CB_CUSTOM, mcast_family_handler, &grp);

    while (ret > 0)
      nl_recvmsgs(sock, mcast_cb);

    if (ret == 0)
      ret = grp.id;

    return ret;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, "get_mcast_id() nla_put_failure");
  }

  void handle_read(const openvpn_io::error_code &error) {
    if (halt)
      return;

    std::ostringstream os;
    if (error) {
      os << "error reading netlink message: " << error.message() << ", "
         << error;
      reset_buffer();
      int8_t cmd = -1;
      buf.write(&cmd, sizeof(cmd));
      buf_write_string(buf, os.str());
      read_handler->tun_read_handler(buf);
    }

    try {
      read_netlink_message();
      queue_genl_read();
    } catch (const netlink_error &e) {
      reset_buffer();
      int8_t cmd = -1;
      buf.write(&cmd, sizeof(cmd));
      buf_write_string(buf, e.what());
      read_handler->tun_read_handler(buf);
    }
  }

  void queue_genl_read() {
    stream->async_wait(openvpn_io::posix::stream_descriptor::wait_read,
                       [self = Ptr(this)](const openvpn_io::error_code &error) {
                         self->handle_read(error);
                       });
  }

  NlMsgPtr create_msg(enum ovpn_nl_commands cmd) {
    NlMsgPtr msg_ptr(nlmsg_alloc(), nlmsg_free);
    genlmsg_put(msg_ptr.get(), 0, 0, ovpn_dco_id, 0, 0, cmd, 0);
    NLA_PUT_U32(msg_ptr.get(), OVPN_ATTR_IFINDEX, ifindex);
    return msg_ptr;

  nla_put_failure:
    OPENVPN_THROW(netlink_error, " start_vpn() nla_put_failure");
  }

  void read_netlink_message() {
    // this is standard error code returned from kernel
    // and assigned inside ovpn_nl_cb_error()
    int ovpn_dco_err = 0;
    nl_cb_err(cb, NL_CB_CUSTOM, ovpn_nl_cb_error, &ovpn_dco_err);

    // this triggers reading callback, GeNL::message_received(),
    // and, if neccessary, ovpn_nl_cb_error() and returns netlink error code
    int netlink_err = nl_recvmsgs(sock, cb);

    if (ovpn_dco_err != 0)
      OPENVPN_THROW(netlink_error, "ovpn-dco error on receiving message: "
                                       << strerror(-ovpn_dco_err) << ", "
                                       << ovpn_dco_err);

    if (netlink_err < 0)
      OPENVPN_THROW(netlink_error, "netlink error on receiving message: "
                                       << nl_geterror(netlink_err) << ", "
                                       << netlink_err);
  }

  /**
   * This is called inside libnl's \c nl_recvmsgs() call
   * to process incoming netlink message.
   *
   * @param msg netlink message to be processed
   * @param arg argument passed by \c nl_cb_set()
   * @return int callback action
   */
  static int message_received(struct nl_msg *msg, void *arg) {
    GeNL *self = static_cast<GeNL *>(arg);

    struct genlmsghdr *gnlh = static_cast<genlmsghdr *>(
        nlmsg_data(reinterpret_cast<const nlmsghdr *>(nlmsg_hdr(msg))));
    struct nlattr *attrs[OVPN_ATTR_MAX + 1];

    nla_parse(attrs, OVPN_ATTR_MAX, genlmsg_attrdata(gnlh, 0),
              genlmsg_attrlen(gnlh, 0), NULL);

    switch (gnlh->cmd) {
    case OVPN_CMD_PACKET:
      if (!attrs[OVPN_ATTR_PACKET])
        OPENVPN_THROW(
            netlink_error,
            "missing OVPN_ATTR_PACKET attribute in OVPN_CMD_PACKET command");

      {
        self->reset_buffer();
        self->buf.write(&gnlh->cmd, sizeof(gnlh->cmd));
        self->buf.write(nla_data(attrs[OVPN_ATTR_PACKET]),
                        nla_len(attrs[OVPN_ATTR_PACKET]));
        // pass control channel message to upper layer
        self->read_handler->tun_read_handler(self->buf);
      }
      break;

    case OVPN_CMD_DEL_PEER:
      if (!attrs[OVPN_ATTR_DEL_PEER_REASON])
        OPENVPN_THROW(netlink_error, "missing OVPN_ATTR_DEL_PEER_REASON "
                                     "attribute in OVPN_CMD_DEL_PEER command");

      {
        self->reset_buffer();
        self->buf.write(&gnlh->cmd, sizeof(gnlh->cmd));
        uint8_t reason = nla_get_u8(attrs[OVPN_ATTR_DEL_PEER_REASON]);
        self->buf.write(&reason, sizeof(reason));
        self->read_handler->tun_read_handler(self->buf);
      }
      break;

    default:
      OPENVPN_LOG(__func__ << " unknown netlink command: " << (int)gnlh->cmd);
    }

    return NL_SKIP;
  }

  void reset_buffer() {
    // good enough values to handle control packets
    buf.reset(512, 3072,
              BufferAllocated::GROW | BufferAllocated::CONSTRUCT_ZERO |
                  BufferAllocated::DESTRUCT_ZERO);
  }

  /**
   * This is an error callback called by netlink for
   * error message processing customization.
   *
   * @param nla netlink address of the peer (value not needed here)
   * @param err netlink error message being processed
   * @param arg argument passed by \c nl_cb_err()
   * @return int callback action
   */
  static int ovpn_nl_cb_error(struct sockaddr_nl* /*nla*/,
                              struct nlmsgerr *err, void *arg) {
    struct nlmsghdr *nlh = (struct nlmsghdr *)err - 1;
    struct nlattr *tb_msg[NLMSGERR_ATTR_MAX + 1];
    int len = nlh->nlmsg_len;
    struct nlattr *attrs;
    int *ret = static_cast<int *>(arg);
    int ack_len = sizeof(*nlh) + sizeof(int) + sizeof(*nlh);

    *ret = err->error;

    if (!(nlh->nlmsg_flags & NLM_F_ACK_TLVS))
      return NL_STOP;

    if (!(nlh->nlmsg_flags & NLM_F_CAPPED))
      ack_len += err->msg.nlmsg_len - sizeof(*nlh);

    if (len <= ack_len)
      return NL_STOP;

    attrs = reinterpret_cast<nlattr *>((unsigned char *)nlh + ack_len);
    len -= ack_len;

    nla_parse(tb_msg, NLMSGERR_ATTR_MAX, attrs, len, NULL);
    if (tb_msg[NLMSGERR_ATTR_MSG]) {
      len = strnlen((char *)nla_data(tb_msg[NLMSGERR_ATTR_MSG]),
                    nla_len(tb_msg[NLMSGERR_ATTR_MSG]));
      OPENVPN_LOG(__func__ << " kernel error "
                           << (char *)nla_data(tb_msg[NLMSGERR_ATTR_MSG]));
    }

    return NL_STOP;
  }

  void send_netlink_message(struct nl_msg *msg) {
    int netlink_err = nl_send_auto(sock, msg);

    if (netlink_err < 0)
      OPENVPN_THROW(netlink_error, "netlink error on sending message: "
                                       << nl_geterror(netlink_err) << ", "
                                       << netlink_err);
  }

  NlSockPtr sock_ptr;
  NlCbPtr cb_ptr;

  struct nl_sock *sock;
  struct nl_cb *cb;

  int ovpn_dco_id;
  unsigned int ifindex;

  ReadHandler read_handler;

  bool halt;
  BufferAllocated buf;

  std::unique_ptr<openvpn_io::posix::stream_descriptor> stream;
};
} // namespace openvpn