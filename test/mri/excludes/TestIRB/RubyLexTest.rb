exclude :test_interpolate_token_with_heredoc_and_unclosed_embexpr, "<\"<<A-②\\n\" + \"\\\#{③*<<B/④\\n\" + \"\\\#{⑤&<<C|⑥\\n\"> expected but was <\"<<A-②\\n\">."
exclude :test_unterminated_heredoc_string_literal, "<\"<<A\"> expected but was <nil>."
