(ns kanat
  (:require [integrant.core :as integrant]
            [ring.adapter.jetty :as jetty]
            [hikari-cp.core :as hikari-cp]
            [migratus.core :as migratus]
            [honeysql.core :as sql]
            [clojure.java.jdbc :as db]))

(def query {:select [:name]
            :from [:foo]
            :where [:= :id 2]})

(defn handler [db-connection request]
  (let [a (->> query sql/format (db/query db-connection) first :name)]
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
  (let [connection {:datasource db-pool}]
    (migratus/migrate {:migration-dir "migrations/" :store :database :db connection})
    connection))


(defmethod integrant/init-key :http/server
  [_ {:keys [handler] :as config}]
  (jetty/run-jetty handler (-> config (dissoc :handler) (assoc :join? false))))

(defmethod integrant/halt-key! :http/server
  [_ this]
  (.stop this))

(defmethod integrant/init-key :http/handler
  [_ {:keys [db-connection]}]
  (partial handler db-connection))


(def system
  (integrant/init config))

(integrant/halt! system)

(defn -main []
  (println "Started"))
