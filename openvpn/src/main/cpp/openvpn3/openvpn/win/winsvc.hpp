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

// windows service utilities

#include <windows.h>

#include <string>
#include <cstring>
#include <vector>
#include <memory>
#include <mutex>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/win/winerr.hpp>
#include <openvpn/win/modname.hpp>

namespace openvpn {
namespace Win {
class Service
{
  public:
    OPENVPN_EXCEPTION(winsvc_error);

    struct Config
    {
        std::string name;
        std::string display_name;
        std::vector<std::string> dependencies;
        bool autostart = false;
        bool restart_on_fail = false;
    };

    Service(const Config &config_arg)
        : config(config_arg)
    {
        std::memset(&status, 0, sizeof(status));
        status_handle = nullptr;
        checkpoint = 1;
    }

    bool is_service() const
    {
        return bool(service);
    }

    void install()
    {
        // open service control manager
        ScopedSCHandle scmgr(::OpenSCManagerW(
            NULL,                    // local computer
            NULL,                    // ServicesActive database
            SC_MANAGER_ALL_ACCESS)); // full access rights
        if (!scmgr.defined())
        {
            const Win::LastError err;
            OPENVPN_THROW(winsvc_error, "OpenSCManagerW failed: " << err.message());
        }

        // convert service names to wide string
        const std::wstring wname = wstring::from_utf8(config.name);
        const std::wstring wdisplay_name = wstring::from_utf8(config.display_name);

        // get dependencies
        const std::wstring deps = wstring::pack_string_vector(config.dependencies);

        // create the service

        // as stated here https://docs.microsoft.com/en-us/windows/win32/api/winsvc/nf-winsvc-createservicew
        // path must be quoted if it contains a space
        const std::wstring binary_path = L"\"" + Win::module_name() + L"\"";
        ScopedSCHandle svc(::CreateServiceW(
            scmgr(),                                                      // SCM database
            wname.c_str(),                                                // name of service
            wdisplay_name.c_str(),                                        // service name to display
            SERVICE_ALL_ACCESS,                                           // desired access
            SERVICE_WIN32_OWN_PROCESS,                                    // service type
            config.autostart ? SERVICE_AUTO_START : SERVICE_DEMAND_START, // start type
            SERVICE_ERROR_NORMAL,                                         // error control type
            binary_path.c_str(),                                          // path to service's binary
            NULL,                                                         // no load ordering group
            NULL,                                                         // no tag identifier
            deps.c_str(),                                                 // dependencies
            NULL,                                                         // LocalSystem account
            NULL));                                                       // no password
        if (!svc.defined())
        {
            const Win::LastError err;
            OPENVPN_THROW(winsvc_error, "CreateServiceW failed: " << err.message());
        }
        if (config.restart_on_fail)
        {
            // http://stackoverflow.com/questions/3242505/how-to-create-service-which-restarts-on-crash
            SERVICE_FAILURE_ACTIONS servFailActions;
            SC_ACTION failActions[3];

            failActions[0].Type = SC_ACTION_RESTART; // Failure action: Restart Service
            failActions[0].Delay = 1000;             // number of seconds to wait before performing failure action, in milliseconds
            failActions[1].Type = SC_ACTION_RESTART;
            failActions[1].Delay = 5000;
            failActions[2].Type = SC_ACTION_RESTART;
            failActions[2].Delay = 30000;

            servFailActions.dwResetPeriod = 86400; // Reset Failures Counter, in Seconds
            servFailActions.lpCommand = NULL;      // Command to perform due to service failure, not used
            servFailActions.lpRebootMsg = NULL;    // Message during rebooting computer due to service failure, not used
            servFailActions.cActions = 3;          // Number of failure action to manage
            servFailActions.lpsaActions = failActions;

            ::ChangeServiceConfig2(svc(), SERVICE_CONFIG_FAILURE_ACTIONS, &servFailActions); // Apply above settings
            ::StartService(svc(), 0, NULL);
        }
    }

    void remove()
    {
        // convert service name to wide string
        const std::wstring wname = wstring::from_utf8(config.name);

        // open service control manager
        ScopedSCHandle scmgr(::OpenSCManagerW(
            NULL,                    // local computer
            NULL,                    // ServicesActive database
            SC_MANAGER_ALL_ACCESS)); // full access rights
        if (!scmgr.defined())
        {
            const Win::LastError err;
            OPENVPN_THROW(winsvc_error, "OpenSCManagerW failed: " << err.message());
        }

        // open the service
        ScopedSCHandle svc(::OpenServiceW(
            scmgr(),                 // SCM database
            wname.c_str(),           // name of service
            SC_MANAGER_ALL_ACCESS)); // access requested
        if (!svc.defined())
        {
            const Win::LastError err;
            OPENVPN_THROW(winsvc_error, "OpenServiceW failed: " << err.message());
        }

        // remove the service
        if (!::DeleteService(svc()))
        {
            const Win::LastError err;
            OPENVPN_THROW(winsvc_error, "DeleteService failed: " << err.message());
        }
    }

    void start()
    {
        service = this;

        // convert service name to wide string
        const std::wstring wname = wstring::from_utf8(config.name);

        // store it in a raw wchar_t array
        std::unique_ptr<wchar_t[]> wname_raw = wstring::to_wchar_t(wname);

        const SERVICE_TABLE_ENTRYW dispatch_table[] = {
            {wname_raw.get(), (LPSERVICE_MAIN_FUNCTIONW)svc_main_static},
            {NULL, NULL}};

        // This call returns when the service has stopped.
        // The process should simply terminate when the call returns.
        if (!::StartServiceCtrlDispatcherW(dispatch_table))
        {
            const Win::LastError err;
            OPENVPN_THROW(winsvc_error, "StartServiceCtrlDispatcherW failed: " << err.message());
        }
    }

