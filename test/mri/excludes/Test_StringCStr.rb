exclude :test_embed, "<0> expected but was <120>."
exclude :test_embedded_from_heap, "Encoding::CompatibilityError: incompatible character encodings: UTF-16BE and UTF-8"
exclude :test_frozen, "<0> expected but was <120>."
exclude :test_long, "<0> expected but was <120>."
exclude :test_rb_str_new_frozen_embed, "NoMethodError: undefined method `cstr_noembed' for class Bug::String"
exclude :test_shared, "retain-on-retag(on CI): FAILED (pid 31660 exit 99)"
exclude :test_wchar_embed, "<0> expected but was <30720>."
exclude :test_wchar_long, "UTF-16BE."
exclude :test_wchar_sub!, 'flaky; the given string is not compatible to the expected encoding "UTF_16BE", did you forget to convert it? (java.lang.IllegalArgumentException)'
