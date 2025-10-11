#!/usr/bin/env bash
# Hook run after extraction in the installation directory by a Ruby installer.
# Useful to perform tasks that depend on the user machine and
# cannot be generally done in advance on another machine.

set -e

lib_truffle=$(cd "$(dirname "$0")" && pwd -P)
root=$(dirname "$(dirname "$lib_truffle")")

# In case the script shells out to ruby, make sure to use truffleruby and not rely on system ruby
export PATH="$root/bin:$PATH"
# Make sure RubyGems is not used while running this file, since it is not needed
export TRUFFLERUBYOPT="--disable-gems $TRUFFLERUBYOPT"

cd "$root"

function recompile() {
  ext="$1"
  cd "src/main/c/$ext"
  truffleruby -w extconf.rb
  if [ -z "$CORES" ]; then
    CORES=$(getconf _NPROCESSORS_ONLN || echo 1)
  fi
  make "--jobs=$CORES"
  cp "$ext.$(truffleruby -rrbconfig -e "print RbConfig::CONFIG['DLEXT']")" "$root/lib/mri"
  cd "$root"
  echo
}

if [ "$TRUFFLERUBY_RECOMPILE_OPENSSL" == "false" ]; then
  echo "Skipping recompilation of the OpenSSL C extension (TRUFFLERUBY_RECOMPILE_OPENSSL=false)"
elif [ "$TRUFFLERUBY_RECOMPILE_OPENSSL" == "true" ]; then
  echo "Recompiling the OpenSSL C extension (TRUFFLERUBY_RECOMPILE_OPENSSL=true)"
  recompile openssl
else
  echo "Recompiling the OpenSSL C extension (against the installed libssl)"
  recompile openssl
fi

echo "TruffleRuby was successfully installed in $root"
