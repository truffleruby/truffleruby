#!/bin/bash

set -e
set -x

VERSION=$(cat .ruby-version)

url="https://cache.ruby-lang.org/pub/ruby/${VERSION%.*}/ruby-$VERSION.tar.gz"

os=$(uname -s)
os=${os/Linux/linux}
os=${os/Darwin/darwin}

arch=$(uname -m)
arch=${arch/x86_64/amd64}
arch=${arch/arm64/aarch64}

mx_platform="${os}_${arch}"

archive=$(basename "$url")

if [ ! -e "$archive" ]; then
    curl -LO "$url"
fi

if [ ! -d "ruby-$VERSION" ]; then
    tar xf "$archive"
fi

cd "ruby-$VERSION" || exit 1
# Disable GMP as it might not be available on the runtime machine and we do not use it
./configure --without-gmp || (cat config.log; exit 1)

cp .ext/include/*/ruby/config.h "../lib/cext/include/truffleruby/config_${mx_platform}.h"
