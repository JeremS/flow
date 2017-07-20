(defproject flow "0.1.0-alpha1"
  :description "Little dataflow engine"
  :url "https://github.com/JeremS/flow"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.671"]]


  :source-paths ["src/main"]

  :plugins [[lein-cljsbuild "1.1.6"]]

  :clean-targets ^{:protect false} ["resources/public/js/"
                                    "target"]
  :target-path "target/%s"

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.10"]
                                  [binaryage/devtools "0.9.4"]
                                  [devcards "0.2.3"]]
                   :source-paths ["src/cards" "test"]}}

  :cljsbuild {:builds [{:id           "devcards"
                        :source-paths ["src" "test" "scripts"]
                        :figwheel     { :devcards true}
                        :compiler     {:main "flow.cards"
                                       :asset-path "js/devcards_out"
                                       :output-to "resources/public/js/devcards.js"
                                       :output-dir "resources/public/js/devcards_out"
                                       :optimizations :none
                                       :preloads [devtools.preload]
                                       :external-config
                                       {:devtools/config
                                        {:features-to-install [:formatters :hints]}}}}]})


