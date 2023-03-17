#!/bin/bash

if [ $# -lt 1 ]
then
    echo "Usage : $0 <version tag>"
    exit
fi

set -e

echo Building Release for "$1"
NEXT=$(echo "$2" | cut -d\- -f2)
ART_DIR=modules/plugin/target/artifacts
ART_NAME=pact-avro-plugin-"${NEXT}".tgz

sbt clean
mkdir -p ${ART_DIR}

mv modules/plugin/target/universal/"${ART_NAME}" ${ART_DIR}
openssl dgst -sha256 -r ${ART_DIR}/"${ART_NAME}" > target/artifacts/"${ART_NAME}".sha256

sed -e 's/VERSION_HERE/'"${NEXT}"'/' modules/plugin/install-plugin.sh > ${ART_DIR}/install-plugin.sh
openssl dgst -sha256 -r ${ART_DIR}/install-plugin.sh > ${ART_DIR}/install-plugin.sh.sha256
