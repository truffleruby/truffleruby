#!/usr/bin/env bash

source test/truffle/common.sh.inc

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd spec/ffi || exit 1

export BUNDLE_PATH=vendor/bundle
bundle install
bundle exec rspec --format doc --exclude-pattern 'vendor/**/*' .
