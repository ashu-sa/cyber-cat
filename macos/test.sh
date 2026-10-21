#!/bin/zsh
set -euo pipefail
cd "${0:A:h}"
TEST_BIN=$(mktemp -t mochi-tests)
trap 'rm -f "$TEST_BIN"' EXIT
swiftc -parse-as-library Sources/MochiStopwatch/Model.swift Sources/MochiStopwatch/Cloud.swift Tests/MochiTests/StopwatchTests.swift -o "$TEST_BIN"
"$TEST_BIN"
