(ns open-spaced-repetition.cljc-fsrs.core
  #:nextjournal.clerk{:visibility {:code :show, :result :show}, :toc true}
  (:require
   [open-spaced-repetition.cljc-fsrs.card :as card]
   [open-spaced-repetition.cljc-fsrs.parameters :as parameters]
   [tick.core :as t]))

;;; # Default Parameters
;; The `default-params` are global params that we start the world
;; with. For an explanation of the parameters, refer:
;; https://github.com/open-spaced-repetition/fsrs4anki/wiki/Free-Spaced-Repetition-Scheduler
(def default-params parameters/default-params)

(defn new-card! "Return a brand new empty card, with empty values"
  []
  (card/new-card! (t/now)))

;;; # Card Operations: The Primary API of our spacing library
;; We are surfacing a card for review! Update the internal state of
;; the card for all possible ratings that the user may provide. This
;; means that recording the rating of the user is as simple as picking
;; the right future from our prediction. This may look like work
;; upfront, but its done because it simplifies state management (I
;; think)
(defn repeat-card!
  "We have repeated the `card` according to previous instructions. We
  have a new `rating` for the card. Generate the new state of the card
  after the rating."
  ([card rating]
   (repeat-card! card rating default-params))
  ([card rating params]
   (parameters/assert-rating rating)
   (parameters/assert-weights (:weights params))
   (repeat-card! card rating (t/now) params))
  ;; This arity should be considered private. It's helpful to be able
  ;; to control time during tests.
  ([card rating repeat-time-instant params]
   (-> card
       (card/repeat-card! repeat-time-instant params)
       rating)))
