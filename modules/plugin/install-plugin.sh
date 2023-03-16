#!/usr/bin/env bash

set -e

VERSION="VERSION_HERE"

case "$(uname -s)" in

   Darwin|Linux|CYGWIN*|MINGW32*|MSYS*|MINGW*)
     echo '== Installing plugin =='
     mkdir -p ~/.pact/plugins/avro-${VERSION}
     wget -c https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-avro-plugin-${VERSION}.tgz \
     -O - | tar -xz -C ~/.pact/plugins/avro-${VERSION} --strip-components 1
     ;;

   *)
     echo "ERROR: $(uname -s) is not a supported operating system"
     exit 1
     ;;
esac
