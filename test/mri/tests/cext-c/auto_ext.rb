# frozen_string_literal: false
def auto_ext(feat = $0[%r[/test/mri/tests/(cext-c/.*)/extconf.rb\z], 1], inc: false)
  $INCFLAGS << " -I$(topdir) -I$(top_srcdir)" if inc

  # internal.h is under $(includedir) together with normal headers in TruffleRuby instead of under $(top_srcdir)
  $INCFLAGS << " -I$(includedir)" if inc && defined?(::TruffleRuby)
  
  $srcs = Dir[File.join($srcdir, "*.{#{SRC_EXT.join(%q{,})}}")]
  inits = $srcs.map {|s| File.basename(s, ".*")}
  inits.delete("init")
  inits.map! {|s|"X(#{s})"}
  $defs << "-DTEST_INIT_FUNCS(X)=\"#{inits.join(' ')}\""
  create_header
  create_makefile(feat)
end
