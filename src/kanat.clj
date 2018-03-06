(ns kanat
  (:require [integrant.core :as integrant]
            [ring.adapter.jetty :as jetty]
            [hikari-cp.core :as hikari-cp]
            [honeysql.core :as sql]
            [clojure.java.jdbc :as db]))

(def query {:select [:id]
            :from [:foo]
            :where [:= :foo.id 2]})

(defn handler [db-connection request]
  (let [a (->> query sql/format (db/query db-connection) first :id)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "Hello, " a)}))



(def db-config {:adapter       "postgresql"
                :server-name   "localhost"
                :database-name "kanat"})

(def config
  {:db/pool       db-config
   :db/connection {:db-pool (integrant/ref :db/pool)}
   :http/server   {:port 3000 :handler (integrant/ref :http/handler)}
   :http/handler  {:db-connection (integrant/ref :db/connection)}})



(defmethod integrant/init-key :db/pool
  [_ config]
  (hikari-cp/make-datasource config))

(defmethod integrant/halt-key! :db/pool
  [_ this]
  (hikari-cp/close-datasource this))

(defmethod integrant/init-key :db/connection
  [_ {:keys [db-pool]}]
  {:datasource db-pool})


(defmethod integrant/init-key :http/server
  [_ {:keys [handler] :as config}]
  (jetty/run-jetty handler (-> config (dissoc :handler) (assoc :join? false))))

(defmethod integrant/halt-key! :http/server
  [_ this]
  (.stop this))

(defmethod integrant/init-key :http/handler
  [_ {:keys [db-connection]}]
  (partial handler db-connection))


;; (def system
;;   (integrant/init config))

;; (integrant/halt! system)

(defn -main []
  (println "Started"))
