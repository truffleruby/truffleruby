#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt gem install unf_ext:0.0.7.4 -V -N --backtrace

jt ruby test/truffle/cexts/unf_ext/test.rb
