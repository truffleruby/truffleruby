#!ruby -s
# frozen_string_literal: true

require 'mkmf'
require 'rbconfig'

def main
  $objs = %w(eventids1.o eventids2.o ripper.o ripper_init.o)
  if defined?(::TruffleRuby)
    # TruffleRuby compiles additional objects required for ripper (parse.o node.o ruby_parser.o).
    # ripper.c requires extra functions such as:
    # * rb_str_to_parser_string(), rb_parser_set_location_of_none(), ruby_global_name_punct_bits from parse.c
    # * rb_parser_lex_get_str() from ruby_parser.c
    # * rb_ast_new() from node.c
    # You'll notice that ripper.c and parse.c are basically copies of each other (same in CRuby),
    # but the key point is ripper.c is compiled with RIPPER defined, and parse.c is compiled with RIPPER undefined so they effectively define different functions.
    # These files do both `#ifdef RIPPER` and `#ifndef RIPPER` so there is no way to "just include everything with a single file".
    # parse.c is included in ripper.so and not libtruffleruby.so because it's a hack and internal stuff so we want to expose it as little as possible.
    $objs.concat %w[parse.o node.o ruby_parser.o]
  end
  $distcleanfiles.concat %w(ripper.y ripper.c eventids1.c eventids1.h eventids2table.c ripper_init.c)
  $cleanfiles.concat %w(ripper.E ripper.output y.output .eventids2-check)
  $defs << '-DRIPPER'
  $defs << '-DRIPPER_DEBUG' if $debug
  $VPATH << '$(topdir)' << '$(top_srcdir)'
  $INCFLAGS << ' -I$(topdir) -I$(top_srcdir)'
  create_makefile 'ripper'
end

main
