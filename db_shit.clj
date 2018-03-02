(require '[ragtime.jdbc :as jdbc])
(require '[ragtime.repl :as repl])

(def db-spec {:dbtype "postgresql"
              :host   "localhost"
              :dbname "kanat"})

(def config
  {:datastore  (jdbc/sql-database db-spec)
   :migrations (jdbc/load-resources "migrations")})

(repl/migrate config)

;; (require '[clojure.java.jdbc :as j])
(repl/rollback config)


(require '[clojure.java.jdbc :as j])
(require '[honeysql.core :as sql])
(require '[hikari-cp.core :as hikari-cp])

(j/insert-multi! db-spec :foo [{:id 1} {:id 2}])

(def datasource-options {:adapter       "postgresql"
                         :server-name   "localhost"
                         :database-name "kanat"})

(def datasource
  (hikari-cp/make-datasource datasource-options))


(def query {:select [:id]
            :from [:foo]
            :where [:= :foo.id 2]})


(time
 (j/with-db-connection [conn db-spec]
   (->> query
        sql/format
        (j/query conn)
        (mapv prn))))

(time
 (j/with-db-connection [conn {:datasource datasource}]
   (->> query
        sql/format
        (j/query conn)
        (mapv prn))))

(close-datasource datasource)
