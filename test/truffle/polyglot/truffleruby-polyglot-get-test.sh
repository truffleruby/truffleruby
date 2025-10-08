#!/usr/bin/env bash

source test/truffle/common.sh.inc

# Clone Graal.js repository
jt mx --dy /vm,/graal-js sversions

# Running that command from /vm as primary suite is needed
# to deploy org.graalvm.polyglot:js-community and not just org.graalvm.js:js-community (GR-52171)
pushd ../graal/vm
jt mx --dy truffleruby,/graal-js ruby_maven_deploy_public
maven_repo=$(jt -q mx --quiet --no-warning --dy truffleruby,/graal-js ruby_maven_deploy_public_repo_dir)
popd

if [ ! -d "$maven_repo" ]; then
    echo "Maven repo not at $maven_repo ?"
    exit 2
fi

jt build --env jvm-ce

standalone=$(jt -u jvm-ce ruby-home)

export PATH="$standalone/bin:$PATH"

env org.graalvm.maven.downloader.repository="file://$maven_repo" truffleruby-polyglot-get js-community

out=$(ruby -e 'p Polyglot.eval("js", "1/2")')
if [ "$out" != "0.5" ]; then
    echo "Wrong output: >>$out<<"
    exit 1
fi
