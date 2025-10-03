#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt gem install RubyInline:3.12.4

jt ruby test/truffle/cexts/RubyInline/ruby_inline_fact.rb
