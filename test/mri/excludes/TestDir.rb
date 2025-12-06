exclude :test_chdir_conflict, "Expected 0 to be an instance of RuntimeError, not Integer."
exclude :test_class_chdir, "[ArgumentError] exception expected, not #<TypeError: no implicit conversion of nil into String>."
exclude :test_for_fd, "Errno::EBADF expected but nothing was raised."
exclude :test_glob, "<[\"/b/b/e/.tmp/__test_dir__20241021-77734-auchi1/.\","
exclude :test_glob_cases, "Dir.glob should return the filename with actual cases on the filesystem." if RUBY_PLATFORM.include?('darwin')
exclude :test_glob_gc_for_fd, "Expected [] to not be empty."
exclude :test_glob_ignore_casefold_invalid_encoding, "Errno::EILSEQ: Invalid or incomplete multibyte or wide character - /private/var/folders/gr/3kff5w4s7779h6ycnt4gxfgm0000gn/T/__test_dir__20240910-84824-6ncred/�a123" if RUBY_PLATFORM.include?('darwin')
exclude :test_glob_too_may_open_files, "Errno::EMFILE expected but nothing was raised."
exclude :test_instance_chdir, "expected: /conflicting chdir during another chdir block\\n^\\/home\\/nirvdrum\\/dev\\/workspaces\\/truffleruby\\-ws\\/truffleruby\\/test\\/mri\\/tests\\/ruby\\/test_dir\\.rb:145:/"
exclude :test_unknown_keywords, "ArgumentError expected but nothing was raised."
