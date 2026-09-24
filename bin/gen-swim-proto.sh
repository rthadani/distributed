#!/usr/bin/env bash
# Regenerate the gRPC/protobuf Java stubs for swim.proto into gen/.
set -euo pipefail

PROTOC="${PROTOC:-/tmp/swim-tools/protoc}"
PLUGIN="${PLUGIN:-/tmp/swim-tools/protoc-gen-grpc-java}"

cd "$(dirname "$0")/.."

mkdir -p gen
"$PROTOC" \
  -I resources/proto \
  --java_out=gen \
  --plugin=protoc-gen-grpc-java="$PLUGIN" \
  --grpc-java_out=gen \
  resources/proto/swim.proto

echo "Generated gen/swim/SwimGrpc.java and gen/swim/SwimSpec.java"
