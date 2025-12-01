#!/usr/bin/env bash

source test/truffle/common.sh.inc

# Ensure ruby_maven_deploy_public keeps working

jt mx ruby_maven_deploy_public
maven_repo=$(jt -q mx --quiet --no-warning ruby_maven_deploy_public_repo_dir)

if [ ! -d "$maven_repo" ]; then
    echo "Maven repo not at $maven_repo ?"
    exit 2
fi
