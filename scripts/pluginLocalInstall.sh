#!/bin/bash
set -e

ART_DIR=target/artifacts
VERSION=99.9.9

case "$(uname -s)" in

   Darwin|Linux|CYGWIN*|MINGW32*|MSYS*|MINGW*)
     echo '== Installing plugin =='
     mkdir -p ~/.pact/plugins/avro-${VERSION}
     cp $ART_DIR/pact-plugin.json ~/.pact/plugins/avro-${VERSION}/pact-plugin.json
     tar -xzvf $ART_DIR/pact-avro-plugin.tgz -C ~/.pact/plugins/avro-${VERSION}
     ;;

   *)
     echo "ERROR: $(uname -s) is not a supported operating system"
     exit 1
     ;;
esac
