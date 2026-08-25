#include "debugger_probe.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace {
constexpr const char* kTracerPidPrefix = "TracerPid:";
constexpr size_t kTracerPidPrefixLen = 10;
}  // namespace

bool isDebuggerAttached() {
    FILE* status = std::fopen("/proc/self/status", "r");
    if (status == nullptr) return false;

    char line[256];
    bool traced = false;
    while (std::fgets(line, sizeof(line), status) != nullptr) {
        if (std::strncmp(line, kTracerPidPrefix, kTracerPidPrefixLen) == 0) {
            const long pid = std::strtol(line + kTracerPidPrefixLen, nullptr, 10);
            traced = (pid != 0);
            break;
        }
    }
    std::fclose(status);
    return traced;
}
