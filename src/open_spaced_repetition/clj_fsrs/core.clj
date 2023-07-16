(ns open-spaced-repetition.clj-fsrs.core
  #:nextjournal.clerk{:visibility {:code :show, :result :show}, :toc true}
  (:import
   (java.time Instant LocalDate Period ZoneId)
   (java.time.temporal ChronoUnit)))

;;; # Default Parameters
(defn calculate-interval-modifier
  [request-retention]
  (* 9 (- (/ 1 request-retention) 1)))

;; The `default-params` are global params that we start the world
;; with. For an explanation of the parameters, refer:
;; https://github.com/open-spaced-repetition/fsrs4anki/wiki/Free-Spaced-Repetition-Scheduler
(def default-params "The default parameters we use with FSRS."
  {
   :weights [1,   ;; Weight for :again rating
             2,   ;; Weight for :hard rating
             3,   ;; Weight for :good rating
             4,   ;; Weight for :easy rating
             5,
             0.5,
             0.5,
             0.2,
             1.4,
             0.2,
             0.8,
             2,
             0.2,
             0.2,
             1,
             0.5, ;; Hard Penalty
             2    ;; Easy Bonus
             ]
   :request-retention 0.9 ;; Retention is the percentage of your
                          ;; successful recall. `request-retention` is
                          ;; a number between 0 and 1 which defines
                          ;; the retention percentage we are
                          ;; targeting.
   :maximum-interval  36500 ;; The interval of time before
                            ;; re-reviewing the card, in days
   :interval-modifier (calculate-interval-modifier 0.9)
   })

;;; # Card Ratings and States
;; There are four possible ratings that we assign to a card on every
;; review. This code maps the human-readable rating to it's index in
;; the provided weights, and explains the meaning of each rating:
(def ->rating "Mapping from rating keyword to it's index in the `weights`"
  ;; NOTE: This is strictly an implementation detail, use it only as such
  {
   :again 0 ;; We got the answer wrong. Automatically means that we
            ;; have forgotten the card. This is a lapse in memory.
   :hard 1  ;; The answer was only partially correct and/or we took
            ;; too long to recall it.
   :good 2  ;; The answer was correct but we were not confident about it.
   :easy 3  ;; The answer was correct and we were confident and quick
            ;; in our recall.
   })

;; A card can be in four possible states, the meaning of which are
;; explained in the code below. The card is always in :new state when
;; it is created, and never returns to :new state after the first
;; review.
(def state "The state of the card we are studying."
  #{
    :new        ;; We have just created the card. We hope to remember
                ;; it the next time we see it.
    :learning   ;; We haven't memorised the card yet, but have
                ;; reviewed it at least once.
    :review     ;; We have memorised the card! This is the ideal state
                ;; we want all cards to be in forever. Alas for memory
                ;; decay!
    :relearning ;; We have forgotten the card. This is the same state
                ;; as :learning, expect it signifies that we've
                ;; arrived from :review instead of :new
    })

;;; # Parameter Calculations
(defn assert-weights "Ensure that weights are meaningful"
  [weights]
  (assert (= (count weights) (count (:weights default-params)))
          (format "Given weights %s do not have enough parameters." weights)))

(defn assert-rating "Ensure that rating is valid"
  [rating]
  (assert (->rating rating) (format "Given rating %s does not exist" rating)))

(defn constrain-difficulty "Keep difficulty between 1 and 10"
  [difficulty]
  (-> difficulty (max 1) (min 10)))

(defn mean-reversion "Ensure that we do not get stuck in easy hell"
  [current-difficulty weights]
  (+ (* (nth weights 7) (nth weights 4))
     (* (- 1 (nth weights 7)) current-difficulty)))

(defn init-difficulty "Give the initial value of difficulty for given `rating`"
  [weights rating]
  (assert-weights weights)
  (assert-rating rating)
  (- (nth weights 4)
     (* (- (nth weights (->rating rating)) 3)
        (nth weights 5))))

(defn init-stability "Give the initial value of stability for given `rating`"
  [weights rating]
  (assert-weights weights)
  (assert-rating rating)
  (max 0.1 (nth weights (->rating rating))))

(defn next-interval "Given the `stability` of item, when should we revisit it?"
  [{:keys [maximum-interval interval-modifier]} stability]
  (let [new-interval (* stability interval-modifier)]
    ;; @TODO: Apply Fuzzying to the `new-interval`
    (min maximum-interval (max 1 (Math/round new-interval)))))

(defn next-difficulty "Given `difficulty` and `rating`, calculate the new diff"
  [difficulty rating weights]
  (let [new-diff (- difficulty
                    (* (nth weights 6)
                       (- (nth weights (->rating rating)) 3)))]
    (constrain-difficulty (mean-reversion new-diff weights))))

