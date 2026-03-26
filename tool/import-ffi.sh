#!/usr/bin/env bash

set -x
set -e

repo="../../ffi"

cp "$repo/LICENSE" doc/legal/ffi.txt

# lib/
rm -rf lib/truffle/ffi
cp -R "$repo/lib/ffi" lib/truffle

# Keep the empty pointer.rb file under lib/, these methods are already defined in core
# and they need to be in core as there are usages in core.
git checkout lib/truffle/ffi/pointer.rb
cp "$repo/lib/ffi/pointer.rb" src/main/ruby/truffleruby/core/truffle/ffi/pointer_extra.rb

# Only keep files for the platforms TruffleRuby supports (see NativeConfiguration)
rm -rf lib/truffle/ffi/platform/*
cp -R "$repo"/lib/ffi/platform/{aarch64-darwin,x86_64-linux,aarch64-linux} lib/truffle/ffi/platform

# spec/
rm -rf spec/ffi
cp -R "$repo/spec/ffi" spec

# Keep the Gemfile files
git checkout spec/ffi/Gemfile spec/ffi/Gemfile.lock

# Remove unused files
rm -rf spec/ffi/embed-test

set +x
echo
echo "Run"
echo "$ git checkout -p src/main/ruby/truffleruby/core/truffle/ffi/pointer_extra.rb"
echo "To restore the changes at the top of the file"
