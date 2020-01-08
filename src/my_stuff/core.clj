(ns my-stuff.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [cheshire.core :as chsr]
            [clojure.string :as str]
            [meinside.clogram :as cg]
            [clojusc.env-ini.core :as env-ini]))

(def env-data (env-ini/load-data (str (System/getProperty "user.dir") "/env")))
(def telegram-token ((env-data :ini) :telegram-token))
(def vk-access-token ((env-data :ini) :vk-access-token))
(def current-year (+ (.getYear (new java.util.Date)) 1900))
(def interval 1)

(def get-query-params (fn [uid] {:query-params {:access_token vk-access-token :user_id uid :fields ["bdate"] :v 5.8}}))

(def parse-response (fn [response] (get-in (chsr/parse-string (response :body) true) [:response :items])))

(def filter-items-by-bdate (fn [item] (= (count (str/split (get item :bdate "") #"\.")) 3)))

(def get-only-years (fn [item] ((str/split (get item :bdate "") #"\.") 2)))

(def calculate-sum-age (fn [bdate-years] (reduce #(+ %1 (- current-year (Long/parseLong %2))) 0 bdate-years)))

(def calculate-average-age (fn [response] (
                                           let [parsed-items (parse-response response)
                                                only-years (->> parsed-items (filter filter-items-by-bdate) (map get-only-years))]
                                            (Math/round (float (/ (calculate-sum-age only-years) (count only-years)))))))

(defn echo
  "echo function"
  [bot update]
  (let [chat-id (get-in update [:message :chat :id])
        reply-to (get-in update [:message :message-id])
        text (get-in update [:message :text])
        response (client/get "https://api.vk.com/method/friends.get" (get-query-params text))
        message (volatile! "Something went wrong. May be this is a private profile")]
    (if (not (contains? (chsr/parse-string (response :body) true) :error)) (vreset! message (calculate-average-age response)))
    (cg/send-message bot chat-id @message))
)
(defn -main
  "Main func"
  [& args]
  (def bot (cg/new-bot telegram-token
                       :verbose? true))
  (cg/poll-updates bot interval echo))