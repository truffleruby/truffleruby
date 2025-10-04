#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt gem install json:2.2.0 -V -N --backtrace

output=$(jt --silent ruby -e 'gem "json", "2.2.0"; require "json"; puts JSON.dump({ a: 1 })')

if [ "$output" = '{"a":1}' ]; then
  echo Success
else
  echo Unexpected output
  echo "$output"
  exit 1
fi
