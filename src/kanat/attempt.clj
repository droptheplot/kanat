(ns kanat.attempt)

(defrecord Failure [payload])

(defn failure? [x]
  (instance? Failure x))

(defn success? [x]
  (complement failure?))

(defn fail
  ([] (fail {}))
  ([payload] (->Failure payload)))

(defmacro if-let-success?
  ([[sym form] success-branch]
   `(if-let-success? [~sym ~form] ~success-branch ~sym))
  ([[sym form] success-branch failure-branch]
   `(let [~sym ~form]
      (if (success? ~sym)
        ~success-branch
        ~failure-branch))))

(defmacro if-let-failure?
  ([[sym form] failure-branch]
   `(if-let-failure? [~sym ~form] ~failure-branch ~sym))
  ([[sym form] failure-branch success-branch]
   `(let [~sym ~form]
      (if (failure? ~sym)
        ~failure-branch
        ~success-branch))))

(defn- attempt*
  [bindings body]
  (->> bindings
       (partition 2)
       (reverse)
       (reduce (fn [next [sym form]]
                 `(if-let-failure? [~sym ~form]
                    ~sym
                    ~next))
               body)))

(defmacro attempt
  ([bindings body]
   (attempt* bindings body))
  ([bindings body on-fail]
   `(if-let-failure? [result# (attempt ~bindings ~body)]
      (~on-fail result#)
      result#)))

(defmacro attempt->
  [expr & forms]
  (let [g (gensym)
        steps (map (fn [step] `(if (failure? ~g) ~g (-> ~g ~step)))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro attempt->>
  [expr & forms]
  (let [g (gensym)
        steps (map (fn [step] `(if (failure? ~g) ~g (->> ~g ~step)))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))
