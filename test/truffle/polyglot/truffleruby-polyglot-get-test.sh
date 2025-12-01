#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt build --env jvm-ce

standalone=$(jt -u jvm-ce ruby-home)

export PATH="$standalone/bin:$PATH"

truffleruby-polyglot-get js

out=$(ruby -e 'p Polyglot.eval("js", "1/2")')
if [ "$out" != "0.5" ]; then
    echo "Wrong output: >>$out<<"
    exit 1
fi
