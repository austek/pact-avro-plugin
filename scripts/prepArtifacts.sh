#!/bin/bash
set -e

echo Generates checksums

ART_DIR=target/artifacts/

openssl dgst -sha256 -r ${ART_DIR}/pact-avro-plugin.zip > ${ART_DIR}/pact-avro-plugin.zip.sha256
openssl dgst -sha256 -r ${ART_DIR}/install-plugin.sh > ${ART_DIR}/install-plugin.sh.sha256
