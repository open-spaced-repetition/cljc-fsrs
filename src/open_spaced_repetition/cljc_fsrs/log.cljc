(ns open-spaced-repetition.cljc-fsrs.log)

;;; `record-log` when `testing?` is false is the minimum data
;;; collection that we can submit to the open spaced repetition team
;;; for refining the weights that they design. The `testing?`
;;; parameter is something I've added to make this useful in my tests
;;; and to observe the change of variables over repeats.
(defn record-log!
  "Create a log of the `repeat-card!` activity. This should be shared
  back with `open-spaced-repetition` for improving the algorithm."
  [card rating testing?]
  (let [log {:rating rating
             :scheduled-days (:scheduled-days card)
             :elapsed-days (:elapsed-days card)
             :repeat (:last-repeat card)
             :state (:state card)}]
    (if testing? (assoc card :rating rating) log)))
