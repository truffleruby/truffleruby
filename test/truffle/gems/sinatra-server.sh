#!/usr/bin/env bash

source test/truffle/common.sh.inc

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd test/truffle/gems/sinatra-server || exit 1

export BUNDLE_PATH=vendor/bundle
bundle install
ruby -rbundler/setup sinatra-server.rb & test_server
