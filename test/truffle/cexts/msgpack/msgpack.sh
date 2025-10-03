#!/usr/bin/env bash

source test/truffle/common.sh.inc

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd "test/truffle/cexts/msgpack" || exit 1

rm -rf msgpack-ruby
git clone --branch v1.2.6 git@github.com:msgpack/msgpack-ruby.git msgpack-ruby

cd msgpack-ruby || exit 1
export BUNDLE_PATH=vendor/bundle
bundle install
bundle exec rake compile
bundle exec rake spec

cd ..
rm -rf msgpack-ruby
