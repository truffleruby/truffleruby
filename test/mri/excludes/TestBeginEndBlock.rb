exclude :test_exitcode_in_at_exit, "| -e:2:in `block in <main>': undefined method `nope' for #<Object:0xb8> (NoMethodError)"
exclude :test_internal_errinfo_at_exit, "NotImplementedError: fork is not available"
exclude :test_propagate_signaled, "needs investigation"
