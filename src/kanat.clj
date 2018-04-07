(ns kanat
  (:require [integrant.core :as integrant]
            [ring.adapter.jetty :as jetty]

            [ring.util.request :as ring.request]
            [ring.util.response :as ring.response]

            [hikari-cp.core :as hikari-cp]
            [migratus.core :as migratus]
            [honeysql.core :as sql]

            [clojure.java.jdbc :as db]
            [clojure.edn :as edn]))

;; EVENT-SOURCING
;; ===================================
;; ===================================


;; COMMAND
;; command-id
;; command-type
;; payload....

;; EVENT
;; event-id (from command-id)
;; event-type
;; payload....

;; 1 command - multiple events
;; command - unit of work (transaction)

;; - load aggregate
;; - validate
;; - reject with error
;; - insert and apply events
;; - command result

(defmulti handle-command  (fn [db {:keys [type] :as payload}] type))
(defmulti apply-event    (fn [db {:keys [type] :as payload}] type))
(defmulti project-event  (fn [db {:keys [type] :as payload}] type))



(defmethod handle-command :member/sign-up
  [db payload]
  (if-not ok?
    (errors)

    [(->aggregate :member)
     [[:member/signed-up payload]
      [:member/signed-in {:token (gen-auth-token)}]]
     [:welcom-member]]))

(defmethod apply-event :member/signed-up
  [member {:keys [email password]}]
  (assoc member
         :email    email
         :password password))

(defmethod apply-event :member/signed-in
  [member {:keys [token]}]
  (update-in member :auth-tokens assoc token))


(defmethod handle-command :member/sign-in
  [db payload]
  (if-not ok?
    (errors)
    (apply-events db
                  (->aggregate :member id)
                  [[:member/signed-in {:token (gen-auth-token)}]])))


(defn uuid []
  (java.util.UUID/randomUUID))

(defn ->aggregate [type & id]
  {:type type
   :id   (or id (uuid))})

(defn ->event [type payload]
  {:type    type
   :id      (or (:id payload) (uuid))
   :payload (apply dissoc payload :type :id)})

(defn insert-event [db aggregate-id event]
  (prn "INSERTING EVENT" event))

(defn apply-event* [db aggregate event]
  (insert-event db (:id aggregate) event)

  (let [aggregate-before aggregate
        aggregate-after  (apply-event aggregate event)]
    (project-event db aggregate-before aggregate-after event)
    (aggregate-after)))

(defn upsert-aggregate [db aggregate]
  (prn "UPSERTING AGGREGATE:" aggregate))

(defn apply-events [db aggregate events]
  (let [aggregate (reduce #(apply-event* aggregate %) aggregate events)]
    (upsert-aggregate db aggregate)
    aggregate))



;; ===================================


(defmethod handle-command :employee/add
  [db payload]
  (let [employee (->aggregate :employee)]
    (handle-events db employee [(->event :employee/added payload )])))

(defmethod apply-event :employee/added
  [db aggregate event]
  (prn "PROJECTING EVENT")
  (merge aggregate (:payload event)))

(defmethod project-event :employee/added
  [db aggregate event]
  ;; INSERT INTO EMPLOYEES here
  (prn "PROJECTING EVENT"))



(defn handler [{:keys [uri params] :as request}]

  (let [response (condp = uri
                   "/command" (handle-command {} params)
                   "query"   "")]
    {:status  200
     :headers {"Content-Type" "application/edn"}
     :body    response}))


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


;; http

(defn guard [handler]
  (fn [{:keys [uri request-method] :as request}]
    (if (and (= :post request-method)
             (= "/command" uri)
             (= "application/edn" (get-in request [:headers "content-type"])))
      (handler request)
      {:status  400 :headers {}})))

(defn wrap-edn-request [handler]
  (fn [request]
    (-> request
        (update :params merge (-> request :body slurp edn/read-string))
        (handler))))

(defmethod integrant/init-key :http/handler
  [_ {:keys [db-connection]}]
  (->> handler
       guard
       wrap-edn-request))


(def system
  (integrant/init config))

(integrant/halt! system)

(defn -main []
  (println "Started"))
