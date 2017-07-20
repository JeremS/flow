(ns flow.utils)


(defn memoize-last-call [f]
  (let [cache (atom {:last-args nil
                     :last-res nil
                     :initialized false})]
    (fn [& args]
      (:last-res
        (swap! cache (fn [{:keys [initialized last-args] :as c}]
                       (if (and initialized (= args last-args))
                         c
                         {:last-args args
                          :last-res (apply f args)
                          :initialized true})))))))