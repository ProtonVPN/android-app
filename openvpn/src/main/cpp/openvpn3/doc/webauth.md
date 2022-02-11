Contents of this documents
==========================
This document describes various web based protocols that are used together with OpenVPN. 
They are not part of core protocol of OpenVPN but are extremely closely related.
Interoperability between products building on OpenVPN is expected, so products
are expect to implement web based mechanisms according to this specification.

This document has a three parts: The first focuses web based authentication during
connection (AUTH_PENDING). The second describes standardised 
endpoints for client applications to retrieve a client profile. And final third section 
describes a simpler REST based interface that OpenVPN Connect Client and Access Server use to
download profiles to the client with minimal user interaction.

OpenVPN web auth protocol during Connect
========================================

This document describes the assumption and what client and server should implement
to facilitate a web based second factor login for OpenVPN.

Triggering web based authentication
-----------------------------------

To trigger web based authentication the client needs to signal its ability with `IV_SSO` and
the server needs to send the url to the client. The details are documented in
https://github.com/OpenVPN/openvpn/blob/master/doc/management-notes.txt
and are outside the scope of this document.

Receiving a WEB_AUTH/OPEN_URL request
------------------------------------

When the client receives an `WEB_AUTH` or `OPEN_URL` (deprecated) request to continue the 
authentication via web based authentication the client should directly open the web page or 
prompt the user to open the web page. This can be either can in an internal browser windows/webview 
or open the web page in an external browser. A web based login should be able to handle both cases.

There is a special "initially hidden" mode that is explained in the internal webview section.

Auth-token usage
----------------

The server side should try to minimize the number of web based authentication requests
to the client. This helps avoid issues when the client cannot reach the authentication 
portal during reconnect in addition to avoid disturbing the user with authentication 
requests in the web views. This disturbance will be even more annoying when an external 
browser window is opened by a client app not having the user's focus.

To avoid this, the server should send an authentication token to the client 
(see `--auth-token` and `--auth-gen-token` in the OpenVPN man page as well as 
`doc/management-notes.txt`). When the server sends an authentication token to the client,
the client will use that token instead of normal user credentials for the succeeding 
authentication requests, for as long as the token is considered valid by the server.

Internal webview integration API
--------------------------------

When the application uses an internal browser for web login process the API
allows tighter integration of the login. The application should append a
`embedded=true` parameter to the URL provided by the server to indicate the request
is made from an internal webview. The server may choose to serve a web page that 
integrates better into the flow of an app or ignore the parameter. 

If an internal webview is used, the app should clearly indicate that the user is interacting 
with an external website rather than with the app itself to avoid phishing attacks.

Initial hiding of a webview
---------------------------

There are situations where initially hiding the webview is desirable. Mainly when relying
on persistent storage/cookies on the client webview to determine if user input is required
or not. Starting all web auth hidden would break web login pages that are not specifically
designed to work with OpenVPN. Therefore, this feature must be implemented strictly as opt-in.
To enable this mode the flags of WEB_AUTH need to contain `hidden`. 
the url. When a client implements this mode it must also implement the State API to allow 
the web page to show the webview. A web login must not depend on the feature being present. 

State API
---------
This API is optional on both server and client side. Neither should the client side assume
that this API will be used during login process nor should the server side assume 
that this API is present. 

The reason to make this API optional on the client side is that client are allowed to open
the URL in the default browser of the user that has no specific OpenVPN support. On the server
side any identity provider should be able to be used. Unless the IdP is tightly integrated/designed
for OpenVPN web auth, it will not support the State API. 

