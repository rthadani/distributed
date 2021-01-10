#!/bin/sh
mkdir -p gen/java
protoc \
  -I=/usr/include \
  -I=/usr/local/include \
  -I=resources/proto \
  resources/proto/*.proto \
  --java_out=gen/java
