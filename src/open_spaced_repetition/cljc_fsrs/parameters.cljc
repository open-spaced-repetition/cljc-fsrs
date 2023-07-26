(ns open-spaced-repetition.cljc-fsrs.parameters)

;;; # Card Ratings and States
;; There are four possible ratings that we assign to a card on every
;; spaced repetition. This code maps the human-readable rating to it's
;; index in the provided weights, and explains the meaning of each
;; rating:
(def ->rating "Mapping from rating keyword to it's index in the `weights`"
  ;; NOTE: This is strictly an implementation detail, use it only as such
  {
   :again 1 ;; We got the answer wrong. Automatically means that we
            ;; have forgotten the card. This is a lapse in memory.
   :hard  2 ;; The answer was only partially correct and/or we took
            ;; too long to recall it.
   :good  3 ;; The answer was correct but we were not confident about it.
   :easy  4 ;; The answer was correct and we were confident and quick
            ;; in our recall.
   })

;; A card can be in four possible states, the meaning of which are
;; explained in the code below. The card is always in :new state when
;; it is created, and never returns to :new state after the first
;; repeat.
(def state "The state of the card we are studying."
  #{
    :new        ;; We have just created the card. We hope to remember
                ;; it the next time we see it.
    :learning   ;; We haven't memorised the card yet, but have
                ;; repeated it at least once.
    :review     ;; We have memorised the card! This is the ideal state
                ;; we want all cards to be in forever. Alas for memory
                ;; decay!
    :relearning ;; We have forgotten the card. This is the same state
                ;; as :learning, expect it signifies that we've
                ;; arrived from :review instead of :new
    })

;;; # Default weights when no external weight is provided
(defn calculate-interval-modifier
  [request-retention]
  (* 9 (- (/ 1 request-retention) 1)))

(def default-params "The default parameters we use with FSRS."
  {
   :weights [0.4,   ;; Weight for :again rating
             0.6,   ;; Weight for :hard rating
             2.4,   ;; Weight for :good rating
             5.8,   ;; Weight for :easy rating
             4.93,
             0.94,
             0.86,
             0.01,
             1.49,
             0.14,
             0.94,
             2.18,
             0.05,
             0.34,
             1.26,
             0.29, ;; Hard Penalty
             2.61  ;; Easy Bonus
             ]
   :request-retention 0.9 ;; Retention is the percentage of your
                          ;; successful recall. `request-retention` is
                          ;; a number between 0 and 1 which defines
                          ;; the retention percentage we are
                          ;; targeting.
   :maximum-interval  36500 ;; The maximum interval of time before
                            ;; repeating the card, in days
   :interval-modifier (calculate-interval-modifier 0.9)
   })

;;; # Formulae and Calculations for predicting new parameters for a card
(defn assert-weights "Ensure that weights are meaningful"
  [weights]
  (assert (= (count weights) (count (:weights default-params)))
          (format "Given weights %s do not have enough parameters." weights)))

(defn assert-rating "Ensure that rating is valid"
  [rating]
  (assert (->rating rating) (format "Given rating %s does not exist" rating)))

(defn init-difficulty "Give the initial value of difficulty for given `rating`"
  [weights rating]
  (assert-weights weights)
  (assert-rating rating)
  (- (nth weights 4)
     (* (nth weights 5)
        (- (->rating rating) 3))))

(defn init-stability "Give the initial value of stability for given `rating`"
  [weights rating]
  (assert-weights weights)
  (assert-rating rating)
  (max 0.1 (nth weights (- (->rating rating) 1))))

(defn calculate-retrievability "Calculate decay in recall over time"
  [elapsed-days stability]
  (Math/pow (+ 1 (/ elapsed-days (* stability 9)))
            -1))

(defn constrain-difficulty "Keep difficulty between 1 and 10"
  [difficulty]
  (-> difficulty (max 1) (min 10)))

(defn mean-reversion "Ensure that we do not get stuck in easy hell"
  [current-difficulty weights]
  (+ (* (nth weights 7) (nth weights 4))
     (* (- 1 (nth weights 7)) current-difficulty)))

(defn next-difficulty "Given `difficulty` and `rating`, calculate the new diff"
  [difficulty rating weights]
  (let [new-diff (- difficulty
                    (* (nth weights 6)
                       (- (->rating rating) 3)))]
    (constrain-difficulty (mean-reversion new-diff weights))))

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

(defn next-interval "Given the `stability` of item, when should we revisit it?"
  [{:keys [maximum-interval interval-modifier]} stability]
  (let [new-interval (* stability interval-modifier)]
    ;; @TODO: Apply Fuzzying to the `new-interval`
    (min maximum-interval (max 1 (Math/round new-interval)))))
