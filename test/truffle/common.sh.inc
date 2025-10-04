#!/usr/bin/env bash

SELF_PATH=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/$(basename "${BASH_SOURCE[0]}")
while [ -h "$SELF_PATH" ]; do
  DIR=$(dirname "$SELF_PATH")
  SYM=$(readlink "$SELF_PATH")
  SELF_PATH=$(cd "$DIR" && cd "$(dirname "$SYM")" && pwd)/$(basename "$SYM")
done
truffle=$(dirname "$SELF_PATH")
test=$(dirname "$truffle")
repo=$(dirname "$test")

set -e
set -x

# The server should be started on host 127.0.0.1 with port 0 so the OS chooses
# an available port, and this function will find which port was opened.
function test_server {
  serverpid=$!
  echo "Server PID: $serverpid"

  ports=$(jt find_ports_for_pid "$serverpid")

  if [[ $(echo "$ports" | wc -l) -gt 1 ]]; then
    echo "More than one port opened!" >&2
    echo "$ports" >&2
    kill -9 "$serverpid"
    exit 1
  else
    echo "Found port: $ports"
  fi

  local cmd="curl -s http://localhost:$ports/"
  local response=""
  while ! response=$($cmd) || [[ -z "$response" ]]; do
    sleep 1
  done

  jobs -l
  kill "$serverpid"

  status=0
  wait "$serverpid" || status=$?
  if [[ "$status" == "134" ]]; then
    echo "Server aborted! $status"
    exit 1
  elif [[ "$status" == "143" ]]; then
    echo "Server terminated with $status (SIGTERM)"
  elif [[ "$status" == "0" ]]; then
    echo "Server terminated with $status"
  else
    echo "Unknown server exit status: $status"
    exit 1
  fi

  if [[ $response != *"Hello"* ]]
  then
    echo "Response not expected:"
    echo "$response"
    exit 1
  fi
}

shopt -s expand_aliases
# shellcheck disable=SC2139
alias jt="$repo/bin/jt"
