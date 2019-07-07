(defproject distributed "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :java-source-paths ["gen"]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.4.490"]
                 [ruiyun/tools.timer "1.0.1"]
                 [clojusc/protobuf "3.5.1-v1.1"]
                 [io.grpc/grpc-netty "1.1.2"]
                 [io.grpc/grpc-protobuf "1.1.2"]
                 [io.grpc/grpc-stub "1.1.2"]
                 [clj-message-digest "1.0.0"]])
