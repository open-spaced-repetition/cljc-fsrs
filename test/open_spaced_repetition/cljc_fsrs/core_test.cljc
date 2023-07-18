(ns open-spaced-repetition.cljc-fsrs.core-test
  (:require
   [clojure.test :as t]
   [open-spaced-repetition.cljc-fsrs.core :as core]
   [open-spaced-repetition.cljc-fsrs.simulate :refer [simulate-repeats]]))

(t/deftest new-card!
  (t/is (= (dissoc (core/new-card!) :last-repeat :due)
           {:lapses 0 :stability 0 :difficulty 0 :reps 0 :state :new
            :elapsed-days 0 :scheduled-days 0})
        "New Cards don't contain any data, not even the actual default values"))

(t/deftest repeat-card!
  (t/is (= (-> (core/new-card!)
               (core/repeat-card! :good core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 0
            :stability 3
            :difficulty 5.0
            :reps 1
            :state :learning
            :scheduled-days 3})
        "Card value transition based on a First Spaced Repetition :good rating")

  (t/is (= (-> (core/new-card!)
               (core/repeat-card! :easy core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 0
            :stability 4
            :difficulty 4.5
            :reps 1
            :state :review
            :scheduled-days 4})
        "Card value transition based on a First Spaced Repetition :easy rating")

  (t/is (= (-> (core/new-card!)
               (core/repeat-card! :hard core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 0
            :stability 2
            :difficulty 5.5
            :reps 1
            :state :learning
            :scheduled-days 0})
        "Card value transition based on a First Spaced Repetition :hard rating")

  (t/is (= (-> (core/new-card!)
               (core/repeat-card! :again core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days]))
           {:lapses 1
            :stability 1
            :difficulty 6.0
            :reps 1
            :state :learning
            :scheduled-days 0})
        "Card value transition based on a First Spaced Repetition :again rating"))

(t/deftest repeat-card!-manual-time-stamps
  (let [card {:lapses 0,
              :stability 0,
              :difficulty 0,
              :reps 0,
              :state :new,
              :due #time/instant "2023-07-15T14:42:14.706482Z",
              :elapsed-days 0,
              :scheduled-days 0,
              :last-repeat #time/instant "2023-07-15T14:42:14.706482Z"}]
    (t/is (-> card
              (core/repeat-card! :hard  #time/instant "2023-07-15T14:42:14.706482Z" core/default-params)
              (core/repeat-card! :again #time/instant "2023-07-18T14:42:14.706482Z" core/default-params)
              (core/repeat-card! :good  #time/instant "2023-07-18T14:47:14.706482Z" core/default-params)
              (core/repeat-card! :good  #time/instant "2023-07-21T14:47:14.706482Z" core/default-params)
              (core/repeat-card! :easy  #time/instant "2023-07-28T14:47:14.706482Z" core/default-params)
              (= {:lapses 1,
                  :stability 24.75017663164144,
                  :difficulty 4.92,
                  :reps 5,
                  :state :review,
                  :due #time/instant "2023-08-22T14:47:14.706482Z",
                  :elapsed-days 7,
                  :scheduled-days 25,
                  :last-repeat #time/instant "2023-07-28T14:47:14.706482Z"})))))

(t/deftest simulate-repeats-readme-example
  (t/is (= (let [card {:lapses 1,
                       :stability 3,
                       :difficulty 5.0,
                       :reps 3,
                       :state :review,
                       :due #time/instant "2023-07-22T14:47:14.706482Z",
                       :elapsed-days 0,
                       :scheduled-days 4,
                       :last-repeat #time/instant "2023-07-18T14:47:14.706482Z"}]
             (simulate-repeats card [:hard :good :easy :good]))
           [{:lapses 1,
             :stability 3,
             :difficulty 5.0,
             :last-repeat #time/instant "2023-07-18T14:47:14.706482Z",
             :reps 3,
             :state :review,
             :due #time/instant "2023-07-22T14:47:14.706482Z",
             :elapsed-days 0,
             :scheduled-days 4}
            {:lapses 1,
             :stability 6.185860963467298,
             :difficulty 5.4,
             :last-repeat #time/instant "2023-07-22T14:47:14.706482Z",
             :reps 4,
             :state :review,
             :due #time/instant "2023-07-28T14:47:14.706482Z",
             :elapsed-days 4,
             :scheduled-days 6,
             :rating :hard}
            {:lapses 1,
             :stability 14.083159793583967,
             :difficulty 5.32,
             :last-repeat #time/instant "2023-07-28T14:47:14.706482Z",
             :reps 5,
             :state :review,
             :due #time/instant "2023-08-11T14:47:14.706482Z",
             :elapsed-days 6,
             :scheduled-days 14,
             :rating :good}
            {:lapses 1,
             :stability 45.743757632503126,
             :difficulty 4.856,
             :last-repeat #time/instant "2023-08-11T14:47:14.706482Z",
             :reps 6,
             :state :review,
             :due #time/instant "2023-09-26T14:47:14.706482Z",
             :elapsed-days 14,
             :scheduled-days 46,
             :rating :easy}
            {:lapses 1,
             :stability 90.16370184543588,
             :difficulty 4.8848,
             :last-repeat #time/instant "2023-09-26T14:47:14.706482Z",
             :reps 7,
             :state :review,
             :due #time/instant "2023-12-25T14:47:14.706482Z",
             :elapsed-days 46,
             :scheduled-days 90,
             :rating :good}])))
