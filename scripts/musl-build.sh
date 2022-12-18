#!/usr/bin/env sh

set -e

cargo build
./target/debug/pact-avro-plugin -v
