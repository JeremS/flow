(ns flow.core
  (:require [clojure.set :refer [union difference intersection]]))


(def whole-db [])

(def empty-system
  {::reactions-by-name {}

   ::default-output [:computed]

   ::reaction-names #{}

   ::reactions-dependencies {}

   ::reactions-outputs {}

   ::data-dependencies {}})


(defn ensure-dest [reaction path]
  (if (:dest reaction)
    reaction
    (assoc reaction :dest (conj path (:name reaction)))))



(defn path->dependency-tree [tree path n]
  (assoc-in tree (conj path n) n))

(defn paths->dependency-tree [tree reaction-name paths]
  (reduce #(path->dependency-tree %1 %2 reaction-name) tree paths))

(defn paths->modification-tree [paths]
  (paths->dependency-tree {} ::sentinel paths))


(defn top-level-leaves [m]
  (into #{} (remove map?) (vals m)))

(defn leaves [v]
  (letfn [(internal-leaves [v]
            (cond
              (map? v) (into [] (mapcat leaves) (vals v))
              (nil? v) []
              :else [v]))]
    (set (internal-leaves v))))

(defn sentinel? [m]
  (= (get m ::sentinel false) ::sentinel))

(defn find-deps [tree m-tree]
  (if (sentinel? m-tree)
    (leaves tree)
    (apply union
           (top-level-leaves tree)
           (for [[k v] (seq m-tree)]
             (let [sub-tree (get tree k ::unwatched-branch)]
               (when-not (= ::unwatched-branch sub-tree)
                 (find-deps sub-tree v)))))))

(defn find-direct-dependencies [tree paths]
  (or (find-deps tree (paths->modification-tree paths)) #{}))


(defn add-to-dependency-tree [system tree-key name paths]
  (let [tree (get system tree-key)]
    (assoc system tree-key
                  (paths->dependency-tree tree name paths))))

(defn add-data-dependencies [system reaction]
  (let [{:keys [name srcs]} reaction]
    (add-to-dependency-tree system ::data-dependencies name (keys srcs))))


(defn add-reaction-outputs [system reaction]
  (let [reaction-outputs (::reactions-outputs system)
        {:keys [name dest]} reaction
        overrides (find-direct-dependencies reaction-outputs [dest])]
    (when-not (empty? overrides)
      (throw (ex-info "Dest conflict, 2 reactions can't override eachother"
                      {:name name :dest dest :overriding overrides})))
    (add-to-dependency-tree system ::reactions-outputs name [dest])))


;; when the sources of a reaction r are destinations of other reactions #{RS}
;; the reaction #{RS} are parent reactions of r.
;; find which reaction I depend on

;; study of other reactions outputs
(defn add-dependencies [system reaction]
  (let [reactions-outputs (::reactions-outputs system)
        reactions-dependencies (::reactions-dependencies system)
        {:keys [name srcs]} reaction
        reactions-modifiying-srcs (find-direct-dependencies reactions-outputs (keys srcs))]
    (assoc system ::reactions-dependencies
                  (reduce #(update %1 %2 conj name)
                          reactions-dependencies
                          reactions-modifiying-srcs))))


;; When a reaction R has a destination that is a source for other reaction r1...rn
;; each of the r1...rn reactions are chldren reactions of R
;; Find who is dependent on me

;; study of other reactions input
(defn add-dependent-reactions [system reaction]
  (let [data-dependencies (::data-dependencies system)
        {:keys [name dest]} reaction
        observers (find-direct-dependencies data-dependencies [dest])]
    (assoc-in system [::reactions-dependencies name] observers)))




(defn add-reaction [system reaction]
  (let [reaction-names (::reaction-names system)
        reaction (ensure-dest reaction (::default-output system))
        {:keys [name srcs dest]} reaction]
    (cond
      (reaction-names name)
      (throw (ex-info "Can't override existing reaction" {:reaction-name name}))

      (not (= ::not-found (get-in system [::reactions-outputs dest] ::not-found)))
      (throw (ex-info "Destination already used." {:dest dest}))

      :else nil)

    (-> system
        (assoc-in [::reactions-by-name name] reaction)
        (update ::reaction-names conj name)
        (add-data-dependencies reaction)
        (add-reaction-outputs reaction)
        (add-dependencies reaction)
        (add-dependent-reactions reaction))))

(defn remove-reaction [system reaction-name]
  (letfn [(remove-from-table [system]
            (update system ::reactions-by-name dissoc reaction-name))

          (remove-from-names [system]
            (update system ::reaction-names disj reaction-name))

          (remove-srcs [system reaction]
            (let [data-dependencies (::data-dependencies system)]
              (assoc system ::data-dependencies
                            (reduce #(update-in %1 %2 dissoc reaction-name)
                                    data-dependencies
                                    (-> reaction :srcs keys)))))

          (remove-dest [system reaction]
            (assoc system ::reactions-outputs
                          (update-in (::reactions-outputs system)
                                     (:dest reaction)
                                     dissoc reaction-name)))
          (remove-dependencies [system]
            (let [deps (::reactions-dependencies system)
                  deps (dissoc deps reaction-name)]
              (assoc system ::reactions-dependencies
                            (persistent!
                              (reduce-kv (fn [d r rs]
                                           (assoc! d r (disj rs reaction-name)))
                                         (transient {})
                                         deps)))))
          (remove-from-order [system]
            (if-let [order (::reactions-order system)]
              (assoc system ::reactions-order
                (into [] (remove #(= reaction-name %)) order))
              system))]
    (let [reaction (get-in system [::reactions-by-name reaction-name])]
      (if-not reaction
        system
        (-> system
            (remove-from-table)
            (remove-from-names)
            (remove-srcs reaction)
            (remove-dest reaction)
            (remove-dependencies)
            (remove-from-order))))))


(defn add-reactions [system reactions]
  (reduce add-reaction system reactions))

;; adaptation from https://gist.github.com/alandipert/1263783
;; considers the graph already normalized.
(defn topological-sort [graph]
  (letfn [(take-1 [s]
            (let [e (first s)]
              [e (disj s e)]))

          (un-pointed [g]
            (let [nodes (set (keys g))
                  pointed-nodes (reduce union (vals g))]
              (difference nodes pointed-nodes)))

          (internal [g L S]
            (if (empty? S)
              (when (every? empty? (vals g)) L)
              (let [[node r] (take-1 S)
                    pointed (get g node)
                    g' (assoc g node #{})
                    S' (union r (intersection (un-pointed g') pointed))]
                (recur g' (conj L node) S'))))]
    (internal graph [] (un-pointed graph))))

(defn ready-system [system]
  (let [reactions-dependencies (::reactions-dependencies system)
        order (topological-sort reactions-dependencies)]
    (when-not order
      (throw (ex-info "Cyclic dependency detected" {})))
    (assoc system ::reactions-order order)))


(defn- find-child-reactions
  [reactions-dependencies parent-reactions already-found]
  (let [children-sets (-> reactions-dependencies
                          (select-keys parent-reactions)
                          vals)]
    (transduce
      (comp cat (remove already-found))
      conj
      #{}
      children-sets)))

(defn add-transitive-reactions [dependency-graph parent-reactions]
  (loop [parent-reactions parent-reactions
         child-reactions (find-child-reactions dependency-graph
                                               parent-reactions
                                               parent-reactions)]
    (if (empty? child-reactions)
      parent-reactions
      (let [res (union parent-reactions child-reactions)]
        (recur res (find-child-reactions dependency-graph
                                         child-reactions
                                         res))))))


(defn compute-affected-reactions [system changed-paths]
  (let [changed-paths (set changed-paths)
        reactions-dependencies (::reactions-dependencies system)
        parent-reactions (find-direct-dependencies (::data-dependencies system) changed-paths)]
    (add-transitive-reactions reactions-dependencies parent-reactions)))


(defn srcs->parameter-map [db srcs]
  (persistent!
    (reduce-kv
      (fn [acc k v]
        (assoc! acc v (get-in db k)))
      (transient {})
      srcs)))

(defn reaction-name->computation [system reaction-name]
  (let [reaction (get-in system [::reactions-by-name reaction-name])
        {f :fn srcs :srcs dest :dest} reaction]
    (fn [db]
      (assoc-in db dest
                (f (srcs->parameter-map db srcs))))))


(defn execute-system [db system changed-paths]
  (let [xform (if ((set changed-paths) whole-db)
                (map #(reaction-name->computation system %))
                (comp (keep (compute-affected-reactions system changed-paths))
                      (map #(reaction-name->computation system %))))]
    (transduce
      xform
      (fn
        ([] db)
        ([db] db)
        ([db c] (c db)))
      (::reactions-order system))))





(defn setup
  ([init-data reactions]
   (setup init-data ::system reactions))
  ([init-data system-key reactions]
   (let [system (-> empty-system
                    (add-reactions reactions)
                    (ready-system))]
     (-> init-data
         (assoc system-key system)
         (execute-system system #{whole-db})))))


