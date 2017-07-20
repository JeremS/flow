(ns flow.simple-ex
  (:require [flow.core :as flow]))

(def init-data
  {:parameters {:turnover 1000}
   :charges [[:charges/by-id 1]]
   :charges/by-id {1 {:id 1 :name "Marketing" :cost 100}}})


(defn compute-total-charges
  [{:keys [charges-table]}]
  (transduce (map :cost)
             +
             (vals charges-table)))

(def total-charges
  {:name :total-charges
   :srcs {[:charges/by-id] :charges-table}
   :dest [:computed :total-charges]
   :fn compute-total-charges})


(defn compute-result
  [{:keys [t cs]}]
  (- t cs))

(def result
  {:name :result
   :srcs {[:computed :total-charges] :cs
          [:parameters :turnover] :t}
   :dest [:computed :result]
   :fn compute-result})

(def system
  (-> flow/empty-system
      (flow/add-reactions [total-charges  result])
      (flow/ready-system)))

(def db (flow/execute-system init-data system #{flow/whole-db}))

(def db2
  (-> db
      (assoc-in [:charges/by-id 2] {:id 2 :name "travel expenses" :cost 200})
      (flow/execute-system system #{[:charges/by-id 2]})))
