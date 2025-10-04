#!/usr/bin/env bash

source test/truffle/common.sh.inc

platform="$(uname -s)-$(uname -m)"

if [[ "$platform" == "Linux-aarch64" ]]; then
  echo '[GR-40810] Skipping test on linux-aarch64 as it fails'
  exit 0
fi

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd "$truffle/cexts/grpc" || exit 1

export BUNDLE_PATH=vendor/bundle
bundle install

output=$(bundle exec ruby -rzlib -ropenssl -e 'require "grpc"; p GRPC')

if [ "$output" = 'GRPC' ]; then
  echo Success
else
  echo Unexpected output
  echo "$output"
  exit 1
fi
