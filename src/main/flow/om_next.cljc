(ns flow.om-next
  (:require
    [flow.core :refer [execute-system]]))



(defn wrap-mutate
  ([mutate-fn]
   (wrap-mutate mutate-fn :flow/system))
  ([mutate-fn system-key]
   (fn [env key params]
     (let [mutate-res (mutate-fn env key params)
           {:keys [db paths]} mutate-res
           re-computed-db (execute-system db (get db system-key) paths)]
       (-> mutate-res
           (assoc :action (fn [] (reset! (:state env) re-computed-db)))
           (dissoc :db :paths))))))