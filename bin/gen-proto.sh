#!/usr/bin/env bash
# Regenerate the gRPC/protobuf Java stubs for raft.proto into gen/.
set -euo pipefail

PROTOC="${PROTOC:-/tmp/raft-tools/bin/protoc}"
PLUGIN="${PLUGIN:-/tmp/raft-tools/grpc-plugin.exe}"

cd "$(dirname "$0")/.."

mkdir -p gen
"$PROTOC" \
  -I resources/proto \
  --java_out=gen \
  --plugin=protoc-gen-grpc-java="$PLUGIN" \
  --grpc-java_out=gen \
  resources/proto/raft.proto

echo "Generated gen/raft/RaftGrpc.java and gen/raft/RaftSpec.java"
