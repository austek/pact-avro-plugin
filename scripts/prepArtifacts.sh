#!/bin/bash
set -e

echo Prepare release artifacts

PLUGIN_TARGET=modules/plugin/target
ART_DIR=target/artifacts
ART_NAME=pact-avro-plugin.zip

mkdir -p ${ART_DIR}

cp ${PLUGIN_TARGET}/universal/"${ART_NAME}" ${ART_DIR}/
openssl dgst -sha256 -r ${ART_DIR}/"${ART_NAME}" > ${ART_DIR}/"${ART_NAME}".sha256

cp ${PLUGIN_TARGET}/artifacts/* ${ART_DIR}/

openssl dgst -sha256 -r ${ART_DIR}/install-plugin.sh > ${ART_DIR}/install-plugin.sh.sha256
