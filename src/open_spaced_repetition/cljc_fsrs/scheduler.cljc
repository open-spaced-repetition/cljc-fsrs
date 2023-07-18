(ns open-spaced-repetition.cljc-fsrs.scheduler
  (:require [open-spaced-repetition.cljc-fsrs.parameters :as parameters]
            [tick.core :as t]))

;;; # Data-structures and functions for card scheduling
;; The Schedule for a card is the internal representation of the card.
;; We build it every time we repeat a card, and calculate future
;; values for all the possible state transitions based on user input.
;; This way, when the user provides a rating, we already have all the
;; information we need to store.

(defn empty-schedule
  "Return a new `schedule`. This is our placeholder for parameter
  predictions."
  [card]
  (reduce (fn [schedule rating] (assoc schedule rating card))
          {}
          (keys parameters/->rating)))

(defn update-schedule-difficulty-stability
  "Given a `schedule` and the associated `card`, find the new values of
  the `:difficulty` and `:stability` metrics for any rating that the
  card gets."
  [schedule {:keys [elapsed-days difficulty stability] :as card} {:keys [weights]}]
  (case (:state card)
    ;; :new state means that we have to initialize the schedule with
    ;; default values.
    :new
    (-> schedule
        (assoc-in [:again :difficulty]
                  (parameters/init-difficulty weights :again))
        (assoc-in [:hard  :difficulty]
                  (parameters/init-difficulty weights :hard))
        (assoc-in [:good  :difficulty]
                  (parameters/init-difficulty weights :good))
        (assoc-in [:easy  :difficulty]
                  (parameters/init-difficulty weights :easy))
        (assoc-in [:again :stability]
                  (parameters/init-stability weights :again))
        (assoc-in [:hard  :stability]
                  (parameters/init-stability weights :hard))
        (assoc-in [:good  :stability]
                  (parameters/init-stability weights :good))
        (assoc-in [:easy  :stability]
                  (parameters/init-stability weights :easy)))

    ;; no change to difficulty or stability in :learning or
    ;; :relearning card states
    (:learning :relearning)
    schedule

    ;; :review state is where D, S, R calculations actually come into play.
    :review
    (let [retrievability (parameters/calculate-retrievability elapsed-days
                                                              stability)]
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
                                               :easy weights))))))

(defn update-schedule-state
  "Update the `:state` field in the `schedule` of the `card` by
  transitioning it through our state machine for every possible rating."
  [schedule card]
  (case (:state card)
    ;; :new means this is the first time we are studying the card
    :new
    (-> schedule
        ;; For all ratings other than :easy, :new moves into :learning.
        ;; For :easy, :new moves directly into :review
        (assoc-in [:again :state] :learning)
        (assoc-in [:hard :state] :learning)
        (assoc-in [:good :state] :learning)
        (assoc-in [:easy :state] :review))

    ;; :learning or :relearning means we have not yet memorised the
    ;; card, or we have forgotten the card.
    (:learning, :relearning)
    (-> schedule
        ;; A rating of :again or :hard does not change the state,
        ;; since we have still not learnt the material For :good or
        ;; :easy ratings, we move the card to :review state
        (assoc-in [:good :state] :review)
        (assoc-in [:easy :state] :review))

    ;; :review means that we have completely memorised the card
    ;; previously, and we are testing if our memory has decayed
    :review
    (-> schedule
        ;; A rating of :again means that we have forgotten the card :(
        ;; We need to :relearn it. All other ratings are excellent, we
        ;; stay in the same space.
        (assoc-in [:again :state] :relearning))))

(defn update-schedule-lapses "`:again` means we've forgotten the card, count it"
  [schedule]
  (update-in schedule [:again :lapses] inc))

(defn update-schedule-due-scheduled-days
  "Update the `:due` and `:scheduled-days` fields in the `schedule` of
  the `card` by transitioning it through our state machine for every
  possible rating."
  [schedule card now params]
  (let [good-interval (parameters/next-interval
                       params
                       (get-in schedule [:good :stability]))
        hard-interval (min (parameters/next-interval
                            params
                            (get-in schedule [:hard :stability]))
                           good-interval)
        good-interval (max good-interval (inc hard-interval))
        easy-interval (max (inc good-interval)
                           (parameters/next-interval
                            params
                            (get-in schedule [:easy :stability])))]
    (case (:state card)
      :new
      (-> schedule
          (assoc-in [:again :due] (t/>> now (t/new-duration 1 :minutes)))
          (assoc-in [:hard  :due] (t/>> now (t/new-duration 5 :minutes)))
          (assoc-in [:good  :due] (t/>> now (t/new-period good-interval :days)))
          (assoc-in [:easy  :due] (t/>> now (t/new-period easy-interval :days)))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval))

      (:learning :relearning)
      (-> schedule
          (assoc-in [:again :due] (t/>> now (t/new-duration 5  :minutes)))
          (assoc-in [:hard  :due] (t/>> now (t/new-duration 10 :minutes)))
          (assoc-in [:good  :due] (t/>> now (t/new-period good-interval :days)))
          (assoc-in [:easy  :due] (t/>> now (t/new-period easy-interval :days)))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval))

      :review
      (-> schedule
          (assoc-in [:again :due] (t/>> now (t/new-duration 5 :minutes)))
          (assoc-in [:hard  :due] (t/>> now (t/new-period hard-interval :days)))
          (assoc-in [:good  :due] (t/>> now (t/new-period good-interval :days)))
          (assoc-in [:easy  :due] (t/>> now (t/new-period easy-interval :days)))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] hard-interval)
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval)))))

;;; # The External Interface of the schedule module.
;; We need a `new-schedule` give a card: a prediction of all the
;; states the given card can transition into due to a spaced
;; repetition.
(defn new-schedule
  "Given a `card`, make a `schedule` for the card which can be used to
  predict the next transition."
  [card repeat-time-instant params]
  (-> (empty-schedule card)
      (update-schedule-difficulty-stability card params)
      (update-schedule-state card)
      update-schedule-lapses
      (update-schedule-due-scheduled-days card repeat-time-instant params)))
