# flow

This library provides a simple dataflow engine. It is created with
om-next's default db format in mind. However since this db format is
just Clojure data...


## Usage
Here is part of the example used in the devcards.

You have a db that looks like this:
```clojure
(def init-data
  {:parameters {:turnover 1000}
   :charges [[:charges/by-id 1]]
   :charges/by-id {1 {:id 1 :name "Marketing" :cost 100}}})
```

You want to have reactions that sums the charges and compute what is the of you turnover
after these charges.

You start by defining reactions:

```clojure
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
```

Then you build a dataflow system:

```clojure
(require '[flow.core :as flow]

(def system
  (-> flow/empty-system
      (flow/add-reactions [total-charges  result])
      (flow/ready-system)))
```

and use it

```clojure
(def db (flow/execute-system init-data system #{flow/whole-db}))

db
;=>
{:parameters {:turnover 1000},
 :charges [[:charges/by-id 1]],
 :charges/by-id {1 {:id 1, :name "Marketing", :cost 100}},
 :computed {:total-charges 100, :result 900}}

(def db2
  (-> db
      (assoc-in [:charges/by-id 2] {:id 2 :name "travel expenses" :cost 200})
      (flow/execute-system system #{[:charges/by-id 2]})))

db2
;=>
{:parameters {:turnover 1000},
 :charges [[:charges/by-id 1]],
 :charges/by-id
 {1 {:id 1, :name "Marketing", :cost 100},
  2 {:id 2, :name "travel expenses", :cost 200}},
 :computed {:total-charges 300, :result 700}}
```


The `execute-system` function takes 3 arguments:
- a db
- a system
- a set of paths indicating where the change happened so that only
the relevant reactions are run.

There is more information in the devcards.

## Running devcards
Clone this repo then:

```
lein run -m clojure.main scripts/repl.clj
```

Open [this link](http://localhost:3449/devcards.html).


## License

Copyright © 2017 Jérémy Schoffen

Distributed under the Eclipse Public License (see LICENSE).
