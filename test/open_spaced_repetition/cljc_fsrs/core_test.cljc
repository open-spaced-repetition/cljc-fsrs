(ns open-spaced-repetition.cljc-fsrs.core-test
  (:require [open-spaced-repetition.cljc-fsrs.core :as core]
            [clojure.test :as t]))

(t/deftest new-card!
  (t/is (= (dissoc (core/new-card!) :last-review :due)
           {:lapses 0 :stability 0 :difficulty 0 :reps 0 :state :new
            :elapsed-days 0 :scheduled-days 0})
        "New Cards don't contain any data, not even the actual default values"))

(t/deftest review-card!
  (t/is (= (-> (core/new-card!)
               (core/review-card! :good core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 0
            :stability 3
            :difficulty 5.0
            :reps 1
            :state :learning
            :scheduled-days 3})
        "Card value transition based on a First review :good rating")

  (t/is (= (-> (core/new-card!)
               (core/review-card! :easy core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 0
            :stability 4
            :difficulty 4.5
            :reps 1
            :state :review
            :scheduled-days 4})
        "Card value transition based on a First review :easy rating")

  (t/is (= (-> (core/new-card!)
               (core/review-card! :hard core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 0
            :stability 2
            :difficulty 5.5
            :reps 1
            :state :learning
            :scheduled-days 0})
        "Card value transition based on a First review :hard rating")

  (t/is (= (-> (core/new-card!)
               (core/review-card! :again core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 1
            :stability 1
            :difficulty 6.0
            :reps 1
            :state :learning
            :scheduled-days 0})
        "Card value transition based on a First review :again rating"))

(t/deftest review-card!-manual-time-stamps
  (let [card {:lapses 0,
              :stability 0,
              :difficulty 0,
              :reps 0,
              :state :new,
              :due #time/instant "2023-07-15T14:42:14.706482Z",
              :elapsed-days 0,
              :scheduled-days 0,
              :last-review #time/instant "2023-07-15T14:42:14.706482Z"}]
    (t/is (-> card
              (core/review-card! :hard  #time/instant "2023-07-15T14:42:14.706482Z" core/default-params)
              (core/review-card! :again #time/instant "2023-07-18T14:42:14.706482Z" core/default-params)
              (core/review-card! :good  #time/instant "2023-07-18T14:47:14.706482Z" core/default-params)
              (core/review-card! :good  #time/instant "2023-07-21T14:47:14.706482Z" core/default-params)
              (core/review-card! :easy  #time/instant "2023-07-28T14:47:14.706482Z" core/default-params)
              (= {:lapses 1,
                  :stability 24.75017663164144,
                  :difficulty 4.92,
                  :reps 5,
                  :state :review,
                  :due #time/instant "2023-08-22T14:47:14.706482Z",
                  :elapsed-days 7,
                  :scheduled-days 25,
                  :last-review #time/instant "2023-07-28T14:47:14.706482Z"})))))
