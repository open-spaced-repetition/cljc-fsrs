(ns open-spaced-repetition.cljc-fsrs.simulate
  (:require
   [open-spaced-repetition.cljc-fsrs.core :as core]
   [open-spaced-repetition.cljc-fsrs.log :as log]))

(defn simulate-repeats
  "Given a `card`, and a vector of `ratings`, simulate the spaced
  repetition of the card as if it went through those ratings on the due
  dates provided by the algorithm."
  [card ratings]
  (second
   (reduce (fn [[card history] rating]
             (let [card (core/repeat-card! card
                                           rating
                                           (:due card)
                                           core/default-params)]
               [card (conj history (log/record-log! card rating true))]))
           [card [card]]
           ratings)))
