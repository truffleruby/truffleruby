// See updating-ruby.md for instructions on regenerating config headers.
#ifdef __APPLE__

#if defined(__aarch64__)
#include <truffleruby/config_darwin_aarch64.h>
#else
#error Unsupported platform
#endif

#else

#if defined(__x86_64__)
#include <truffleruby/config_linux_amd64.h>
#elif defined(__aarch64__)
#include <truffleruby/config_linux_aarch64.h>
#else
#error Unsupported platform
#endif

#endif
