exclude :test_dlopen_linker_script_group_linux, "NotImplementedError: NotImplementedError" if RUBY_PLATFORM.include?('linux')
exclude :test_dlopen_linker_script_input_linux, "retain-on-retag(fails on GitHub Actions only): NotImplementedError: NotImplementedError"
