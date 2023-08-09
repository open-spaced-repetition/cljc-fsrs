(ns user
  (:require
   [clojure.spec.alpha :as s]
   [nextjournal.clerk :as clerk]))

(clerk/serve! {:browse? true})
(s/check-asserts true)

(comment
  ;; set! 'cant be run in a non-binding thread', whatever that means.
  ;; This needs to be executed by hand.
  (set! *warn-on-reflection* true))
