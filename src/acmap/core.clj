(ns acmap.core
  (:gen-class)
  (:require [acmap.util :as util]
            [clojure.string :as cs])
  (:import org.apache.commons.io.IOUtils
           java.io.BufferedReader
           java.io.IOException
           java.io.InputStreamReader))


(defn transform-http-msg
  "Take headers and body of a HTTP message and reverse each line of its body."
  [headers body]
  {:headers headers
   :body (-> apply
             (partial str)
             (comp reverse)
             (map body))})


(defn -main
  [& args]
  (let [br (BufferedReader. (InputStreamReader. System/in))]
    (try
      (loop [hex-line (.readLine br)]
        (let [decoded-req (util/decode-hex-string hex-line)
              http-request (partition-by empty? (cs/split-lines decoded-req))
              headers (first http-request)
              body (when (= 3 (count http-request)) (last http-request))]
          (-> (transform-http-msg headers body)
              util/format-http-msg
              util/encode-to-hex-string
              println))
        (when-let [line (.readLine br)]
          (recur line)))
      (catch IOException e (IOUtils/closeQuietly br)))))
