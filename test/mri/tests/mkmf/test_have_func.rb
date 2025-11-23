# frozen_string_literal: false
require_relative 'base'
require 'tempfile'

class TestMkmfHaveFunc < TestMkmf
  def test_have_func
    # TruffleRuby change `rb_init` to `rb_intern` so the test runs because TruffleRuby doesn't have `rb_init`.
    assert_equal(true, have_func("rb_intern"), MKMFLOG)
    assert_include($defs, '-DHAVE_RB_INTERN')
  end

  def test_not_have_func
    # TruffleRuby change `no_rb_init` to `no_rb_intern` so the test runs because TruffleRuby doesn't have `rb_init`.
    assert_equal(false, have_func("no_rb_intern"), MKMFLOG)
    assert_not_include($defs, '-DHAVE_RB_INTERN')
  end
end
