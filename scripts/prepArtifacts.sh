#!/bin/bash

if [ $# -lt 1 ]
then
    echo "Usage : $0 <version tag>"
    exit
fi

set -ex

VERSION=$(echo "$1" | cut -d/ -f3 | sed 's/v//')
echo Building Release for "$VERSION"

ART_DIR=target/artifacts/
ART_NAME=pact-avro-plugin-"${VERSION}".tgz

mkdir -p ${ART_DIR}

mv modules/plugin/target/universal/"${ART_NAME}" ${ART_DIR}
openssl dgst -sha256 -r ${ART_DIR}/"${ART_NAME}" > ${ART_DIR}/"${ART_NAME}".sha256

sed -e 's/VERSION_HERE/'"${VERSION}"'/' modules/plugin/install-plugin.sh > ${ART_DIR}/install-plugin.sh
openssl dgst -sha256 -r ${ART_DIR}/install-plugin.sh > ${ART_DIR}/install-plugin.sh.sha256
