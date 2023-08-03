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
  (t/is (= {:lapses 0
            :stability 2.4
            :difficulty 4.93
            :reps 1
            :state :learning
            :scheduled-days 0}
           (-> (core/new-card!)
               (core/repeat-card! :good core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days])))
        "Card value transition based on a First Spaced Repetition :good rating")

  (t/is (= {:lapses 0
            :stability 5.8
            :difficulty 3.9899999999999998
            :reps 1
            :state :review
            :scheduled-days 6}
           (-> (core/new-card!)
               (core/repeat-card! :easy core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days])))
        "Card value transition based on a First Spaced Repetition :easy rating")

  (t/is (= {:lapses 0
            :stability 0.6
            :difficulty 5.869999999999999
            :reps 1
            :state :learning
            :scheduled-days 0}
           (-> (core/new-card!)
               (core/repeat-card! :hard core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days])))
        "Card value transition based on a First Spaced Repetition :hard rating")

  (t/is (= {:lapses 1
            :stability 0.4
            :difficulty 6.81
            :reps 1
            :state :learning
            :scheduled-days 0}
           (-> (core/new-card!)
               (core/repeat-card! :again core/default-params)
               (select-keys [:lapses :stability :difficulty :reps :state
                             :scheduled-days])))
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
    (t/is (= {:lapses 1,
              :stability 37.92909396014857
              :difficulty 4.9998939999999985
              :reps 5,
              :state :review,
              :due #time/instant "2023-09-04T14:47:14.706482Z"
              :elapsed-days 7,
              :scheduled-days 38
              :last-repeat #time/instant "2023-07-28T14:47:14.706482Z"}
             (-> card
                 (core/repeat-card! :hard  #time/instant "2023-07-15T14:42:14.706482Z" core/default-params)
                 (core/repeat-card! :again #time/instant "2023-07-18T14:42:14.706482Z" core/default-params)
                 (core/repeat-card! :good  #time/instant "2023-07-18T14:47:14.706482Z" core/default-params)
                 (core/repeat-card! :good  #time/instant "2023-07-21T14:47:14.706482Z" core/default-params)
                 (core/repeat-card! :easy  #time/instant "2023-07-28T14:47:14.706482Z" core/default-params))))))

(t/deftest simulate-repeats-readme-example
  (t/is (= [{:lapses 1,
            :stability 3,
            :difficulty 5.0,
            :last-repeat #time/instant "2023-07-18T14:47:14.706482Z",
            :reps 3,
            :state :review,
            :due #time/instant "2023-07-22T14:47:14.706482Z",
            :elapsed-days 0,
            :scheduled-days 4}
           {:lapses 1,
            :stability 5.560958220030711,
            :difficulty 5.8507,
            :last-repeat #time/instant "2023-07-22T14:47:14.706482Z",
            :reps 4,
            :state :review,
            :due #time/instant "2023-07-28T14:47:14.706482Z",
            :elapsed-days 4,
            :scheduled-days 6,
            :rating :hard}
           {:lapses 1,
            :stability 16.13945305968846,
            :difficulty 5.841493,
            :last-repeat #time/instant "2023-07-28T14:47:14.706482Z",
            :reps 5,
            :state :review,
            :due #time/instant "2023-08-13T14:47:14.706482Z",
            :elapsed-days 6,
            :scheduled-days 16,
            :rating :good}
           {:lapses 1,
            :stability 79.99409887788744,
            :difficulty 4.980978069999999,
            :last-repeat #time/instant "2023-08-13T14:47:14.706482Z",
            :reps 6,
            :state :review,
            :due #time/instant "2023-11-01T14:47:14.706482Z",
            :elapsed-days 16,
            :scheduled-days 80,
            :rating :easy}
           {:lapses 1,
            :stability 194.01496233386962,
            :difficulty 4.980468289299998,
            :last-repeat #time/instant "2023-11-01T14:47:14.706482Z",
            :reps 7,
            :state :review,
            :due #time/instant "2024-05-13T14:47:14.706482Z",
            :elapsed-days 80,
            :scheduled-days 194,
            :rating :good}]
           (let [card {:lapses 1,
                       :stability 3,
                       :difficulty 5.0,
                       :reps 3,
                       :state :review,
                       :due #time/instant "2023-07-22T14:47:14.706482Z",
                       :elapsed-days 0,
                       :scheduled-days 4,
                       :last-repeat #time/instant "2023-07-18T14:47:14.706482Z"}]
             (simulate-repeats card [:hard :good :easy :good])))))
