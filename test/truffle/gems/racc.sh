#!/usr/bin/env bash

source test/truffle/common.sh.inc

# The bundled gem omits cparse. Build it out of tree to exercise the same choice as an installed gem.
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
mkdir "$dir/racc"
(
  cd "$dir/racc"
  jt ruby "$repo"/lib/gems/gems/racc-*/ext/racc/cparse/extconf.rb
  make
)

jt ruby -I"$dir" -rracc/parser -e '
  abort "expected Ruby Racc" unless Racc::Parser.racc_runtime_type == "ruby"
  abort "cparse was already loaded" unless require "racc/cparse.so"
'
jt ruby -I"$dir" -e '
  Racc_No_Extensions = false
  require "racc/parser"
  abort "expected native Racc" unless Racc::Parser.racc_runtime_type == "c"
'
