#include "frida_scan.h"

#include <fstream>
#include <string>

namespace {
// Substrings that appear in a memory map when Frida (server or gadget) is
// injected into the process. Kept in sync with the pre-native Kotlin
// FridaCheck implementation.
constexpr const char* kMarkers[] = {
    "frida",
    "gum-js-loop",
    "gadget",
};
}  // namespace

bool scanMapsForFrida() {
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
