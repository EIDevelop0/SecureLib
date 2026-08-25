#pragma once

// Returns true if a tracer (JDWP debugger, gdb, Frida, strace, …)
// is attached to the current process. Reads TracerPid from
// /proc/self/status via native I/O so the check is not visible to
// Java-level hooks on java.io.File / FileInputStream.
bool isDebuggerAttached();
