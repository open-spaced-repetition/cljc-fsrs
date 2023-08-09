(ns open-spaced-repetition.cljc-fsrs.scheduler
  #:nextjournal.clerk{:visibility {:code :show, :result :show}, :toc true}
  (:require [open-spaced-repetition.cljc-fsrs.parameters :as parameters]
            [tick.core :as t]))

;;; # Data-structures and functions for card scheduling
;; The `schedule` for the card is the data-structure that calculates
;; the next repetition of the card, based on how the user interacts
;; with the card this time. Since these calculations are pure
;; functions, we calculate all future values for the given card
;; (values for all the ratings that the user can give to the current
;; card). This way, when the user provides a rating, we already have
;; all the information about the next repeat schedule.

(defn empty-schedule "This is our placeholder for DSR parameter predictions."
  [card]
  (reduce (fn [schedule rating] (assoc schedule rating card))
          {}
          (keys parameters/->rating)))

;;; # Calculating lapses
;; For any `card`, a rating of `:again` means that we've forgotten the
;; card. We record this information for statistics later.
(defn calculate-lapses "`:again` means we've forgotten the card, count it"
  [schedule]
  (update-in schedule [:again :lapses] inc))

;;; # Calculating new values for State
;; For a `card` in any state, we can calculate it's transitions for
;; all the ratings that a user might give the card.

(defmulti calculate-state "What should the next `:state` of the card be?"
  (fn [_schedule card] (:state card)))

;;; ## When the previous state of the card is `:new`
;; `:new` means this is the first time we are studying the card.
(defmethod calculate-state :new
  [schedule _card]
  (-> schedule
      ;; For all ratings other than `:easy`, `:new` moves into
      ;; `:learning`. For `:easy`, `:new` moves directly into
      ;; `:review`
      (assoc-in [:again :state] :learning)
      (assoc-in [:hard  :state] :learning)
      (assoc-in [:good  :state] :learning)
      (assoc-in [:easy  :state] :review)))

;;; ## When the previous state of the card is `:learning` or `:relearning`
;; `:learning` or `:relearning` means we have not yet memorised the
;; card, or we have forgotten the card.
(defn state-change-when-learning
  [schedule]
  (-> schedule
      ;; A rating of `:again` or `:hard` does not change the state,
      ;; since we have still not learnt the material. For `:good` or
      ;; `:easy` ratings, we move the card to `:review` state.
      (assoc-in [:good :state] :review)
      (assoc-in [:easy :state] :review)))

(defmethod calculate-state :learning
  [schedule _card]
  (state-change-when-learning schedule))

(defmethod calculate-state :relearning
  [schedule _card]
  (state-change-when-learning schedule))

