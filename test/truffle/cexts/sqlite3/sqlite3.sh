#!/usr/bin/env bash

source test/truffle/common.sh.inc

# The sqlite3 extconf.rb needs pkg-config
command -v pkg-config
pkg-config --version

jt gem install mini_portile2:2.8.0 -V -N --backtrace
jt gem install sqlite3:1.6.3 -V -N --backtrace

jt ruby test/truffle/cexts/sqlite3/test.rb
