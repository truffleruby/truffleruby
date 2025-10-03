#!/usr/bin/env bash

# Set this up before each commit with
#   cp tool/hooks/lint-check.sh .git/hooks/pre-commit
# or after each commit with
#   cp tool/hooks/lint-check.sh .git/hooks/post-commit
# or before each push with
#   cp tool/hooks/lint-check.sh .git/hooks/pre-push
# The choice is yours.

# Unset variable set by git which confuses git commands on other repositories
unset GIT_DIR

filename=$(basename "${BASH_SOURCE[0]}")

case "$filename" in
  pre-commit)   exec bin/jt lint fast HEAD ;;
  post-commit)  exec bin/jt lint fast HEAD^ ;;
  pre-push)     exec bin/jt lint fast origin/master ;;
esac
