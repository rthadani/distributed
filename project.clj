(defproject distributed "0.1.0-SNAPSHOT"
  :description "Raft consensus implementation with gRPC"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :java-source-paths ["gen"]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "1.2.603"]
                 [io.grpc/grpc-netty-shaded "1.59.1"]
                 [io.grpc/grpc-protobuf "1.59.1"]
                 [io.grpc/grpc-stub "1.59.1"]
                 [com.google.protobuf/protobuf-java "3.24.0"]
                 [javax.annotation/javax.annotation-api "1.3.2"]
                 [clj-message-digest "1.0.0"]
                 [org.clojure/data.priority-map "1.0.0"]])
