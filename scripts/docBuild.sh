#!/bin/bash

if [ $# -lt 1 ]
then
    echo "Usage : $0 <version tag>"
    exit
fi

set -ex

VERSION=$(echo "$1" | cut -d/ -f3 | sed 's/v//')

echo Building docs for "$VERSION"
sed -i 's/version: snapshot/version: '"${VERSION}"'/' docs/antora.yml
npm run build
git restore docs/antora.yml
