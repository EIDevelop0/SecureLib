#include "zygisk_probe.h"

#include <fstream>
#include <string>

namespace {
constexpr const char* kMarkers[] = {
    "zygisk",
    "libzygisk",
    "riru",
    "libriru",
};
}  // namespace

bool isZygiskDetected() {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return false;
    std::string line;
    while (std::getline(maps, line)) {
        for (const char* marker : kMarkers) {
            if (line.find(marker) != std::string::npos) return true;
        }
    }
    return false;
}
