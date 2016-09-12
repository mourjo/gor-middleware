(ns acmap.util
  (:require [clojure.string :as cs])
  (:import org.apache.commons.codec.binary.Hex))


(defn get-content-length
  [s]
  (alength (.getBytes s)))


(defn decode-hex-string
  [s]
  (String. (Hex/decodeHex (.toCharArray s))))


(defn encode-to-hex-string
  [^String s]
  (String. (Hex/encodeHex (.getBytes s))))


(defn format-http-msg
  [{:keys [headers body]}]
  (-> "\n"
      (cs/join headers)
      (str (when body (str "\n\n" (cs/join "\n" body))))))