To communicate the current state of the web based login with an application that 
implements an internal webview, the web page should use a JavaScript mechanism based on 
[postMessage](https://developer.mozilla.org/en-US/docs/Web/API/Window/postMessage). 

The wep page should send events with either appEvent.postMessage if appEvent exists and
window.parent.postMessage otherwise. The data is a JSON dictionary with `type` and an optional
`data` key if needed.

    appEvent.postMessage({"type": <event_string>, "data": <any>})

The approach of first trying to use `appEvent` and otherwise `window.parent` is to maximise
compatibility with various webview implementations. 
  
The events that are defined are:

For events where `data` is not defined, the `data` field is reserved and a client should ignore
it when present.

- `ACTION_REQUIRED`: This signal to the client that a user input is required. 

  If the webview is currently hidden, the webview needs to be shown to the user. 
  
  This event can be ignored if the client does not implement the hidden initial webview.
  
- `CONNECT_SUCCESS` and `CONNECT_FAILED`: The web login process was successful/failed. For the logic of the 
   application this state is just informal and can be shown in the VPN connection progress. The real 
   success/failure condition is determined by the VPN connection succeeding/failing.
   
   The application should close the webview on receiving this event.
   
-  `LOCATION_CHANGE`: This notifies the internal webview to change the title to the provided title as
   string in `data`, for example
    
       {"type": "LOCATION_CHANGE", "data": "Flower power VPN login step 2/3"}
       
    Web pages also need to implement the traditional changing of the web page to change the title when
    the page is opening in an external browser.
       
   
Certificate checks
------------------
A client must implement certificate checks. It is recommended to implement a user dialog 
step where the user is presented the subject information of an unknown certificate, which
the user can choose to accept or reject.

The client profile may also contain an embedded custom CA certificate. These CA certificates
should be considered trustworthy without interaction from the user. The embedded custom 
certificate are included in the client profile like this:

    # OVPN_ACCESS_SERVER_WEB_CA_BUNDLE_START
    # -----BEGIN CERTIFICATE-----
    # [...]
    # -----END CERTIFICATE-----
    # OVPN_ACCESS_SERVER_WEB_CA_BUNDLE_STOP

Profile download
================

There are two possible ways users may be provided client profiles: Browser based download
interface and a generic direct download interface. The browser based interface is best suited
for implementations providing an embedded browser experience, where the app can pick up the 
downloaded file and parse it further. The browser based interface is also useful when the server 
can provide different client profiles for various server regions. The generic direct download API 
will not require any user interaction and the received client profile can be parsed instantly.


Web based profile download
==========================


This API is intended to provide a uniform way for client to initiate the download of a profile
and have the login/download process performed through a webview. For an internal webview
the state API provides event to automatically trigger the import of a profile. If an external 
browser the OpenVPN profile should be offered as download with be content-type  
`application/x-openvpn-profile`.
 
Detection of web based profile download support
-----------------------------------------------

The classic way of a downloading profiles is outlined in the next section. To determine
what method has to be used to download a profile from a server, the client should do 
a `HEAD` or `GET` request to `https://servername/openvpn-api/profile`. If the response
contains a header `Ovpn-WebAuth` with any value, the web based method should be used.

The header `Ovpn-WebAuth` has the following format:

    Ovpn-WebAuth: providername,flags
   
The flags are also comma separated values. Currently, the followings flag that are defined:
    
    * hidden-webview   Starts the webview in hidden mode. See the web auth section for more details
    * external         Indicates that an internal webivew should NOT be used but instead a normal
                       browser is to be used.

In general websites should also report ovpn-webauth without `embedded=true` parameter to allow
clients without internal browser support to craft a url to open in an external browser that
contains the additional parameters like `deviceID` and indicates `tls-cryptv2` support.

To start the web based method the client should load the url
  
  https://servername/openvpn-api/profile
  
with the following optional parameters:

- `deviceID`    unique device ID, must be identical with the ID provided with `IV_HWADDR` if provided   
- `deviceModel`	model of connected device
- `deviceOS`	OS version of connected device
- `appVersion`	Version of OpenVPN client 
- `embedded`    Request is made from an internal webview. The server may choose to serve a
                web page that integrates better into the flow of an app or ignore the parameter.
- `tls-cryptv2`  Should be set to 1 if the client supports tls-crypt-v2
- `auth=method` The app supports the auth specified AUTH_PENDING authentication method. The parameter
                can be specified multiple times. The values for the method parameter
                are the same as in the `IV_SSO` parameter. For example an app supporting text based
                challenge response and the web based authentication would add 
                `auth=crtext&auth=openurl` to the request.

State API
---------

The state API is identical to the API used during connection. The web page can
also use the `LOCATION_CHANGED` event and additionally should provide the following
events.

 - `PROFILE_DOWNLOAD_FAILED`: The process in the web page to download the profile was unsuccessful. 
    The client should close the webview.
 - `PROFILE_DOWNLOAD_SUCCESS`: Download a of profile worked. The client should import the profile
     in the next step. `data` will contain the profile as json object with `profile` and `title` as
     keys. `profile` will contain the ovpn profile and title will have a suggested title
     for the new profile:
     
       {"type": "PROFILE_DOWNLOAD_SUCCESS", "data": {"profile": "<.ovpn profile>", "title": "<title>"}}
       
     To allow a client to connect directly after downloading a profile without requiring a
     web authentication on the first the connect, the two optional keys `vpn-session-user`
     and `vpn-session-token` can be present that act as `the auth-token` to be used on
     the first connect. These keys are not base64 encoded (unlike in the REST based download)
     since JSON has proper escaping:
     
       {"type": "PROFILE_DOWNLOAD_SUCCESS", "data": {"profile": "<.ovpn profile content>", "vpn-session-user": "foo\'bar", "vpn-session-token": "AT-123456789"}}

    The implementation of `vpn-session-token` and `vpn-session-user` is optional but strongly
    recommended to improve user experience.



openvpn://import-profile URL
============================
To trigger a direct import of a profile in an OpenVPN app an openvpn://import-profile/
link can be used. The syntax of the link is

    openvpn://import-profile/https://server/path/to/profile

The client will try to fetch the profile specified in the https:// URL and offer
the user the option to import the profile. The URL MUST NOT require any additional
authentication or require user interaction, e.g. by embedding a session ID or a one
time use token. The client MUST check certificates on a HTTPS connection and offer
a user a choice to accept or deny the connection if a self-signed certificate is
encountered.

The API MUST provide an openvpn profile with the MIME-type of `application/x-openvpn-profile`.
Any other response is invalid. The response MAY contain the  `VPN-Session-User` and
`VPN-Session-Token` headers as described in the Basic API section.

  
REST profile download API
=========================
REST is a simple and more lightweight interface to download profiles.
This is currently mainly implemented by OpenVPN Access Server API and 
Connect clients but can also be implemented by other server and clients.
The endpoint is https://servername/rest/methodname

Basic API
-----------
Access server calls profile that require username and password Userlogin
profile and profile without Autologin. This is also replicated in the
method name (`rest/GetAutologin` or `rest/GetUserlogin`) in the request URL. 
The configuration file is returned as a `text/plain` HTTP document (Note: this
in contrast to the right type `application/x-openvpn-profile`). Credentials 
are specified using HTTP Basic Authentication.  The REST API is implemented
through an SSL web server. The client must do the normal SSL certificate check
but should also allow a user to pin a self-signed certificate.

The client should also indicate support supported features that influence
profile generation and cannot be negotiated by the VPN protocol. 
Currently only TLS Crypt V2 support indicated by adding a tls-cryptv2=1
to the request

Typically a client app will present a username and password input field and
checkbox to enable/disable autologin. 

To get the Autologin configuration using curl (from a client without tls-cryptv2 support):

    $ curl -u USERNAME:PASSWORD https://asdemo.openvpn.net/rest/GetAutologin

To get the Userlogin (requiring authentication) configuration using curl, indicating
that the client supports tls crypt v2

    $ curl -u USERNAME:PASSWORD https://asdemo.openvpn.net/rest/GetUserlogin?tls-cryptv2=1&action=import

Additional for User login profiles headers to initiate a direct VPN are provided to avoid
double 2FA login in a short amount of time:

    VPN-Session-User: base64_encoded_user
    VPN-Session-Token: base64_encoded_pw
    
These parameters are optional but should be used as VPN session user and token when 
initiating a connection directly after downloading a profile.

Error reporting with Rest API
-----------------------------

Internally OpenVPN Access server used XMLRPC when this and the current 
implementation did not properly account for this, so error reporting is
done with xml replies. This is an unfortunate design/implementation detail.
So the rest error replies look more like XMLRPC errors than rest error.
They carry a HTTP error status.

Authentication failed (bad USERNAME or PASSWORD):

    <?xml version="1.0" encoding="UTF-8"?>
    <Error>
      <Type>Authorization Required</Type>
      <Synopsis>REST method failed</Synopsis>
      <Message>AUTH_FAILED: Server Agent XML method requires authentication (9007)</Message>
    </Error>

User does not have permission to use an Autologin profile:

    <?xml version="1.0" encoding="UTF-8"?>
    <Error>
      <Type>Internal Server Error</Type>
      <Synopsis>REST method failed</Synopsis>
      <Message>NEED_AUTOLOGIN: User 'USERNAME' lacks autologin privilege (9000)</Message>
    </Error>

User is not enrolled through the WEB client yet:

    <?xml version="1.0" encoding="UTF-8"?>
    <Error>
      <Type>Access denied</Type>
      <Synopsis>REST method failed</Synopsis>
      <Message>You must enroll this user in Authenticator first before you are allowed to retrieve a connection profile. (9008)</Message>
    </Error>

Challenge/response authentication
---------------------------------
The challenge/response protocol for the Rest web api mirrors the approach
taken by the old (non using AUTH-PENDING,cr-response) challenge/response
of the OpenVPN protocol.

When the server issues a challenge to the authentication
request. For example suppose we have a user called 'test' and a password
of 'mypass".  Get the OpenVPN config file:

    curl -u test:mypass https://ACCESS_SERVER/rest/GetUserlogin

But instead of immediately receiving the config file,
we might get a challenge instead:

    <Error>
      <Type>Authorization Required</Type>
      <Synopsis>REST method failed</Synopsis>
      <Message>CRV1:R,E:miwN39AlF4k40Fd8X8r9j74FuOoaJKJM:dGVzdA==:Turing test: what is 1 x 3? (9007)</Message>
    </Error>


a challenge is indicated by the "CRV1:" prefix in the <Message> (meaning
Challenge Response protocol Version 1).  The CRV1 message is formatted
as follows:

    CRV1:<flags>:<state_id>:<username_base64>:<challenge_text>

`flags` : a series of optional, comma-separated flags:
  - `E` : echo the response when the user types it
  - `R` : a response is required

`state_id`: an opaque string that should be returned to the server
along with the response.

`username_base64` : the username formatted as base64

`challenge_text` : the challenge text to be shown to the user

After showing the challenge_text and getting a response from the user
(if `R` flag is specified), the client should resubmit the REST
request with the `USERNAME:PASSWORD` field in the HTTP header set
as follows:

    <username decoded from username_base64>:CRV1::<state_id>::<response_text>

Where state_id is taken from the challenge request and `response_text`
is what the user entered in response to the `challenge_text`.
If the `R` flag is not present, `response_text` may be the empty
string.

Using curl to respond to the turing test given in the example above:

    curl -u "test:CRV1::miwN39AlF4k40Fd8X8r9j74FuOoaJKJM::3" https://ACCESS_SERVER/rest/GetUserlogin

If the challenge response (In this case '3' in response to the turing
test) is verified by the server, it will then return the configuration
file per the GetUserlogin method.
