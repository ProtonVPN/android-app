#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Error: No artifact path provided."
  exit 1
fi

ARTIFACT_PATH=$1
EXPECTED_SHA256="dcc9439ec1a6c6a8d0203f3423ee42bcc8b970628e53cb73a0393f398dd5b853"

apksigner verify --print-certs $ARTIFACT_PATH > output.txt
SHA256_VALUE=$(grep 'Signer #1 certificate SHA-256 digest' output.txt | awk '{print $NF}')
echo "SHA-256 value of artifact: $SHA256_VALUE"
echo "SHA-256 value expected: $EXPECTED_SHA256"

if [ "$SHA256_VALUE" != "$EXPECTED_SHA256" ]; then
  echo "Error: SHA-256 value does not match the expected value. The apk was not signed with production key."
  exit 1
else
  echo "SHA-256 value matches."
fi