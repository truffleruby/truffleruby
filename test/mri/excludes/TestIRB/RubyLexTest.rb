exclude :test_assignment_expression, "\"foo = bar\": should be an assignment expression"
exclude :test_assignment_expression_with_local_variable, "spurious; a /1;x=1#/: should be an assignment expression"
exclude :test_should_continue, "spurious; Wrong result of should_continue for: a. <false> expected but was <true>."