#pragma once

// Returns true if Zygisk (Magisk in-process injection) or Riru is
// present in the current process. Detection is based on injected
// library names appearing in /proc/self/maps — Zygisk/Riru cannot
// avoid mapping their code into the target process.
bool isZygiskDetected();
