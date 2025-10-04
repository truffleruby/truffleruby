#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt gem install puma:3.10.0 -V -N --backtrace
jt gem install rack:1.6.1 -V -N --backtrace

jt ruby -S \
  puma --bind "tcp://127.0.0.1:0" test/truffle/cexts/puma/app.ru & test_server
