(ns flyingmachine.cartographer.t-core
  (:use midje.sweet)
  (:require [flyingmachine.cartographer.core :as c]))

(def t1 {:topic/title "First topic"
         :db/id 100})

(def extension-fn (constantly "extension-fn"))

(def posts
  [{:post/content "T1 First post content"
    :post/topic t1
    :post/extension "extension 1"
    :db/id 101}
   {:post/content "T1 Second post content"
    :post/topic t1
    :post/extension "extension 2"
    :db/id 102}])

(c/defmaprules ent->post
  (c/attr :id :db/id)
  (c/attr :content :post/content)
  ;; notice that that the second value of attr can be any function
  (c/attr :topic-id (comp :db/id :post/topic))
  (c/has-one :topic
             :rules flyingmachine.cartographer.t-core/ent->topic
             :retriever :post/topic))

(c/defmaprules ent->topic
  (c/attr :id :db/id)
  (c/attr :title :topic/title)
  (c/has-many :posts
              :rules flyingmachine.cartographer.t-core/ent->post
              :retriever (fn [_] posts)))

(def extended-post
  (c/extend-maprules
   ent->post
   :add
   (c/attr :extension :post/extension)
   (c/attr :extension-fn extension-fn)))

(fact "mapify does not include relationships by default"
  (let [serialization (c/mapify t1 ent->topic)]
    serialization => (just {:id 100 :title "First topic"})))

(fact "mapify lets you include relationships"
  (let [t-posts (:posts (c/mapify t1 ent->topic {:include :posts}))]
    (count t-posts) => 2
    t-posts => (contains (map #(c/mapify % ent->post) posts))))

(fact "mapify lets you exclude attributes"
  (c/mapify t1 ent->topic {:exclude [:id]}) =not=> (contains {:id 100}))

(fact "you can exclude attributes of relationships"
  (let [t-posts (:posts (c/mapify t1 ent->topic {:include {:posts {:exclude [:id :topic-id]}}}))]
    (count t-posts) => 2
    t-posts => (contains {:content "T1 First post content"})
    t-posts => (contains {:content "T1 Second post content"})))

(fact "you can exclude all attributes except those specified with :only"
  (c/mapify t1 ent->topic {:only [:id]}) => (just {:id 100}))

(fact "you can use :only in relationships"
  (let [t-posts (:posts (c/mapify t1 ent->topic {:include {:posts {:only [:content]}}}))]
    (count t-posts) => 2
    t-posts => (contains {:content "T1 First post content"})
    t-posts => (contains {:content "T1 Second post content"})))

(fact "you can include relationships of relationships"
  (let [topic (c/mapify t1 ent->topic {:include {:posts {:exclude [:id :topic-id :content]
                                                            :include :topic}}})
        t-posts (:posts topic)
        p-topic (select-keys topic [:id :title])]
    (count t-posts) => 2
    t-posts => (contains {:topic p-topic})))

(fact "you can extend maprules"
  (let [post (c/mapify (first posts) extended-post)]
    (:extension post) => "extension 1"
    (extension-fn post) => "extension-fn"))
