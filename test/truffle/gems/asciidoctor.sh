#!/usr/bin/env bash

source test/truffle/common.sh.inc

ruby_home="$(jt ruby-home)"
export PATH="$ruby_home/bin:$PATH"

cd test/truffle/gems/asciidoctor || exit 1

export BUNDLE_PATH=vendor/bundle
bundle install
bundle exec asciidoctor --attribute last-update-label!= userguide.adoc

if ! cmp --silent userguide.html userguide-expected.html
then
  echo Asciidoctor output was not as expected
  diff -u userguide-expected.html userguide.html
  rm -f userguide.html
  exit 1
else
  rm -f userguide.html
fi
