# Ruby::Box needs to be enabled at startup with RUBY_BOX=1 on CRuby.
# When Ruby::Box is not enabled, the specs in this directory are guarded out
# and subprocess_spec.rb runs them all in a single subprocess with RUBY_BOX=1 instead,
# to avoid a subprocess per spec.
def ruby_box_enabled?
  defined?(Ruby::Box) and Ruby::Box.enabled?
end

# Require the main spec_helper.rb at the end to let `ruby ...spec.rb` work
require_relative '../../spec_helper'
