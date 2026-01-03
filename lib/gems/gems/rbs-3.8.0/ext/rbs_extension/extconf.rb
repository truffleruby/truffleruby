require 'mkmf'

if defined?(::TruffleRuby)
  # On TruffleRuby, all .c files are copied to the same directory (src/main/c/rbs)
  # and include files are copied to an include/ subdirectory
  $INCFLAGS << " -I$(srcdir)/include"
  $srcs = Dir.glob("#{__dir__}/*.c")
else
# original code, not indented to make diff nicer and re-applying patches easier
$INCFLAGS << " -I$(top_srcdir)" if $extmk
$INCFLAGS << " -I$(srcdir)/../../include"

$VPATH << "$(srcdir)/../../src"
$VPATH << "$(srcdir)/ext/rbs_extension"

root_dir = File.expand_path('../../../', __FILE__)
$srcs = Dir.glob("#{root_dir}/src/*.c") +
        Dir.glob("#{root_dir}/ext/rbs_extension/*.c")
end

append_cflags ['-std=gnu99']
create_makefile 'rbs_extension'
