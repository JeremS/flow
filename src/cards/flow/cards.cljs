(ns flow.cards
  (:require
    [flow.core :as flow]
    [flow.example :as ex]
    [devcards.core :as dc]
    [flow.core-test])
  (:require-macros
    [devcards.core :refer [defcard defcard-doc mkdn-pprint-source]]))



(def example-reaction
  {:name :reaction1
   :srcs {[:a :b] :arg1
          [:a :c] :arg2}
   :dest [:computed :d1]
   :fn (fn [{:keys [arg1 arg2]}]
         :a-result)})


(defcard-doc
  "
  # The flow system
  The flow system aims to provide derived values stored directly
  in the om next App DB."

  "To do so we define a system which contains reactions.

  ## Anatomy of a reaction.
  A reaction is a map clojure such as this:"
  (dc/mkdn-pprint-source example-reaction)
  "
  A system will use these keys for different purposes:

  - `:name` -> a name for the reaction that will be used as a unique ID for this reaction.
  - `:srcs` -> a map of paths to a name. This map allows to specify which part of the app DB the reaction
  is interested in, a give a name to each part.
  - `:dest` -> a path at which the reaction will store it's result in the app DB
  - `:fn` -> The actual code for the reaction. Note how the function takes only one parameter.
  This parameter is a map of names to actual data from the DB. What goes in this map is defined by the
  `:srcs` key of the reaction.
  "

  "
  ## Anatomy of a system

  Here is the empty system we will build up by adding reactions to it:
  "
  (mkdn-pprint-source flow/empty-system)

  "
  ## Example

  Let's try with a simplified accounting example.

  ### The DB
  Here is the initial value of our database.
  We store our turnover, a percentage that will be taken from the
  turnover before charges and a set of charges to be retracted.
  "
  (mkdn-pprint-source ex/init-data)

  "
  ### Definition of the reactions

  We define a first reaction that will compute the cost of the tax
  "
  (mkdn-pprint-source ex/compute-tax-cost)
  (mkdn-pprint-source ex/tax-cost)

  "
  This reaction will compute what is left of the turnover after taxes
  "
  (mkdn-pprint-source ex/compute-turnover-after-tax)
  (mkdn-pprint-source ex/turnover-after-tax)

  "
  We can define the total cost of the charges
  "
  (mkdn-pprint-source ex/compute-total-charges)
  (mkdn-pprint-source ex/total-charges)

  "
  Finally, the result, the money that is left
  "
  (mkdn-pprint-source ex/compute-result)
  (mkdn-pprint-source ex/result)

  "
  ### Setting up a system
  Here we setup our system by adding our reactions to it, the
  order doesn't matter.
  When we add a reaction to a system, reaction dependencies will
  be updated. These dependencies are used by our `ready-system`
  function to compute the order in which reactions should be run.
  "
  (mkdn-pprint-source ex/system)

  "
  We can now start by running all reactions.
  "
  (mkdn-pprint-source ex/db)

  "The third parameter to the `execute-system` function is a set
  of path. Here we give it only one empty path
  ```clojure
  #{flow/whole-db}
  =>
  #{[]}
  ```

  This basically tells to execute all reactions.
  We then get this nex db
  "
  ex/db
  "
  in which the reactions have been computed.

  We can now add a new charge in our budget and run the system.
  "
  (mkdn-pprint-source ex/db1)

  "
  And get the new db
  "
  ex/db1
  "
  We can see that the reactions `:total-charges` and `:result` have been updated.


  More, all reaction haven't been run this time since we indicated
  that only the reactions observing the path [:charges/by-id]
  had to be updated. The `execute-system` function use the
  `compute-affected-reactions` function to determine which reaction
  need to be run based on paths.


  ```clojure
  (flow/compute-affected-reactions system #{[:charges/by-id]})
  ```
  "
  (flow/compute-affected-reactions ex/system #{[:charges/by-id]})

  "
  Even though we don't have a reactions watching for the specific
  path `[:charges/by-id 2]`
  "
  (mkdn-pprint-source ex/db2)
  ex/db2

  "
  we can see that the charges and result have been recomputed on this path
  since the reaction `:total-charges` source `[:charges/by-id]` implicitely
  contains the path `[:charges/by-id 2]`.

  ### Next
  You cant learn more on the internals [there](/devcards.html#!/flow.core_test).
  ")







