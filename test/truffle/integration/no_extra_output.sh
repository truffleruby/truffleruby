#!/usr/bin/env bash

source test/truffle/common.sh.inc

# Disable fast-fail to get more useful output
set +e
# Disable debug output by Bash as it creates extra output in the output file
set +x

function check() {
  if [ "$1" -ne 0 ]; then
    echo Command failed with "$1"
    echo Output:
    cat temp.txt
    exit 1
  fi

  if ! cmp --silent temp.txt test/truffle/integration/no_extra_output/twelve.txt
  then
    echo Extra output
    cat temp.txt
    exit 1
  else
    rm -f temp.txt
  fi
}

echo "Basic test of the output"

$RUBY_BIN -w -e 'puts 3*4' 1>temp.txt 2>&1
check $?

echo "Basic test of the output with lazy options disabled"

$RUBY_BIN -w --experimental-options --lazy-default=false -e 'puts 3*4' 1>temp.txt 2>&1
check $?

echo "Test loading many standard libraries"

$RUBY_BIN -w --experimental-options --lazy-default=false test/truffle/integration/no_extra_output/all_stdlibs.rb 1>temp.txt 2>&1
check $?

echo "Test of the unexpected output to stderr."
echo "To ensure there are no unexpected warnings for instance."

jt --silent test specs fast --error-output stderr 2>stderr.txt
if [ -s stderr.txt ]; then
  echo Extra output:
  cat stderr.txt
  exit 1
else
  echo No extra output
  rm -f stderr.txt
fi
