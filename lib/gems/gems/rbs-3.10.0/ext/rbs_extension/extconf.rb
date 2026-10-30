require 'mkmf'

$INCFLAGS << " -I$(top_srcdir)" if $extmk
$INCFLAGS << " -I$(srcdir)/../../include"

$VPATH << "$(srcdir)/../../src"
$VPATH << "$(srcdir)/../../src/util"
$VPATH << "$(srcdir)/ext/rbs_extension"

root_dir = File.expand_path('../../../', __FILE__)
$srcs = Dir.glob("#{root_dir}/src/**/*.c") +
        Dir.glob("#{root_dir}/ext/rbs_extension/*.c")

append_cflags [
  '-std=gnu99',
  '-Wimplicit-fallthrough',
  '-Wunused-result',
  '-Wc++-compat',
]

if ENV['DEBUG']
  append_cflags ['-O0', '-pg']
else
  append_cflags ['-DNDEBUG']
end
if ENV["TEST_NO_C23"]
  puts "Adding -Wc2x-extensions to CFLAGS"
  $CFLAGS << " -Werror -Wc2x-extensions"
end

create_makefile 'rbs_extension'

# Only generate compile_commands.json when compiling through Rake tasks
# This is to avoid adding extconf_compile_commands_json as a runtime dependency
if ENV["COMPILE_COMMANDS_JSON"]
  require 'extconf_compile_commands_json'
  ExtconfCompileCommandsJson.generate!
  ExtconfCompileCommandsJson.symlink!
end
