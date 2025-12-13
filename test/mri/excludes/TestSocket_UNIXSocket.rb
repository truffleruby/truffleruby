exclude :test_abstract_namespace, "Expected /\\0foo\\z/ to match \"\\x01\\x00\\x00foo\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\"." if RUBY_PLATFORM.include?('linux')
exclude :test_abstract_unix_server, "ArgumentError: string contains null byte" if RUBY_PLATFORM.include?('linux')
exclude :test_abstract_unix_server_socket, "<\"\\u0000ruby-test_unix\"> expected but was <\"\">." if RUBY_PLATFORM.include?('linux')
exclude :test_abstract_unix_socket_econnrefused, "[Errno::ECONNREFUSED] exception expected, not #<ArgumentError: string contains null byte>." if RUBY_PLATFORM.include?('linux')
exclude :test_autobind, "Expected /\\A\\0[0-9a-f]{5}\\z/ to match \"\"." if RUBY_PLATFORM.include?('linux')
exclude :test_cloexec, "Failed assertion, no message given."
exclude :test_getcred_ucred, "Expected / pid=142305 / to match \"#<Socket::Option:    \\\"\\\\xE1+\\\\x02\\\\x00\\\\xE8\\\\x03\\\\x00\\\\x00\\\\xE8\\\\x03\\\\x00\\\\x00\\\">\"." if RUBY_PLATFORM.include?('linux')
exclude :test_getcred_xucred, "NameError: uninitialized constant Socket::LOCAL_PEERCRED" if RUBY_PLATFORM.include?('darwin')
exclude :test_initialize, "spurious; [ArgumentError] exception expected, not #<SocketError: Result too large for supplied buffer>."
exclude :test_sendcred_ucred, "SocketError: Result too large for supplied buffer" if RUBY_PLATFORM.include?('linux')
exclude :test_seqpacket_pair, "Expected \"\" to be nil."
exclude :test_socket_pair_with_block, "<:return_value> expected but was <[#<Socket:fd 80>, #<Socket:fd 81>]>."
exclude :test_unix_socket_pair_with_block, "NoMethodError: undefined method `[]' for nil"
