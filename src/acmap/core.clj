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


(defn get-content-length
  [query]
  (alength (.getBytes (cjson/generate-string query))))


(defn convert-ac-query
  "Convert an old autocompletion request to the new autocompletion request.
  1. Change the ES query based on the field.
  2. Change HTTP header to point to the correct URI."
  [req q]
  (when (or ((set (keys q)) "fld-suggestions")
            ((set (keys q)) "query"))
   (let [cfg (get-cfg)
         domain (get-in q ["aggregations" "autocomplete" "filter" "term" "_domain"])
         fld (get-in q ["aggregations" "autocomplete" "aggs" "autocomplete" "terms" "field"])
         fld (if (.endsWith fld ".raw") (subs fld 0 (- (count fld) 4)) fld)
         typed-by-user (or (get-in q ["query" "match_phrase_prefix" fld "query"]) "")
         ac-fld (str fld ".autocomplete")
         size 25]
     (case (cfg fld)
       nil nil
       :completion (let [query {:fld-suggestions
                                {:text typed-by-user
                                 :completion {:context {:_domain domain}
                                              :field ac-fld
                                              :size size}}}]
                     {:h (for [header req]
                           (cond
                             (cs/starts-with? header "POST")
                             "POST /moby-advsearch/_suggest? HTTP/1.1"

                             (cs/starts-with? header "Content-Length: ")
                             (str "Content-Length: " (get-content-length query))

                             :else
                             header))
                      :q query})

       :ngram-words (let [query {:query {:filtered {:query {:match {ac-fld typed-by-user}}
                                                    :filter {:term {:_domain domain}}}}
                                 :aggregations {:fld-suggestions
                                                {:terms
                                                 {:field (str fld ".raw")
                                                  :size size
                                                  :order {:ranking "desc"}}
                                                 :aggs {:ranking {:max {:script "_score"}}}}}}]
                      {:q query
                       :h (for [header req]
                            (cond
                              (cs/starts-with? header "POST")
                              "POST /moby-advsearch/issue/_search?search_type=count&query_cache=true HTTP/1.1"

                              (cs/starts-with? header "Content-Length: ")
                              (str "Content-Length: " (get-content-length query))

                              :else
                              header))})

       :ngram-raw (let [query {:query {:filtered {:query {:match {ac-fld typed-by-user}}
                                          :filter {:term {:_domain domain}}}}
                       :aggregations {:fld-suggestions
                                      {:terms
                                       {:field (str fld ".raw")
                                        :size size
                                        :order {:ranking "desc"}}
                                       :aggs {:ranking {:max {:script "_score"}}}}}}]
                    {:q query
                     :h (for [header req]
                          (cond
                            (cs/starts-with? header "POST")
                            "POST /moby-advsearch/issue/_search?search_type=count&query_cache=true HTTP/1.1"

                            (cs/starts-with? header "Content-Length: ")
                            (str "Content-Length: " (get-content-length query))

                            :else
                            header))})))))


(defn decode-hex-string
  [s]
  (String. (Hex/decodeHex (.toCharArray s))))


(defn encode-to-hex-string
  [^String s]
  (String. (Hex/encodeHex (.getBytes s))))


(defn -main
  [& args]
  (let [br (BufferedReader. (InputStreamReader. System/in))
        req (atom [])]
    (try
      (loop [hex-line (.readLine br)]
        (let [decoded-req (decode-hex-string hex-line)
              http-request (partition-by empty? (cs/split-lines decoded-req))
              headers (first http-request)
              body (when (= 3 (count http-request)) (last http-request))]

          (if (and body
                   (= 1 (count body))
                   (.contains (first body) "autocomplete")
                   (try (cjson/parse-string (first body)) (catch Exception _ nil)))

            ;; using when-let because we removed autocompletions of some fields
            (when-let [changed-req (convert-ac-query headers (cjson/parse-string (first body)))]
              (println (encode-to-hex-string (str (cs/join "\n" (:h changed-req))
                                                  "\n\n"
                                                  (cjson/generate-string (:q changed-req))))))

            (println (encode-to-hex-string (str (cs/join "\n" headers)
                                                (when body
                                                  (str "\n\n"
                                                       (cs/join "\n" body))))))))
        (when-let [line (.readLine br)]
          (recur line)))
      (catch IOException e (IOUtils/closeQuietly br)))))
