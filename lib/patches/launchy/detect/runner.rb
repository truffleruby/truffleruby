# truffleruby_primitives: true

# Copyright (c) 2007-2013 Jeremy Hinegardner
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
# ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
# ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
# OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

require Primitive.get_original_require(__FILE__)

module Launchy::Detect
  class Runner
    def self.detect
      host_os_family = Launchy::Detect::HostOsFamily.detect
      ruby_engine    = Launchy::Detect::RubyEngine.detect

      return Windows.new if host_os_family.windows?

      if ruby_engine.jruby? || ruby_engine.truffleruby? then
        # This is a bit misnamed now. But the point is the Jruby runner class avoids using 'fork'.
        return Jruby.new
      end

      return Forkable.new
    end
  end
end