    void report_service_running()
    {
        if (is_service())
            report_service_status(SERVICE_RUNNING, NO_ERROR, 0);
    }

    // The work of the service.
    virtual void service_work(DWORD argc, LPWSTR *argv) = 0;

    // Called by service control manager in another thread
    // to signal the service_work() method to exit.
    virtual void service_stop() = 0;

  private:
    class ScopedSCHandle
    {
        ScopedSCHandle(const ScopedSCHandle &) = delete;
        ScopedSCHandle &operator=(const ScopedSCHandle &) = delete;

      public:
        ScopedSCHandle()
            : handle(nullptr)
        {
        }

        explicit ScopedSCHandle(SC_HANDLE h)
            : handle(h)
        {
        }

        SC_HANDLE release()
        {
            const SC_HANDLE ret = handle;
            handle = nullptr;
            return ret;
        }

        bool defined() const
        {
            return bool(handle);
        }

        SC_HANDLE operator()() const
        {
            return handle;
        }

        SC_HANDLE *ref()
        {
            return &handle;
        }

        void reset(SC_HANDLE h)
        {
            close();
            handle = h;
        }

        // unusual semantics: replace handle without closing it first
        void replace(SC_HANDLE h)
        {
            handle = h;
        }

        bool close()
        {
            if (defined())
            {
                const BOOL ret = ::CloseServiceHandle(handle);
                handle = nullptr;
                return ret != 0;
            }
            else
                return true;
        }

        ~ScopedSCHandle()
        {
            close();
        }

        ScopedSCHandle(ScopedSCHandle &&other) noexcept
        {
            handle = other.handle;
            other.handle = nullptr;
        }

        ScopedSCHandle &operator=(ScopedSCHandle &&other) noexcept
        {
            close();
            handle = other.handle;
            other.handle = nullptr;
            return *this;
        }

      private:
        SC_HANDLE handle;
    };

    static VOID WINAPI svc_main_static(DWORD argc, LPWSTR *argv)
    {
        service->svc_main(argc, argv);
    }

    void svc_main(DWORD argc, LPWSTR *argv)
    {
        try
        {
            // convert service name to wide string
            const std::wstring wname = wstring::from_utf8(config.name);

            // Register the handler function for the service
            status_handle = ::RegisterServiceCtrlHandlerW(
                wname.c_str(),
                svc_ctrl_handler_static);
            if (!status_handle)
            {
                const Win::LastError err;
                OPENVPN_THROW(winsvc_error, "RegisterServiceCtrlHandlerW failed: " << err.message());
            }

            // These SERVICE_STATUS members remain as set here
            status.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
            status.dwServiceSpecificExitCode = 0;

            // Report initial status to the SCM
            report_service_status(SERVICE_START_PENDING, NO_ERROR, 0);

            // Perform service-specific initialization and work
            service_work(argc, argv);

            // Tell SCM we are done
            report_service_status(SERVICE_STOPPED, NO_ERROR, 0);
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("service exception: " << e.what());
            report_service_status(SERVICE_STOPPED, NO_ERROR, 0);
        }
    }

    // Purpose:
    //   Called by SCM whenever a control code is sent to the service
    //   using the ControlService function.
    //
    // Parameters:
    //   dwCtrl - control code
    void svc_ctrl_handler(DWORD dwCtrl)
    {
        // Handle the requested control code.
        switch (dwCtrl)
        {
        case SERVICE_CONTROL_STOP:
            report_service_status(SERVICE_STOP_PENDING, NO_ERROR, 0);

            // Signal the service to stop.
            try
            {
                service_stop();
            }
            catch (const std::exception &e)
            {
                OPENVPN_LOG("service stop exception: " << e.what());
            }
            report_service_status(0, NO_ERROR, 0);
            return;

        case SERVICE_CONTROL_INTERROGATE:
            break;

        default:
            break;
        }
    }

    static VOID WINAPI svc_ctrl_handler_static(DWORD dwCtrl)
    {
        service->svc_ctrl_handler(dwCtrl);
    }

    // Purpose:
    //   Sets the current service status and reports it to the SCM.
    //
    // Parameters:
    //   dwCurrentState - The current state (see SERVICE_STATUS)
    //   dwWin32ExitCode - The system error code
    //   dwWaitHint - Estimated time for pending operation, in milliseconds
    void report_service_status(DWORD dwCurrentState,
                               DWORD dwWin32ExitCode,
                               DWORD dwWaitHint)
    {
        std::lock_guard<std::mutex> lock(mutex);

        // Fill in the SERVICE_STATUS structure.
        if (dwCurrentState)
            status.dwCurrentState = dwCurrentState;
        status.dwWin32ExitCode = dwWin32ExitCode;
        status.dwWaitHint = dwWaitHint;

        if (dwCurrentState == SERVICE_START_PENDING)
            status.dwControlsAccepted = 0;
        else
            status.dwControlsAccepted = SERVICE_ACCEPT_STOP;
        if ((dwCurrentState == SERVICE_RUNNING) || (dwCurrentState == SERVICE_STOPPED))
            status.dwCheckPoint = 0;
        else
            status.dwCheckPoint = checkpoint++;

        // Report the status of the service to the SCM.
        ::SetServiceStatus(status_handle, &status);
    }

    static Service *service; // GLOBAL

    const Config config;

    SERVICE_STATUS status;
    SERVICE_STATUS_HANDLE status_handle;
    DWORD checkpoint;

    std::mutex mutex;
};

Service *Service::service = nullptr; // GLOBAL
} // namespace Win
} // namespace openvpn
