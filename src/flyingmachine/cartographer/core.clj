(ns flyingmachine.cartographer.core)

(declare mapify)

;;;;
;; Below are functions for defining serializers

(defn attr
  "Name is the key you want in the result map
Retriever is the function that will be applied to the entity"
  [name retriever]
  {name (eval retriever)})

(defn has
  [name arity directives]
  {name (apply assoc {:arity arity} directives)})

(defn has-one
  "Used to define a has-one relationship"
  [name & directives]
  (has name :one directives))

(defn has-many
  [name & directives]
  (has name :many directives))

;; (defn ref-count
;;   [ref-attr]
;;   #(ffirst (db/q [:find '(count ?c) :where ['?c ref-attr (:db/id %)]])))

(defmacro defmaprules
  [name & fields]
  `(def ~name
     (let [seed# {:attributes {} :relationships {}}]
       (reduce (fn [result# [fun# & args#]]
                 (let [tomerge# (apply (resolve fun#) args#)
                       destination# (if (:rules (first (vals tomerge#))) :relationships :attributes)]
                   (update-in result# [destination#] merge tomerge#)))
               seed#
               (quote [~@fields])))))


;;;;
;; Below are functions for actually serializing an entity

(defn apply-options-to-attributes
  [attributes options]
  (if-let [only (:only options)]
    (select-keys attributes only)
    (apply dissoc attributes (:exclude options))))

(defn apply-attribute-rules
  [entity attributes options]
  (reduce (fn [acc [attr-name retrieval-fn]]
            (conj acc [attr-name (retrieval-fn entity)]))
          {}
          (apply-options-to-attributes
           attributes
           options)))

(defn apply-options-to-relationships
  [relationships options]
  (let [include (:include options)]
    (cond
     (keyword? include) (select-keys relationships [include])
     (empty? include) {}
     (map? include) (reduce merge {} (map (fn [[k o]]
                                            {k (merge (k relationships) {:options o})})
                                          include))
     :else (select-keys relationships include))))

(defn mapify-relationship
  [entity directives]
  (let [serialize-retrieved #(mapify
                              %
                              (eval (:rules directives))
                              (:options directives))
        retrieved ((eval (:retriever directives)) entity)]
    (cond
     (= :one (:arity directives)) (serialize-retrieved retrieved)
     (= :many (:arity directives)) (map serialize-retrieved retrieved))))

(defn apply-relationship-rules
  [entity relationships options]
  (reduce (fn [acc [attr-name directives]]
            (merge acc {attr-name (mapify-relationship entity directives)}))
          {}
          (apply-options-to-relationships
           relationships
           options)))

(defn mapify
  "Given a map of transformations, apply them such that a map is
  returned where the keys of the return and the transformations are
  the same, and the return values are derived by applying the
  values of the transformation map to the supplied entity"
  ([entity rules]
     (mapify entity rules {}))
  ([entity rules options]
     (merge
      (apply-attribute-rules entity (:attributes rules) options)
      (apply-relationship-rules entity (:relationships rules) options))))