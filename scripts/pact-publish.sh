#!/bin/bash

#This is an example of how you might publish pacts to a remote broker using the Pact Foundation's pact-cli

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo "Running pact-publish script at $SCRIPT_DIR"

PACT_DIR=$(realpath modules/examples/consumer/target/pacts)
LATEST_COMMIT=$(git rev-parse --short HEAD)

echo Found the following pact files:
for file in ${PACT_DIR}/*; do
    echo "$(basename "$file")"
done

docker run --rm \
  -v ${PACT_DIR}:/pacts \
  --network="host" \
  pactfoundation/pact-cli \
  publish /pacts \
  --broker-base-url ${PACT_BROKER_BASE_URL} \
  --broker-username ${PACT_BROKER_USERNAME} \
  --broker-password ${PACT_BROKER_PASSWORD} \
  --consumer-app-version=${LATEST_COMMIT} \
  --tag=${LATEST_COMMIT} \
  --branch=main
