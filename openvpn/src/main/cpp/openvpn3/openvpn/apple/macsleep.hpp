//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#ifndef OPENVPN_APPLE_MACSLEEP_H
#define OPENVPN_APPLE_MACSLEEP_H

#include <mach/mach_port.h>
#include <mach/mach_interface.h>
#include <mach/mach_init.h>

#include <IOKit/pwr_mgt/IOPMLib.h>
#include <IOKit/IOMessage.h>

#include <openvpn/common/size.hpp>

namespace openvpn {
class MacSleep
{
    MacSleep(const MacSleep &) = delete;
    MacSleep &operator=(const MacSleep &) = delete;

  public:
    MacSleep()
        : root_port(0),
          notifyPortRef(nullptr),
          notifierObject(0)
    {
    }

    virtual ~MacSleep()
    {
        mac_sleep_stop();
    }

    bool mac_sleep_start()
    {
        if (!root_port)
        {
            root_port = IORegisterForSystemPower(this, &notifyPortRef, callback_static, &notifierObject);
            if (!root_port)
                return false;
            CFRunLoopAddSource(CFRunLoopGetCurrent(), IONotificationPortGetRunLoopSource(notifyPortRef), kCFRunLoopCommonModes);
        }
        return true;
    }

    void mac_sleep_stop()
    {
        if (root_port)
        {
            // remove the sleep notification port from the application runloop
            CFRunLoopRemoveSource(CFRunLoopGetCurrent(),
                                  IONotificationPortGetRunLoopSource(notifyPortRef),
                                  kCFRunLoopCommonModes);

            // deregister for system sleep notifications
            IODeregisterForSystemPower(&notifierObject);

            // IORegisterForSystemPower implicitly opens the Root Power Domain IOService
            // so we close it here
            IOServiceClose(root_port);

            // destroy the notification port allocated by IORegisterForSystemPower
            IONotificationPortDestroy(notifyPortRef);

            // reset object members
            root_port = 0;
            notifyPortRef = nullptr;
            notifierObject = 0;
        }
    }

    virtual void notify_sleep() = 0;
    virtual void notify_wakeup() = 0;

  private:
    static void callback_static(void *arg, io_service_t service, natural_t messageType, void *messageArgument)
    {
        MacSleep *self = (MacSleep *)arg;
        self->callback(service, messageType, messageArgument);
    }

    void callback(io_service_t service, natural_t messageType, void *messageArgument)
    {
        switch (messageType)
        {
        case kIOMessageCanSystemSleep:
            IOAllowPowerChange(root_port, (long)messageArgument);
            break;
        case kIOMessageSystemWillSleep:
            notify_sleep();
            IOAllowPowerChange(root_port, (long)messageArgument);
            break;
        case kIOMessageSystemHasPoweredOn:
            notify_wakeup();
            break;
        }
    }

    // a reference to the Root Power Domain IOService
    io_connect_t root_port;

    // notification port allocated by IORegisterForSystemPower
    IONotificationPortRef notifyPortRef;

    // notifier object, used to deregister later
    io_object_t notifierObject;
};
} // namespace openvpn

#endif
