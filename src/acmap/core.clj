(ns acmap.core
  (:gen-class)
  (:require [cheshire.core :as cjson]
            [clojure.string :as cs]
            [clojure.tools.logging :as ctl]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import com.fasterxml.jackson.core.JsonParseException
           org.apache.commons.codec.binary.Hex
           org.apache.commons.io.IOUtils
           java.io.BufferedReader
           java.io.IOException
           java.io.FileReader
           java.io.InputStreamReader))

(def config
  {"network_type" {:type :ngram-agg, :tokenized? false},
   "meta.carrier" {:type :ngram-agg, :tokenized? false},
   "meta.device_model" {:type :ngram-agg, :tokenized? false},
   "assignee_name" {:type :ngram-agg, :tokenized? true},
   "app_version" {:type :completion-suggester, :tokenized? false},
   "meta.language" {:type :completion-suggester, :tokenized? false},
   "meta.os_version" {:type :completion-suggester, :tokenized? false},
   "tags" {:type :ngram-agg, :tokenized? false},
   "country_code" {:type :completion-suggester, :tokenized? false},
   "assignee_email" {:type :completion-suggester, :tokenized? false},
   "faq_publish_ids" {:type :ngram-agg, :tokenized? false},
   "author_name" {:type :ngram-agg, :tokenized? true},
   "cc" {:type :completion-suggester, :tokenized? false},
   "sdk_version" {:type :completion-suggester, :tokenized? false},
   "author_email" {:type :completion-suggester, :tokenized? false}})


(defn get-cfg
  []
  (reduce-kv
   (fn [curr k v]
     (assoc curr
            k
            (cond
              (= (:type v) :completion-suggester) :completion
              (and (:tokenized? v) (= (:type v) :ngram-agg)) :ngram-words
              (and (not (:tokenized? v)) (= (:type v) :ngram-agg)) :ngram-raw)))
   {}
   config))


(defn convert-ac-query
  "Convert an old autocompletion request to the new autocompletion request.
  1. Change the ES query based on the field.
  2. Change HTTP header to point to the correct URI."
  [req q]
  (let [cfg (get-cfg)
        domain (get-in q ["aggregations" "autocomplete" "filter" "term" "_domain"])
        fld (get-in q ["aggregations" "autocomplete" "aggs" "autocomplete" "terms" "field"])
        fld (if (.endsWith ^String fld ".raw") (subs fld 0 (- (count fld) 4)) fld)
        typed-by-user (or (get-in q ["query" "match_phrase_prefix" fld "query"]) "")
        ac-fld (str fld ".autocomplete")
        size 25]
    (case (cfg fld)
      nil nil
      :completion {:h (for [header req]
                        (if (cs/starts-with? header "POST")
                          "POST /moby-advsearch/issue/_suggest? HTTP/1.1"
                          header))
                   :q {:fld-suggestions
                       {:text typed-by-user
                        :completion {:context {:_domain domain}
                                     :field ac-fld
                                     :size size}}}}

      :ngram-words {:q {:query {:filtered {:query {:match {ac-fld typed-by-user}}
                                           :filter {:term {:_domain domain}}}}
                        :aggregations {:fld-suggestions
                                       {:terms
                                        {:field (str fld ".raw")
                                         :size size
                                         :order {:ranking "desc"}}
                                        :aggs {:ranking {:max {:script "_score"}}}}}}
                    :h (for [header req]
                         (if (cs/starts-with? header "POST")
                           "POST /moby-advsearch/issue/_search?search_type=count&query_cache=true HTTP/1.1"
                           header))}

      :ngram-raw {:q {:query {:filtered {:query {:match {ac-fld typed-by-user}}
                                         :filter {:term {:_domain domain}}}}
                      :aggregations {:fld-suggestions
                                     {:terms
                                      {:field (str fld ".raw")
                                       :size size
                                       :order {:ranking "desc"}}
                                      :aggs {:ranking {:max {:script "_score"}}}}}}
                  :h (for [header req]
                       (if (cs/starts-with? header "POST")
                         "POST /moby-advsearch/issue/_search?search_type=count&query_cache=true HTTP/1.1"
                         header))})))


(defn decode-hex-string
  [s]
  (String. (Hex/decodeHex (.toCharArray s))))


(defn encode-to-hex-string
  [^String s]
  (String. (Hex/encodeHex (.getBytes s))))


(defn -main
  [& args]
  (let [;;br (BufferedReader. (FileReader. "/Users/mourjo/tmp/sample_ac.txt"))
        br (BufferedReader. (InputStreamReader. System/in))
        req (atom [])]
    (spit "debug.log" "")
    (spit "test.log" "")
    (try
      (loop [hex-line (.readLine br)]
        (let [decoded-line (decode-hex-string hex-line)]
          (spit "debug.log" (str decoded-line) :append true)
          (doseq [line (cs/split-lines decoded-line)]
            (spit "test.log" (str line "\n") :append true)
            (cond
              ;; Start of a request
              (.startsWith ^String line "1 ")
              (swap! req (constantly [line]))

              ;; empty line after req
              (empty? line)
              (swap! req #(conj % line))

              ;; Autocompletion Query: Needs to be changed (req need changing too)
              ;; (.contains line "\"aggregations\":{\"autocomplete")
              (.contains line "autocomplete")
              (try
                (let [q (cjson/parse-string line)]
                  (when-let [changed-req (convert-ac-query @req q)]
                    (println (encode-to-hex-string (str (cs/join "\n" (:h changed-req))
                                                        "\n"
                                                        (cjson/generate-string (:q changed-req))))))
                  (swap! req (constantly [])))
                (catch JsonParseException _ (swap! req #(conj % line))))

              ;; Non-autocompletion query: Return as is
              (try (cjson/parse-string line) (catch Exception e nil))
              (do (println (encode-to-hex-string (str (cs/join "\n" @req) "\n" line)))
                  (swap! req (constantly [])))

              ;; part of a header
              :else
              (swap! req #(conj % line)))))
        (when-let [line (.readLine br)]
          (recur line)))
      (catch IOException e (IOUtils/closeQuietly br)))))
