(ns event-store)

(defmulti handle-command (fn [deps {:keys [type] :as payload}] type))
(defmulti apply-event    :type)
(defmulti project-event  :type)

(defn ->aggregate [type & id])
(defn ->event [type ])

(defn upsert-aggregate [])
(defn insert-event [])

(defn apply-events [db aggregate events]
  (let [aggregate (reduce #(apply-event aggregate % payload) aggregate events)]
    (upsert-aggregate db aggregate)
    (doseq [event events]
      (insert-event db aggregate event)
      (project-event db aggregate event))
    aggregate))

;; (defmethod handle-command :member/auto-sign-in
;;   [{:keys [db]} {:keys [member-id]}]
;;   (->> [(->event :member/signed-in {:member-id member-id
;;                                     :token     (generate-auth-token)})]
;;        (apply-events db (->aggregate :authorisation))
;;        (select-keys [:token])))

;; (defmethod handle-command :member/sign-in
;;   [db {:keys [email password]}]
;;   (let [member (member/find-by-email-password email password)]
;;     (when member
;;       (handle-command db {:type      :member/auto-sign-in
;;                           :member-id (:id member)}))))

(defmethod handle-command :member/sign-up
  [db payload]
  (when-not (member/exists? (:email payload))
    (let [member (apply-events db (->aggregate :member) [(->event :member/signed-up payload)])]
      (apply-events db
                    (->aggregate :authorisation)
                    [(->event :member/signed-in {:member member
                                                 :token  (generate-auth-token)})])

(defmethod apply-event :member/signed-up
  [member {:keys [email password]}]
  (assoc member
         :email    email
         :password password))

(defmethod apply-event :member/signed-in
  [authorisation {:keys [member-id token]}]
  (assoc authorisation
         :member-id member-id
         :token     token))


(defmethod project-event :member/signed-up
  [db {:keys [email password]}]
  (create-member email password))

(defmethod project-event :member/signed-in
  [db {:keys [member-id token]}]
  (create-authorisation member-id token))
