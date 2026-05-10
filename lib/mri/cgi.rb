# frozen_string_literal: true

require "cgi/escape"
# TruffleRuby: do not rely on Gem::BUNDLED_GEMS in stdlib:
# Gem might not exist (--disable-gems) and Gem::BUNDLED_GEMS is not loaded eagerly on TruffleRuby.
warn <<-WARNING, uplevel: 1 if $VERBOSE
CGI library is removed from Ruby 4.0. Please use cgi/escape instead for CGI.escape and CGI.unescape features.
If you need to use the full features of CGI library, Please install cgi gem.
WARNING
