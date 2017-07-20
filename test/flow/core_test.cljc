(ns flow.core-test
  (:require
    [flow.core :as flow]
    [flow.example :as ex]
    #?@(:clj [[clojure.test :refer [deftest testing is are]]]
        :cljs [[clojure.test :refer-macros [testing is are]]
               [devcards.core :refer-macros [defcard defcard-doc mkdn-pprint-source deftest]]])))



(def dependency-tree-example
  (-> {}
    (flow/paths->dependency-tree :r0 #{[:a] [:b :c]})
    (flow/paths->dependency-tree :r1 #{[:a :b]})
    (flow/paths->dependency-tree :r2 #{[]})
    (flow/paths->dependency-tree :r3 #{[:a :c]})))

#?(:cljs
    (defcard-doc
      "
      # A tour of Flow's internals

      ## Data dependency and dependency trees

      ### Generalities
      The first concept we use in this library is the dependency tree.
      It is used to keep track of which parts of the DB reactions observe,
      which parts of the DB reactions write to and which reactions should
      run based on a set of paths describing where change occured in the db.

      These trees allow us to refactor a set of paths into one data structure.
      The basic idea is to have the branches of the tree representing
      paths in our DB and the leaves representing reactions.

      When a reaction `:r` is based on data at the path `[:a :b :c]`
      we get the tree
      "
      {:a {:b {:c {:r :r}}}}

      "
      If we have another reaction :r' watching the path [:a :b :c']
      we get the overall tree
      "
      {:a {:b {:c  {:r :r}
               :c' {:r' :r'}}}}

      "
      Our alogorithms can do the job for the partial path [:a :b] once instead
      of once for each path.

      Our system map stores 2 of these datastructures:

      - under the key `:flow/data-dependencies` to track which reaction
      looks where in the DB.
      - under the key `:flow/reactions-outputs` to track where reaction store their
      value, and determine if a reaction would override the result of another.

      These trees are updated everytime a reaction is added to the system.
      "

      "
      ### Implementation details
      Each node in our tree is a clojure map.

      A branch in the tree is represented
      by a key-value pair of a node where the key is part of a path and the
      value is a node (aka a map).

      A leaf in the tree is represented by a key-value pair of the map where
      both key and value are the name of the reaction.

      if we have these dependencies:

      - :r0 -> [:a] [:b :c]
      - :r1 -> [:a :b]
      - :r2 -> []
      - :r3 -> [:a :c]

      We would conrstuct our tree this way:
      "

      (mkdn-pprint-source dependency-tree-example)
      "
      And get:
      "
      dependency-tree-example

      "
      We then have 2 functions to explore the tree.

      The first, `leaves` will give all the leaves (aka reaction names)
      in a dependency tree.
      "
      (mkdn-pprint-source flow/leaves)

      "
      The second will return only the leaves directly related to a node.
      "
      (mkdn-pprint-source flow/top-level-leaves)

      "
      We use this 2 functions to find all reactions watching a set of path
      as follow.

      1. We create a tree of the paths we are interested in, the structure is
      the same as the one for dependency trees withthe value `::sentinel` as leaves
      2. We compare the dependency tree containing the reactions with our sentinel
      tree by walking the 2 trees.

      `find-direct-dependencies` starts the process
      "
      (mkdn-pprint-source flow/find-direct-dependencies)

      "
      `paths->modification-tree` constructs our sentinel tree
      "
      (mkdn-pprint-source flow/paths->modification-tree)

      "
      `find-deps` walks both trees guided by the sentienl tree and collects
      the leaves (reaction names) of the dependency tree along the way.
      "
      (mkdn-pprint-source flow/find-deps)

      "
      the `sentinel?` function gives us our recurring condition in this tree walking
      "
      (mkdn-pprint-source flow/sentinel?)
      "

      ### Some tests
      "))


(deftest dependency-trees

  (testing "We can create a dependency tree from a path"
    (is (= (flow/path->dependency-tree {} [:a :b] :r)
           {:a {:b {:r :r}}}))
    (is (= dependency-tree-example
           {:a {:r0 :r0
                :b {:r1 :r1}
                :c {:r3 :r3}}
            :b {:c {:r0 :r0}}
            :r2 :r2})))


  (testing "If the path points to the whole db, every reaction is affected"
    (is (= (flow/find-direct-dependencies dependency-tree-example #{[]})
           #{:r1 :r2 :r3 :r0})))

  (testing
    "
    When given a path, we find the the reactions watching
    this path exactly, but also the reactions watching futher or before
    "
    (is (= (flow/find-direct-dependencies dependency-tree-example #{[:a]})
           #{:r0 :r1 :r2 :r3}))

    (is (= (flow/find-direct-dependencies dependency-tree-example #{[:a :b]})
           #{:r0 :r1 :r2}))

    (is (= (flow/find-direct-dependencies dependency-tree-example #{[:b]})
           #{:r0 :r2}))))

#?(:cljs
    (defcard-doc
      "
      ## Reaction dependencies and dependency graph

      ### Construction of the graph
      One of the dependency trees role is to help us figure out
      dependencies between reactions. The dependencies between reactions
      are store in the system under de key `:flow/reactions-dependencies`.
      We use a graph to strore these.

      This graph is updated everytime a reaction is added to a system
      with the help of the functions:
      "
      (mkdn-pprint-source flow/add-dependencies)
      (mkdn-pprint-source flow/add-dependent-reactions)

      "
      The first will compare the paths the added reaction looks at to figure out
      if these paths are registered as output to other reaction. In this case
      the added reaction depends on others.

      The second function compares the added reactions's output to see if
      other are watching it. In this case other reactions depend on the added one.

      Theses functions give us the opportunity to added reactions to a system without
      worrying about the order in which we add them.

      ### utilisation of the graph
      Since reactions can depend on one another, the order in which we excute
      them is important. I can't run reaction :r2 if it depends on the result
      of :r1 and :r1 hasn't been run yet.

      The dependency graph helps us with this since any topological sort
      of the dependency graph gives us a valid order in which to execute
      the reaction.

      Once all reactions are in a system, we can use the `ready-system` function
      "
      (mkdn-pprint-source flow/ready-system)

      "
      It uses a modified version of [this implementation](https://gist.github.com/alandipert/1263783])
      of the Khan algorithm.
      "
      (mkdn-pprint-source flow/topological-sort)

      "
      The result of the sort is added to the system under the key
      `:flow/reactions-order`
      "))


(def r1 {:name :r1
         :srcs {[:a :b] :b
                [:x :d] :d}})


(def r2 {:name :r2
         :srcs {[:c :d] :d}
         :dest [:x :d]})

(def r3 {:name :r3
         :srcs {[:e :f] :f}})

(def system (flow/add-reactions flow/empty-system [r3 r1 r2]))

#?(:cljs
    (defcard-doc
      "
      ###  Some tests
      Let's play with these 3 reactions.
      "
      (mkdn-pprint-source r1)
      (mkdn-pprint-source r2)
      (mkdn-pprint-source r3)
      (mkdn-pprint-source system)
      system))



(deftest reaction-dependencies
  (testing "The order doesn't matter."
    (is (= (-> flow/empty-system
               (flow/add-reaction r1)
               (flow/add-reaction r2)
               (flow/add-reaction r3))
           (-> flow/empty-system
               (flow/add-reaction r2)
               (flow/add-reaction r3)
               (flow/add-reaction r1))
           (flow/add-reactions flow/empty-system [r3 r1 r2]))))

  (testing "Destinations have attributed"
    (is (= (conj (::flow/default-output system) (:name r1))
           (get-in system [::flow/reactions-by-name (:name r1) :dest])))
    (is (= (conj (::flow/default-output system) (:name r3))
           (get-in system [::flow/reactions-by-name (:name r3) :dest]))))

  (testing "We have caught the dependency betxen :r1 an :2"
    (is (contains? (get-in system [::flow/reactions-dependencies :r2])
                   :r1)))

  (testing ":r2 will be excuted before :r1"
    (is (= (->> system
                flow/ready-system
                ::flow/reactions-order
                (some #{:r1 :r2}))
         :r2))))

#?(:cljs
    (defcard-doc
      "
      ## Putting it all together
      Reusing the [example](/devcards.html#!/flow.cards).
      "))

(deftest whole-system
  (testing "All reactions run on initial data."
    (is (= (:computed ex/db)
           {:tax-cost 100,
            :total-charges 100,
            :turnover-after-tax 900,
            :result 800})))

  (testing "After adding a charge."
    (is (= (:computed ex/db1)
           {:tax-cost 100,
            :total-charges 150,
            :turnover-after-tax 900,
            :result 750})))


  (testing "After changing a tax cost."
    (is (= (:computed ex/db2)
           {:tax-cost 100,
            :total-charges 250,
            :turnover-after-tax 900,
            :result 650})))

  (testing "The system selects which reactions to recompute"
    (is (= (flow/compute-affected-reactions ex/system #{[:charges/by-id 2]})
           #{:total-charges :result})))

  (testing "We can remove a reaction"
    (let [s (flow/remove-reaction ex/system :result)]
      (is (= (get-in s [::flow/reactions-by-name :result] ::gone)
             ::gone))

      (is (not (-> s ::flow/reaction-names (contains? :result))))

      (is (every? #(= ::gone %)
                  (let [deps (get s ::flow/data-dependencies)]
                    (map #(get-in deps (conj % :result) ::gone)
                         (-> ex/result :srcs keys)))))

      (is (= ::gone
             (let [o (::flow/reactions-outputs s)]
               (get-in o (conj (-> ex/result :dest) :result) ::gone))))

      (is (= ::gone
             (get-in s [::flow/reactions-dependencies :result] ::gone)))

      (is (not (contains? (-> s ::flow/reactions-dependencies vals set)
                          :result)))
      (is (not (contains? (-> s ::flow/reactions-order set)
                          :result))))))






