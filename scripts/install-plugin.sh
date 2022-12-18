#!/usr/bin/env bash

set -e

VERSION="0.1.5"

case "$(uname -s)" in

   Darwin)
     echo '== Installing plugin for Mac OSX =='
     mkdir -p ~/.pact/plugins/avro-${VERSION}
     wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-plugin.json -O ~/.pact/plugins/avro-${VERSION}/pact-plugin.json
     if [ "$(uname -m)" == "arm64" ]; then
        wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-avro-plugin-osx-aarch64.gz -O ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.gz
     else
        wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-avro-plugin-osx-x86_64.gz -O ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.gz
     fi
     gunzip -N -f ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.gz
     chmod +x ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin
     ;;

   Linux)
     echo '== Installing plugin for Linux =='
     mkdir -p ~/.pact/plugins/avro-${VERSION}
     wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-plugin.json -O ~/.pact/plugins/avro-${VERSION}/pact-plugin.json
     wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-avro-plugin-linux-x86_64.gz -O ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.gz
     gunzip -N -f ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.gz
     chmod +x ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin
     ;;

   CYGWIN*|MINGW32*|MSYS*|MINGW*)
     echo '== Installing plugin for MS Windows =='
     mkdir -p ~/.pact/plugins/avro-${VERSION}
     wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-plugin.json -O ~/.pact/plugins/avro-${VERSION}/pact-plugin.json
     wget https://github.com/austek/pact-avro-plugin/releases/download/v-${VERSION}/pact-avro-plugin-windows-x86_64.exe.gz -O ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.exe.gz
     gunzip -N -f ~/.pact/plugins/avro-${VERSION}/pact-avro-plugin.exe.gz
     ;;

   *)
     echo "ERROR: $(uname -s) is not a supported operating system"
     exit 1
     ;;
esac
