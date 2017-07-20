(require '[figwheel-sidecar.repl-api :as ra])



(ra/start-figwheel!) ;; <-- fetches configuration

(ra/switch-to-build "devcards")


(ra/cljs-repl)