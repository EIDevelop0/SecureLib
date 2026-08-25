#pragma once

// Returns true if any Frida marker is present in /proc/self/maps
// (indicating frida-server or frida-gadget injected into this process).
bool scanMapsForFrida();
