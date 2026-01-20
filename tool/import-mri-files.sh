#!/usr/bin/env bash

# This file should pass `shellcheck tool/import-mri-files.sh`.

set -x
set -e

topdir=$(cd ../ruby && pwd -P)

if [ -z "$RUBY_SOURCE_DIR" ]; then
  if [ -n "$VERSION" ]; then
    RUBY_SOURCE_DIR=../ruby-$VERSION
  else
    RUBY_SOURCE_DIR=../ruby
  fi
fi

if [ -z "$RUBY_BUILD_DIR" ]; then
  tag=$(cd "$topdir" && git describe --tags)
  version=$(echo "$tag" | tr -d v | tr '_' '.')
  RUBY_BUILD_DIR=$HOME/src/ruby-$version
fi

if [ ! -d "$RUBY_BUILD_DIR" ]; then
  echo "$RUBY_BUILD_DIR does not exist!"
  exit 1
fi

# Generate ext/rbconfig/sizeof/sizes.c and limits.c
(
  cd "$RUBY_SOURCE_DIR/ext/rbconfig/sizeof"
  cp depend Makefile
  make sizes.c limits.c RUBY=ruby top_srcdir="$topdir"
  rm Makefile
)

# lib/
rm -r lib/mri
cp -R "$RUBY_SOURCE_DIR/lib" lib/mri
# CRuby-specific
rm -r lib/mri/ruby_vm
# We have our own version under lib/truffle
rm lib/mri/timeout.rb
rm lib/mri/weakref.rb
# Files not actually installed in MRI
find lib/mri -name '*.gemspec' -delete
find lib/mri -name '.document' -delete

# *.c
cp "$RUBY_SOURCE_DIR/st.c" src/main/c/cext
cp "$RUBY_SOURCE_DIR/missing/strlcpy.c" src/main/c/cext

# Copy Ruby files in ext/, sorted alphabetically
mkdir lib/mri/digest
cp "$RUBY_SOURCE_DIR/ext/date/lib/date.rb" lib/mri
cp -R "$RUBY_SOURCE_DIR/ext/digest/sha2/lib"/* lib/mri/digest
cp -R "$RUBY_SOURCE_DIR/ext/fiddle/lib/fiddle" lib/mri
cp -R "$RUBY_SOURCE_DIR/ext/fiddle/lib/fiddle.rb" lib/mri
cp "$RUBY_SOURCE_DIR/ext/monitor/lib"/*.rb lib/mri
mkdir lib/mri/objspace
cp -R "$RUBY_SOURCE_DIR/ext/objspace/lib/objspace" lib/mri
cp "$RUBY_SOURCE_DIR/ext/objspace/lib/objspace.rb" lib/mri
cp -R "$RUBY_SOURCE_DIR/ext/openssl/lib"/* lib/mri
cp "$RUBY_SOURCE_DIR/ext/pty/lib"/*.rb lib/mri
cp "$RUBY_SOURCE_DIR/ext/psych/lib/psych.rb" lib/mri
cp -R "$RUBY_SOURCE_DIR/ext/psych/lib/psych" lib/mri
cp "$RUBY_SOURCE_DIR/ext/socket/lib/socket.rb" lib/truffle/socket/mri.rb

# Copy C extensions in ext/, sorted alphabetically
rm -r src/main/c/{date,etc,io-console,openssl,psych,rbconfig-sizeof,zlib}
mkdir src/main/c/{date,etc,io-console,openssl,psych,rbconfig-sizeof,zlib}
cp "$RUBY_SOURCE_DIR/ext/date"/*.{c,gemspec,h,rb} src/main/c/date
cp "$RUBY_SOURCE_DIR/ext/etc"/*.{c,rb} src/main/c/etc
cp "$RUBY_SOURCE_DIR/ext/io/console"/*.{c,rb} src/main/c/io-console
cp "$RUBY_SOURCE_DIR/ext/openssl"/*.{c,h,rb} src/main/c/openssl
cp "$RUBY_SOURCE_DIR/ext/psych"/*.{c,h,rb} src/main/c/psych
cp "$RUBY_SOURCE_DIR/ext/rbconfig/sizeof"/*.{c,rb} src/main/c/rbconfig-sizeof
cp "$RUBY_SOURCE_DIR/ext/zlib"/*.{c,rb} src/main/c/zlib

# test/
rm -rf test/mri/tests
cp -R "$RUBY_SOURCE_DIR/test" test/mri/tests
rm -rf test/mri/tests/excludes
cp -R "$RUBY_SOURCE_DIR/ext/-test-" test/mri/tests
mkdir test/mri/tests/cext
mv test/mri/tests/-ext- test/mri/tests/cext-ruby
mv test/mri/tests/-test- test/mri/tests/cext-c
find test/mri/tests/cext-ruby -name '*.rb' -print0 | xargs -0 -n 1 sed -i.backup 's/-test-/c/g'
find test/mri/tests/cext-ruby -name '*.backup' -delete
rm -rf test/mri/excludes
git checkout -- test/mri/excludes

# Prism is updated separately by tool/import-prism.sh
git checkout -- lib/mri/prism.rb
rm -rf lib/mri/prism
git checkout -- lib/mri/prism
rm -rf test/mri/tests/prism
git checkout -- test/mri/tests/prism

# Copy from tool/lib to tests/lib
cp -R "$RUBY_SOURCE_DIR/tool/lib"/* test/mri/tests/lib
rm -f test/mri/tests/lib/leakchecker.rb

# Copy from tool/test to tests/tool
rm -rf test/mri/tests/tool
mkdir -p test/mri/tests/tool/test
cp -R "$RUBY_SOURCE_DIR/tool/test/runner.rb" test/mri/tests/tool/test
cp -R "$RUBY_SOURCE_DIR/tool/test/init.rb" test/mri/tests/tool/test

# basictest/ and bootstraptest/
rm -rf test/basictest
cp -R "$RUBY_SOURCE_DIR/basictest" test/basictest
rm -rf test/bootstraptest
cp -R "$RUBY_SOURCE_DIR/bootstraptest" test/bootstraptest
# Do not import huge yjit_30k test files
rm -f test/bootstraptest/test_yjit*

# Licences
cp "$RUBY_SOURCE_DIR/BSDL" doc/legal/ruby-bsdl.txt
cp "$RUBY_SOURCE_DIR/COPYING" doc/legal/ruby-licence.txt
cp "$RUBY_SOURCE_DIR/gems/bundled_gems" doc/legal/bundled_gems

# include/
rm -rf lib/cext/include/ruby
git checkout lib/cext/include/ruby/config.h
cp -R "$RUBY_SOURCE_DIR/include/." lib/cext/include
cp -R "$RUBY_SOURCE_DIR/ext/digest/digest.h" lib/cext/include/ruby

# Keep in sync with the list in lib/cext/include/internal_all.h
internal_headers=({bits,compilers,st,static_assert}.h)
rm -f "${internal_headers[@]/#/lib/cext/include/internal/}"
cp -R "${internal_headers[@]/#/"$RUBY_SOURCE_DIR/internal/"}" lib/cext/include/internal

cp "$RUBY_BUILD_DIR"/id.h spec/truffle/capi/ext/internal_id.h

rm -f lib/cext/include/ruby_assert.h && cp "$RUBY_SOURCE_DIR/ruby_assert.h" lib/cext/include/ruby_assert.h

# defs/
cp "$RUBY_SOURCE_DIR/defs/known_errors.def" tool
cp "$RUBY_SOURCE_DIR/defs/id.def" tool
