#!/usr/bin/env bash
# Hook run after extraction in the installation directory by a Ruby installer.
# Useful to perform tasks that depend on the user machine and
# cannot be generally done in advance on another machine.

set -e

lib_truffle=$(cd "$(dirname "$0")" && pwd -P)
root=$(dirname "$(dirname "$lib_truffle")")

echo "TruffleRuby was successfully installed in $root"
