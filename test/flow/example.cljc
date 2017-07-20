(ns flow.example
  (:require
    [flow.core :as flow]))


(def init-data
  {:parameters {:turnover 1000
                :tax 10}
   :charges [[:charges/by-id 1]]
   :charges/by-id {1 {:id 1 :name "Marketing" :cost 100}}})



(defn compute-tax-cost [{:keys [turnover tax]}]
  (* turnover (/ tax 100)))

(def tax-cost
  {:name :tax-cost
   :srcs {[:parameters :turnover] :turnover
          [:parameters :tax]      :tax}
   :dest [:computed :tax-cost]
   :fn   compute-tax-cost})


(defn compute-turnover-after-tax
  [{:keys [turnover cost]}]
  (- turnover cost))

(def turnover-after-tax
  {:name :turnover-after-tax
   :srcs {[:parameters :turnover] :turnover
          [:computed   :tax-cost] :cost}
   :dest [:computed :turnover-after-tax]
   :fn   compute-turnover-after-tax})


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
          [:computed :turnover-after-tax] :t}
   :dest [:computed :result]
   :fn compute-result})

(def system
  (-> flow/empty-system
      (flow/add-reactions [tax-cost total-charges turnover-after-tax result])
      (flow/ready-system)))



(def db
  (flow/execute-system init-data system #{flow/whole-db}))


(def db1
  (-> db
      (assoc-in [:charges/by-id 2] {:id 2 :name "travel expenses" :cost 50})
      (flow/execute-system system #{[:charges/by-id]})))

(def db2
  (-> db1
      (assoc-in [:charges/by-id 2 :cost] 150)
      (flow/execute-system system #{[:charges/by-id 2]}))) system #{[:charges/by-id 2]}