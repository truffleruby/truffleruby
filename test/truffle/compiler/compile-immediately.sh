#!/usr/bin/env bash

source test/truffle/common.sh.inc

code="puts 'hello'"

# Test both without and with BackgroundCompilation, it catches different issues.

# Use --compiler.DeoptCycleDetectionThreshold=32 since the deopt loop detection seems more prone to false positives with CompileImmediately,
# see https://github.com/oracle/graal/issues/12286 and Slack discussion https://graalvm.slack.com/archives/CNQSB2DHD/p1759493884063259.

jt ruby --engine.UsePreInitializedContext=false --check-compilation --experimental-options --engine.CompileImmediately --compiler.DeoptCycleDetectionThreshold=32 --engine.BackgroundCompilation=false --trace -e "$code"

jt ruby --engine.UsePreInitializedContext=false --check-compilation --experimental-options --engine.CompileImmediately --compiler.DeoptCycleDetectionThreshold=32 --trace -e "$code"
