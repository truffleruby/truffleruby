exclude :test_no_memory_leak, "very slow: 70.93s on truffleruby 34.0.0-dev-0b9bf975, like ruby 3.4.7, GraalVM CE JVM [x86_64-linux] with AMD Ryzen 7 9800X3D 8-Core Processor: ( 16 vCPUs)"
exclude :test_sexp_no_memory_leak, "rss: 1715376128 => 3474296832."
exclude :test_yydebug_ident, "ArgumentError: unhandled rb_sprintf arg type 26"
exclude :test_yydebug_string, "ArgumentError: unhandled rb_sprintf arg type 26"
