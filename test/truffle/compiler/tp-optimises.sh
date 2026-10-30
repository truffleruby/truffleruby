#!/usr/bin/env bash

source test/truffle/common.sh.inc

# Only the Oracle GraalVM compiler constant-folds a local variable written through the Binding of a trace event,
# since this needs to alias the write through the Binding's frame with the read of the frame in the traced method.
ruby_version=$(jt ruby -v)
if [[ ! $ruby_version =~ "Oracle GraalVM" ]]; then
  echo Oracle GraalVM not detected, skipping test
  exit 0
fi

export TRUFFLERUBY_ALLOW_PRIVATE_PRIMITIVES_IN="$truffle/compiler/"
jt ruby --check-compilation --experimental-options --engine.MultiTier=false test/truffle/compiler/tp-optimises/tp-optimises.rb
