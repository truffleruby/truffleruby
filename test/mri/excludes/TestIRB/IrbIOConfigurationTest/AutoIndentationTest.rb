exclude :test_broken_heredoc, "Calculated the wrong number of indent level for:  def foo"
exclude :test_heredoc_keep_indent_spaces, "Calculated the wrong number of indent level for:  def foo"
exclude :test_heredoc_with_indent, "Calculated the wrong number of indent level for:  <<~Q+<<~R."
exclude :test_pasted_code_keep_base_indent_spaces_with_heredoc, "Calculated the wrong number of indent level for:      def foo"
