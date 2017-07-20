(ns flow.specs.core
  (:require
    #?(:clj [clojure.spec.alpha :as s]
       :cljs [cljs.spec.alpha :as s])))


(s/def ::path (s/and vector?
                     (s/* keyword?)))

