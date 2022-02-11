// SWIG interface file for OpenVPN client

// enable director feature for OpenVPNClientBase virtual method callbacks
%module(directors="1") ovpncli
%feature("director") OpenVPNClient;

%include "std_string.i" // for std::string typemaps
%include "std_vector.i"

// top-level C++ implementation file
%{
#include "ovpncli.hpp"
%}

// ignore these ClientAPI::OpenVPNClient bases
%ignore openvpn::ClientAPI::LogReceiver;
%ignore openvpn::ExternalTun::Factory;
%ignore openvpn::ExternalTransport::Factory;

// modify exported C++ class names to incorporate their enclosing namespace
%rename(ClientAPI_OpenVPNClient) OpenVPNClient;
%rename(ClientAPI_OpenVPNClientHelper) OpenVPNClientHelper;
%rename(ClientAPI_TunBuilderBase) TunBuilderBase;
%rename(ClientAPI_ExternalPKIBase) ExternalPKIBase;
%rename(ClientAPI_ServerEntry) ServerEntry;
%rename(ClientAPI_EvalConfig) EvalConfig;
%rename(ClientAPI_ProvideCreds) ProvideCreds;
%rename(ClientAPI_SessionToken) SessionToken;
%rename(ClientAPI_DynamicChallenge) DynamicChallenge;
%rename(ClientAPI_KeyValue) KeyValue;
%rename(ClientAPI_Config) Config;
%rename(ClientAPI_Event) Event;
%rename(ClientAPI_ConnectionInfo) ConnectionInfo;
%rename(ClientAPI_Status) Status;
%rename(ClientAPI_LogInfo) LogInfo;
%rename(ClientAPI_InterfaceStats) InterfaceStats;
%rename(ClientAPI_TransportStats) TransportStats;
%rename(ClientAPI_MergeConfig) MergeConfig;
%rename(ClientAPI_ExternalPKIRequestBase) ExternalPKIRequestBase;
%rename(ClientAPI_ExternalPKICertRequest) ExternalPKICertRequest;
%rename(ClientAPI_ExternalPKISignRequest) ExternalPKISignRequest;
%rename(ClientAPI_RemoteOverride) RemoteOverride;

// declare vectors
namespace std {
  %template(ClientAPI_ServerEntryVector) vector<openvpn::ClientAPI::ServerEntry>;
  %template(ClientAPI_LLVector) vector<long long>;
  %template(ClientAPI_StringVec) vector<string>;
};

// interface to be bridged between C++ and target language
%include "openvpn/pki/epkibase.hpp"
%include "openvpn/tun/builder/base.hpp"
%import  "openvpn/tun/extern/fw.hpp"     // ignored
%import  "openvpn/transport/client/extern/fw.hpp"     // ignored
%include "ovpncli.hpp"
