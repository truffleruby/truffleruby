#!/usr/bin/env bash

# Generate a Gemfile.lock with all default and bundled gems. This way they are
# automatically checked by GitHub for vulnerabilities that reminds us to update the
# doc/user/known-cves.md document. Also it still seems valuable to check that
# default + bundled gems are recognized by Bundler.

source test/truffle/common.sh.inc

cd "$truffle/gems/default-bundled-gems" || exit 1

jt ruby gems2gemfile.rb

cat Gemfile

# This test only works with no bundle path set
bundle config set --local path.system true

jt ruby -S bundle install --local

echo 'Check if Gemfile is up to date'
git diff --exit-code .
