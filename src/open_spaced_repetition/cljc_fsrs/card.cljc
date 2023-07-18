(ns open-spaced-repetition.cljc-fsrs.card
  (:require
   [open-spaced-repetition.cljc-fsrs.scheduler :as scheduler]
   [tick.core :as t]))

;;; # Data-structures and functions for creating and repeating cards
(defn new-repetition-elapsed-days
  "How long has it been since we repeated this card?"
  [card repeat-time-instant]
  (->> repeat-time-instant
       (t/between (:last-repeat card))
       t/days
       (assoc card :elapsed-days)))

(defn new-repetition-time
  "Record `repeat-time-instant` as the most recent repeat of the card."
  [card repeat-time-instant]
  (assoc card :last-repeat repeat-time-instant))

(defn update-reps "Update the total number of times this card has been repeated"
  [card]
  (update card :reps inc))

;;; # The External Interface of the card module
;; We only need two functions: `new-card!` to create a new card
;; datastructure, and `repeat-card!` to take an existing card and
;; transition it through a spaced repetition.
(defn repeat-card!
  "Repeat this card. Return the `scheduler` datastructure for every
  possible future state of this card."
  [card repeat-time-instant params]
  (-> card
      update-reps
      (new-repetition-elapsed-days repeat-time-instant)
      (new-repetition-time repeat-time-instant)
      (scheduler/new-schedule repeat-time-instant params)))

(defn new-card! "Return a brand new empty card, with empty values"
  [creation-time-instant]
  {:due creation-time-instant
   :stability 0
   :difficulty 0
   :elapsed-days 0
   :scheduled-days 0
   :reps 0
   :lapses 0
   :state :new
   :last-repeat creation-time-instant})
