exclude :test_call, "ArgumentError: wrong number of arguments (given 1, expected 0)"
exclude :test_check_localvars, "RuntimeError: Continuations are unsupported on TruffleRuby"
exclude :test_create, "ArgumentError: wrong number of arguments (given 1, expected 0)"
exclude :test_tracing_with_thread_set_trace_func, "NoMethodError: private method 'set_trace_func' called for an instance of Thread"