(defn calculate-retrievability "Calculate decay in recall over time"
  [elapsed-days stability]
  (Math/pow (+ 1 (/ elapsed-days (* stability 9)))
            -1))

(defn next-stability "Given current D, S, R and rating, find the new stability"
  [difficulty stability retrievability rating weights]
  (let [recall-factor (* (Math/exp (nth weights 8))
                         (- 11 difficulty)
                         (Math/pow stability (- (nth weights 9)))
                         (- (Math/exp (* (nth weights 10)
                                         (- 1 retrievability)))
                            1))]
    (case rating
      :again
      (min (* (nth weights 11)
              (Math/pow difficulty (- (nth weights 12)))
              (- (Math/pow (+ 1 stability) (nth weights 13)) 1)
              (Math/exp (* (nth weights 14) (- 1 retrievability))))
           stability)

      :hard
      ;; Multiple the recall-factor with the hard-penalty
      (* stability (+ 1 (* recall-factor (nth weights 15))))

      :good
      (* stability (+ 1 recall-factor))

      :easy
      ;; Multiply the recall-factor with the easy-bonus boost
      (* stability (+ 1 (* recall-factor (nth weights 16)))))))

;;; # Data-structures for card scheduling
(defn new-card! "Return a brand new empty card, with empty values"
  []
  (let [now (Instant/now)]
    {:due now
     :stability 0
     :difficulty 0
     :elapsed-days 0
     :scheduled-days 0
     :reps 0
     :lapses 0
     :state :new
     :last-review now}))

;; The Schedule for a card is the internal representation of the card.
;; We build it every time we surface a card for review, and calculate
;; future values for all the possible state transitions based on user
;; input. This way, when the user provides a rating, we already have
;; all the information we need to store.

(defn empty-schedule
  "Given a `card`, return a new `schedule` that we can fill out for
  review predictions."
  [card]
  (reduce (fn [schedule rating] (assoc schedule rating card))
          {}
          (keys ->rating)))

;;; ## Initializing the schedule when creating a new card
;; When we have a :new card, we also know the following values
;; automatically for any rating the user might give, based on the
;; `default-params`:
;; 1. :difficulty (dependent on initial `weights`)
;; 2. :stability  (dependent on initial `weights`)
;; 3. :due (dependent on `easy-bonus` for `:easy` rating)
(defn update-schedule-difficulty-stability
  "Given a `schedule` and the associated `card`, find the new values of
  the `:difficulty` and `:stability` metrics for any review that the
  card gets."
  [schedule {:keys [elapsed-days difficulty stability] :as card} {:keys [weights]}]
  (case (:state card)
    ;; :new state means that we have to initialize the schedule with
    ;; default values.
    :new
    (-> schedule
        (assoc-in [:again :difficulty] (init-difficulty weights :again))
        (assoc-in [:hard  :difficulty] (init-difficulty weights :hard))
        (assoc-in [:good  :difficulty] (init-difficulty weights :good))
        (assoc-in [:easy  :difficulty] (init-difficulty weights :easy))
        (assoc-in [:again :stability] (init-stability weights :again))
        (assoc-in [:hard  :stability] (init-stability weights :hard))
        (assoc-in [:good  :stability] (init-stability weights :good))
        (assoc-in [:easy  :stability] (init-stability weights :easy)))

    ;; no change to difficulty or stability in :learning or
    ;; :relearning card states
    (:learning :relearning)
    schedule

    ;; :review state is where D, S, R calculations actually come into play.
    :review
    (let [retrievability (calculate-retrievability elapsed-days stability)]
      (-> schedule
          (assoc-in [:again :difficulty]
                    (next-difficulty difficulty :again weights))
          (assoc-in [:hard  :difficulty]
                    (next-difficulty difficulty :hard weights))
          (assoc-in [:good  :difficulty]
                    (next-difficulty difficulty :good weights))
          (assoc-in [:easy  :difficulty]
                    (next-difficulty difficulty :easy weights))
          (assoc-in [:again :stability]
                    (next-stability difficulty stability retrievability
                                    :again weights))
          (assoc-in [:hard  :stability]
                    (next-stability difficulty stability retrievability
                                    :hard weights))
          (assoc-in [:good  :stability]
                    (next-stability difficulty stability retrievability
                                    :good weights))
          (assoc-in [:easy  :stability]
                    (next-stability difficulty stability retrievability
                                    :easy weights))))))