;;; ## When the previous state of the card is `:review`
;; `:review` means that we have completely memorised the card
;; previously, and we are testing if our memory has decayed
(defmethod calculate-state :review
  [schedule _card]
  (-> schedule
      ;; A rating of `:again` means that we have forgotten the card :(
      ;; We need to `:relearn` it. All other ratings are excellent, we
      ;; stay in the same space.
      (assoc-in [:again :state] :relearning)))

;;; # Calculating new values for Difficulty, Stability and Retrievability (DSR)
;; Given a `card` and the `params` used in the FSRS algorithm, we can
;; calculate the new DSR values. The formulae for these calculations
;; depend on the state of the card. Let's see what the different
;; transitions look like:

(defmulti calculate-difficulty-stability "What should be the next DS values?"
  (fn [_schedule card _params] (:state card)))

;;; ## DSR values for a `:new` card
;; For a card that we have just created, FSRS defines initial values
;; for DS depending on whether the user finds the card `:easy`,
;; `:good`, `:hard` or `:again`. Note that there is no preconceived
;; difficulty value that we associate with a new card when we generate
;; one.
(defmethod calculate-difficulty-stability :new
  [schedule _card {:keys [weights]}]
  (-> schedule
      (assoc-in [:again :difficulty] (parameters/init-difficulty weights :again))
      (assoc-in [:hard  :difficulty] (parameters/init-difficulty weights :hard))
      (assoc-in [:good  :difficulty] (parameters/init-difficulty weights :good))
      (assoc-in [:easy  :difficulty] (parameters/init-difficulty weights :easy))

      (assoc-in [:again :stability] (parameters/init-stability weights :again))
      (assoc-in [:hard  :stability] (parameters/init-stability weights :hard))
      (assoc-in [:good  :stability] (parameters/init-stability weights :good))
      (assoc-in [:easy  :stability] (parameters/init-stability weights :easy))))

;;; ## DSR values for `:learning` and `:relearning` cards
;; The FSRS algorithm does not update the DSR value of the card when
;; the card is in `:learning` or `:relearning` state. We do not expect
;; to stay in this state for a long time either.
(defmethod calculate-difficulty-stability :learning
  [schedule _card _params] schedule)
(defmethod calculate-difficulty-stability :relearning
  [schedule _card _params] schedule)

;;; ## DSR values for `:review` cards
;; Cards in `:review` state are where the DSR calculations actually
;; play a big role. `:review` here means that we have committed the
;; card to memory and are now predicting DSR values to ensure that
;; retention of this card remains above 90%.
(defmethod calculate-difficulty-stability :review
  [schedule {:keys [elapsed-days difficulty stability]} {:keys [weights]}]
  (let [retrievability (parameters/calculate-retrievability elapsed-days stability)]
    (-> schedule
        (assoc-in [:again :difficulty]
                  (parameters/next-difficulty difficulty :again weights))
        (assoc-in [:hard  :difficulty]
                  (parameters/next-difficulty difficulty :hard weights))
        (assoc-in [:good  :difficulty]
                  (parameters/next-difficulty difficulty :good weights))
        (assoc-in [:easy  :difficulty]
                  (parameters/next-difficulty difficulty :easy weights))
        (assoc-in [:again :stability]
                  (parameters/next-stability difficulty
                                             stability
                                             retrievability
                                             :again weights))

        (assoc-in [:hard  :stability]
                  (parameters/next-stability difficulty
                                             stability
                                             retrievability
                                             :hard weights))
        (assoc-in [:good  :stability]
                  (parameters/next-stability difficulty
                                             stability
                                             retrievability
                                             :good weights))
        (assoc-in [:easy  :stability]
                  (parameters/next-stability difficulty
                                             stability
                                             retrievability
                                             :easy weights)))))

;;; # Calculating the next due dates
;; Based on how the user rates the `card`, the FSRS algorithm decides
;; the optimal time when we should repeat the card. We calculate this
;; information so that we can record it after the user gives us a
;; rating.
(defmulti calculate-due "What's the best next time to repeat this card?"
  (fn [_schedule card _now _params] (:state card)))

;;; ## `:due` and `:scheduled-days` values for a card in `:new` state
(defmethod calculate-due :new
  [schedule _card now params]
  (let [easy-interval (parameters/next-interval
                       params
                       (get-in schedule [:easy :stability]))]
    (-> schedule
        (assoc-in [:again :due] (t/>> now (t/new-duration 1 :minutes)))
        (assoc-in [:hard  :due] (t/>> now (t/new-duration 5 :minutes)))
        (assoc-in [:good  :due] (t/>> now (t/new-duration 10 :minutes)))
        (assoc-in [:easy  :due] (t/>> now (t/new-period easy-interval :days)))
        (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
        (assoc-in [:hard  :scheduled-days] 0) ; Since due is in minutes
        (assoc-in [:good  :scheduled-days] 0) ; Since due is in minutes
        (assoc-in [:easy  :scheduled-days] easy-interval))))

;;; ## `:due` and `:scheduled-days` values for `:learning`,`:relearning` states
(defn due-change-when-learning
  [schedule now params]
  (let [good-interval (parameters/next-interval
                       params
                       (get-in schedule [:good :stability]))
          easy-interval (max (inc good-interval)
                             (parameters/next-interval
                              params
                              (get-in schedule [:easy :stability])))]
      (-> schedule
          (assoc-in [:again :due] (t/>> now (t/new-duration 5  :minutes)))
          (assoc-in [:hard  :due] (t/>> now (t/new-duration 10 :minutes)))
          (assoc-in [:good  :due] (t/>> now (t/new-period good-interval :days)))
          (assoc-in [:easy  :due] (t/>> now (t/new-period easy-interval :days)))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval))))

(defmethod calculate-due :learning
  [schedule _card now params]
  (due-change-when-learning schedule now params))

(defmethod calculate-due :relearning
  [schedule _card now params]
  (due-change-when-learning schedule now params))

;;; ## `:due` and `:scheduled-days` values for `:review` state
(defmethod calculate-due :review
  [schedule _card now params]
  (let [hard-interval (parameters/next-interval
                       params
                       (get-in schedule [:hard :stability]))
        good-interval (parameters/next-interval
                       params
                       (get-in schedule [:good :stability]))
        hard-interval (min hard-interval good-interval)
        good-interval (max good-interval (inc hard-interval))
        easy-interval (max (parameters/next-interval
                            params
                            (get-in schedule [:easy :stability]))
                           (inc good-interval))]
    (-> schedule
        (assoc-in [:again :due] (t/>> now (t/new-duration 5 :minutes)))
        (assoc-in [:hard  :due] (t/>> now (t/new-period hard-interval :days)))
        (assoc-in [:good  :due] (t/>> now (t/new-period good-interval :days)))
        (assoc-in [:easy  :due] (t/>> now (t/new-period easy-interval :days)))
        (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
        (assoc-in [:hard  :scheduled-days] hard-interval)
        (assoc-in [:good  :scheduled-days] good-interval)
        (assoc-in [:easy  :scheduled-days] easy-interval))))

;;; # The External Interface of the schedule module.
;; `next-repeat-schedule`: What should be the next time we schedule
;; this card for study? This function calculates the DSR values for
;; this card for all the possible ratings that the user can give. This
;; lets us choose the right schedule for repeating the card based on
;; user input.
(defn next-repeat-schedule "When should we repeat this card next?"
  [card repeat-time-instant params]
  (-> (empty-schedule card)
      calculate-lapses
      (calculate-state card)
      (calculate-difficulty-stability card params)
      (calculate-due card repeat-time-instant params)))
