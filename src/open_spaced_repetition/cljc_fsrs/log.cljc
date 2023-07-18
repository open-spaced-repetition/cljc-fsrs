(ns open-spaced-repetition.cljc-fsrs.log)

;;; @TODO: What is the point of this log? It does not capture anything
;;; that the card can't tell better, other than the actual rating.
(defn record-log
  "Create a log of the `repeat-card!` activity. This should be shared
  back with `open-spaced-repetition` for improving the algorithm."
  [card rating]
  {:rating rating
   :scheduled-days (:scheduled-days card)
   :elapsed-days (:elapsed-days card)
   :repeat (:last-repeat card)
   :state (:state card)})
