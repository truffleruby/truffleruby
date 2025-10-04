#!/usr/bin/env bash

source test/truffle/common.sh.inc

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd test/truffle/ecosystem/discourse || exit 2

echo 'Disabled due to https://github.com/rubygems/rubygems/issues/6165'
export BUNDLE_PATH=vendor/bundle
bundle lock
bundle install
