#!/usr/bin/env bash

source test/truffle/common.sh.inc

platform="$(uname -s)-$(uname -m)"

if [[ "$platform" == "Linux-aarch64" ]]; then
  echo '[GR-40810] Skipping test on linux-aarch64 as it fails'
  exit 0
fi

gem_test_pack=$(jt gem-test-pack)

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd "$truffle/cexts/grpc" || exit 1

bundle config --local cache_path "$gem_test_pack/gem-cache"
bundle install --local --no-cache

output=$(bundle exec ruby -rzlib -ropenssl -e 'require "grpc"; p GRPC')

if [ "$output" = 'GRPC' ]; then
  echo Success
else
  echo Unexpected output
  echo "$output"
  exit 1
fi
