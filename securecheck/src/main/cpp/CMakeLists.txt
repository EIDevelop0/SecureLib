cmake_minimum_required(VERSION 3.22.1)
project(securecheck)

add_library(securecheck SHARED
    native_checks.cpp
    frida_scan.cpp
    debugger_probe.cpp
    zygisk_probe.cpp
)

target_compile_features(securecheck PRIVATE cxx_std_17)
target_compile_options(securecheck PRIVATE -fvisibility=hidden -Wall)
