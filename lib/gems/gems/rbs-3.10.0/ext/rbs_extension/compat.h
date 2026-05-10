#ifdef __clang__
#define SUPPRESS_RUBY_HEADER_DIAGNOSTICS_BEGIN \
    _Pragma("clang diagnostic push")           \
        _Pragma("clang diagnostic ignored \"-Wc2x-extensions\"")
#define SUPPRESS_RUBY_HEADER_DIAGNOSTICS_END \
    _Pragma("clang diagnostic pop")
#else
#define SUPPRESS_RUBY_HEADER_DIAGNOSTICS_BEGIN
#define SUPPRESS_RUBY_HEADER_DIAGNOSTICS_END
#endif
