# frozen_string_literal: true

# Ripper is implemented with Ruby::Prism, the Prism built into TruffleRuby,
# so it does not depend on which version of the prism gem is activated.
require 'ruby/prism'
require 'ruby/prism/translation/ripper/shim'
