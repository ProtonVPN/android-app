
#include "breakpad.h"
#include "client/linux/handler/exception_handler.h"


static
bool DumpCallback(const google_breakpad::MinidumpDescriptor& descriptor,
                  void* context,
                  bool succeeded) {
    printf("Dump path: %s\n", descriptor.path());
    fflush(stdout);
    fflush(stderr);
    return succeeded;
}

static google_breakpad::MinidumpDescriptor* desc;
static google_breakpad::ExceptionHandler* eh;

void breakpad_setup(void)
{
    printf("Initializing Google Breakpad!\n");
    desc = new google_breakpad::MinidumpDescriptor("/data/data/de.blinkt.openvpn/cache");
    eh = new google_breakpad::ExceptionHandler(*desc, NULL, DumpCallback, NULL, true,-1);
}

void breakpad_dodump(void)
{
    eh->WriteMinidump();
}