(defn update-schedule-state
  "Update the `:state` field in the `schedule` of the `card` by
  transitioning it through our state machine for every possible rating."
  [schedule card]
  (case (:state card)
    ;; :new means this is the first time we are reviewing the card
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

;; For any schedule, a rating of :again means that we have forgotten
;; the card (regardless of the current state of the card). So we
;; should increment the :again state to reflect this. @TODO: The fact
;; that this function does not depend on anything external at all
;; means that it can be removed. Do it in a refactor later.

(defn update-schedule-lapses
  [schedule]
  (update-in schedule [:again :lapses] inc))

(defn update-schedule-due-scheduled-days
  "Update the `:due` and `:scheduled-days` fields in the `schedule` of
  the `card` by transitioning it through our state machine for every
  possible rating."
  [schedule card now params]
  (let [good-interval (next-interval params
                                     (get-in schedule [:good :stability]))
        hard-interval (min (next-interval params
                                          (get-in schedule [:hard :stability]))
                           good-interval)
        good-interval (max good-interval (inc hard-interval))
        easy-interval (max (inc good-interval)
                           (next-interval params
                                          (get-in schedule [:easy :stability])))]
    (case (:state card)
      :new
      (-> schedule
          (assoc-in [:again :due] (.plus now 1 ChronoUnit/MINUTES))
          (assoc-in [:hard  :due] (.plus now 5 ChronoUnit/MINUTES))
          (assoc-in [:good  :due] (.plus now good-interval ChronoUnit/DAYS))
          (assoc-in [:easy  :due] (.plus now easy-interval ChronoUnit/DAYS))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval))

      (:learning :relearning)
      (-> schedule
          (assoc-in [:again :due] (.plus now 5 ChronoUnit/MINUTES))
          (assoc-in [:hard  :due] (.plus now 10 ChronoUnit/MINUTES))
          (assoc-in [:good  :due] (.plus now good-interval ChronoUnit/DAYS))
          (assoc-in [:easy  :due] (.plus now easy-interval ChronoUnit/DAYS))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval))

      :review
      (-> schedule
          (assoc-in [:again :due] (.plus now 5 ChronoUnit/MINUTES))
          (assoc-in [:hard  :due] (.plus now hard-interval ChronoUnit/DAYS))
          (assoc-in [:good  :due] (.plus now good-interval ChronoUnit/DAYS))
          (assoc-in [:easy  :due] (.plus now easy-interval ChronoUnit/DAYS))
          (assoc-in [:again :scheduled-days] 0) ; Since due is in minutes
          (assoc-in [:hard  :scheduled-days] hard-interval)
          (assoc-in [:good  :scheduled-days] good-interval)
          (assoc-in [:easy  :scheduled-days] easy-interval)))))

(defn make-schedule
  "Given a `card`, make a `schedule` for the card which can be used to
  predict the next transition."
  [card review-time-instant params]
  (-> (empty-schedule card)
      (update-schedule-difficulty-stability card params)
      (update-schedule-state card)
      update-schedule-lapses
      (update-schedule-due-scheduled-days card review-time-instant params)))

(defn update-elapsed-days
  [card review-time-instant]
  (->> (LocalDate/ofInstant review-time-instant (ZoneId/of "UTC"))
       (Period/between
        (LocalDate/ofInstant (:last-review card) (ZoneId/of "UTC")))
       .getDays
       (assoc card :elapsed-days)))

;; We are surfacing a card for review! Update the internal state of
;; the card for all possible ratings that the user may provide. This
;; means that recording the rating of the user is as simple as picking
;; the right future from our prediction. This may look like work
;; upfront, but its done because it simplifies state management (I
;; think)
(defn review-card!
  "We have repeated the `card` according to previous instructions. We
  have a new `rating` for the card. Generate the new state of the card
  after the rating."
  ([card rating]
   (review-card! card rating default-params))
  ([card rating params]
   (assert-rating rating)
   (assert-weights (:weights params))
   (review-card! card rating (Instant/now) params))
  ;; This arity should be considered private. It's helpful to be able
  ;; to control time during tests.
  ([card rating review-time-instant params]
   (-> card
       (update-elapsed-days review-time-instant)
       (assoc :last-review review-time-instant)
       (update :reps inc)
       (make-schedule review-time-instant params)
       rating)))

;;; @TODO: What is the point of this log? It does not capture anything
;;; that the card can't tell better, other than the actual rating.
(defn record-log
  "Create a log of the `review-card!` activity. This should be shared
  back with `open-spaced-repetition` for improving the algorithm."
  [card rating]
  {:rating rating
   :scheduled-days (:scheduled-days card)
   :elapsed-days (:elapsed-days card)
   :review (:last-review card)
   :state (:state card)})
