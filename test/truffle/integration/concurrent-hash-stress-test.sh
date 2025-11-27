#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt test fast :all -- --experimental-options --shared-objects-force --concurrent-hash-always
