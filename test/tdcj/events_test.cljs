(ns tdcj.events-test
  (:require [cljs.test :refer [deftest is testing]]
            [spy.core :as spy]
            [tdcj.events :as e]
            [tdcj.db :as db]))

(def ^:private db {:todos []
                   :count 0
                   :new-todo-txt ""})

(def ^:private base-ctx {:coeffects {:db {:todos [{:id 0
                                                   :txt "zero"
                                                   :done 'any
                                                   :editing 'any}
                                                  {:id 1
                                                   :txt "one old"
                                                   :done 'any
                                                   :editing 'any}]}}
                         :effects {:db {:todos [{:id 1
                                                 :txt "one new"
                                                 :done 'any
                                                 :editing 'any}
                                                {:id 2
                                                 :txt "two"
                                                 :done 'any
                                                 :editing 'any}]}}})

(defn- set-event [ctx e]
  (assoc-in ctx [:coeffects :event] e))
(defn- set-effect [ctx effect-key effect-val]
  (assoc-in ctx [:effects effect-key] effect-val))

(deftest event-add-todo
  (testing "empty todo text: does not add a new todo"
    (is (= db
           (e/add-todo db ['any ""]))))

  (testing "some todo text: adds a new todo"
    (let [expected-db {:todos [{:txt "some todo txt"
                                :id 0
                                :done false
                                :editing false}]
                       :count 1
                       :new-todo-txt ""}]
      (is (= expected-db
             (e/add-todo db ['any "some todo txt"])))
      (is (= expected-db
             (e/add-todo (assoc db :new-todo-txt "non-empty")
                         ['any "some todo txt"]))))))

(deftest event-remove-todo
  (let [db (assoc db :todos ['todo1 'todo2 'todo3])
        expected-db (assoc db :todos ['todo1 'todo3])]
    (is (= expected-db (e/remove-todo db ['any 1])))))

(deftest intercept-todo-store-effect
  (testing "add todo"
    (let [ctx (set-event base-ctx [::e/add-todo 'any])
          expected (set-effect ctx ::e/put-todo-store ["todo:2" {:id 2
                                                                 :txt "two"
                                                                 :done 'any}])]
      (is (= expected (e/todo-store-effect ctx)))))
  (testing "strike todo"
    (let [index 0
          ctx (set-event base-ctx [::e/strike-todo index])
          expected (set-effect ctx ::e/put-todo-store ["todo:1" {:id 1
                                                                 :txt "one new"
                                                                 :done 'any}])]
      (is (= expected (e/todo-store-effect ctx)))))
  (testing "edit todo"
    (let [set-editing (fn [ctx val] (assoc-in ctx [:effects :db :todos 0 :editing] val))
          index 0
          ctx (set-event base-ctx [::e/edit-todo index])]
      (let [ctx (set-editing ctx false)
            expected (set-effect ctx ::e/put-todo-store ["todo:1" {:id 1
                                                                   :txt "one new"
                                                                   :done 'any}])]
        (is (= expected (e/todo-store-effect ctx))))
      (let [ctx (set-editing ctx true)
            expected ctx]
        (is (= expected (e/todo-store-effect ctx))))))
  (testing "remove todo"
    (let [index 0
          ctx (set-event base-ctx [::e/remove-todo index])
          expected (set-effect ctx ::e/delete-todo-store "todo:0")]
      (is (= expected (e/todo-store-effect ctx)))))
  (testing "unknown event"
    (let [ctx (set-event base-ctx [:unknown 'any])
          expected ctx]
      (is (= expected (e/todo-store-effect ctx))))))

(deftest fx-put-todo-store
  (let [set-local (spy/spy)
        get-todo-ids (spy/stub ["id1" "id2"])
        put-todo-store (partial e/put-todo-store set-local get-todo-ids)]

    (testing "todo in store"
      (put-todo-store ["id2" 'todo])
      (is (spy/called-once-with? set-local "id2" 'todo)))

    (testing "todo not in store"
      (spy/reset-spy! set-local)
      (put-todo-store ["id3" 'todo])
      (is (spy/called-n-times? set-local 2))
      (is (spy/called-with? set-local "id3" 'todo))
      (is (spy/called-with? set-local db/todo-ids-key ["id1" "id2" "id3"])))))

(deftest fx-delete-todo-store 
  (let [set-local (spy/spy)
        remove-local (spy/spy)
        get-todo-ids (spy/stub ["id1" "id2" "id3"])
        delete-todo-store (partial e/delete-todo-store set-local remove-local get-todo-ids)]

    (testing "todo in store"
      (delete-todo-store "id1")
      (is (spy/called-once-with? remove-local "id1"))
      (is (spy/called-once-with? set-local db/todo-ids-key ["id2" "id3"])))

    (testing "todo not in store"
      (spy/reset-spy! set-local)
      (spy/reset-spy! remove-local)
      (delete-todo-store "idx")
      (is (spy/not-called? remove-local))
      (is (spy/not-called? set-local)))))