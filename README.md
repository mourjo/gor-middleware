# Gor Middleware

A simple Clojure project which can be used to write a middleware for Gor.

## Usage

* Git clone this repository.
* Rewrite fn `gor-middleware.core/transform-http-msg`.
* Make a JAR: `lein uberjar`
* Create a shell file which runs the jar `java -jar gor-middleware-0.2.0-SNAPSHOT.jar`
* Run Gor with your middleware: `sudo ~/gor --verbose --input-raw :9202 --middleware ../middleware.sh --output-http-stats --output-http http://1.2.3.4:8888`
