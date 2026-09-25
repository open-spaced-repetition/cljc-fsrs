(ns open-spaced-repetition.cljc-fsrs.core-test
  (:require #?(:clj [clojure.test :refer [deftest is]]
               :cljs [cljs.test :refer-macros [deftest is]])
            [open-spaced-repetition.cljc-fsrs.card :as card]
            [open-spaced-repetition.cljc-fsrs.core :as core]
            [open-spaced-repetition.cljc-fsrs.parameters :as p]))

;; Expected results generated with py-fsrs Scheduler(enable_fuzzing=False),
;; default 1m/10m learning steps and 10m relearning step. See README.
(def start #time/instant "2026-01-01T00:00:00Z")

(defn close? [expected actual]
  (< (Math/abs (- expected actual)) 1.0e-9))

(defn check-history [steps params]
  (reduce
   (fn [current [rating at state step stability difficulty due]]
     (let [result (core/repeat-card! current rating at params)]
       (is (= state (:state result)))
       (is (= step (:step result)))
       (is (close? stability (:stability result)))
       (is (close? difficulty (:difficulty result)))
       (is (= due (:due result)))
       result))
   (card/new-card! start)
   steps))

(deftest fsrs-6-learning-and-review-vector
  (check-history
   [[:good #time/instant "2026-01-01T00:00:00Z" :learning 1
     2.3065 2.11810397045902 #time/instant "2026-01-01T00:10:00Z"]
    [:good #time/instant "2026-01-01T00:00:00Z" :review nil
     2.3065 2.1112142357854 #time/instant "2026-01-03T00:00:00Z"]
    [:hard #time/instant "2026-01-02T00:00:00Z" :review nil
     5.32112941924106 4.74828476159457 #time/instant "2026-01-07T00:00:00Z"]
    [:good #time/instant "2026-01-04T00:00:00Z" :review nil
     11.5925926131477 4.73876484612981 #time/instant "2026-01-16T00:00:00Z"]
    [:easy #time/instant "2026-01-11T00:00:00Z" :review nil
     44.8669094193924 2.96593360056141 #time/instant "2026-02-25T00:00:00Z"]]
   core/default-params))

(deftest fsrs-6-lapse-and-relearning-vector
  (let [result
        (check-history
         [[:easy #time/instant "2026-01-01T00:00:00Z" :review nil
           8.2956 1.0 #time/instant "2026-01-09T00:00:00Z"]
          [:again #time/instant "2026-01-09T00:00:00Z" :relearning 0
           1.38863246098212 7.02698956929684 #time/instant "2026-01-09T00:10:00Z"]
          [:hard #time/instant "2026-01-09T00:00:00Z" :relearning 0
           1.38863246098212 8.01160550311001 #time/instant "2026-01-09T00:15:00Z"]
          [:good #time/instant "2026-01-09T00:00:00Z" :review nil
           1.42788168076388 7.99882226690374 #time/instant "2026-01-10T00:00:00Z"]
          [:again #time/instant "2026-01-12T00:00:00Z" :relearning 0
           0.445219757049851 9.32745485643668 #time/instant "2026-01-12T00:10:00Z"]
          [:good #time/instant "2026-01-12T00:00:00Z" :review nil
           0.493384367085389 9.31335577087708 #time/instant "2026-01-13T00:00:00Z"]]
         core/default-params)]
    (is (= 2 (:lapses result)))
    (is (= 6 (:reps result)))))

(deftest fsrs-6-retention-and-maximum-vector
  (let [params (assoc core/default-params :request-retention 0.8 :maximum-interval 5)
        result (check-history
                [[:easy #time/instant "2026-01-01T00:00:00Z" :review nil
                  8.2956 1.0 #time/instant "2026-01-06T00:00:00Z"]
                 [:good #time/instant "2026-01-09T00:00:00Z" :review nil
                  38.9051499823268 1.0 #time/instant "2026-01-14T00:00:00Z"]
                 [:again #time/instant "2026-01-14T00:00:00Z" :relearning 0
                  2.50031148881565 7.02698956929684 #time/instant "2026-01-14T00:10:00Z"]]
                params)]
    (is (= 0 (:scheduled-days result)))
    (is (= 5 (p/next-interval params 38.9051499823268)))))

(deftest rejects-old-cards-and-weights
  (let [new-card (card/new-card! start)]
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (core/repeat-card! (dissoc new-card :fsrs-version)
                                    :good start core/default-params)))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (core/repeat-card! new-card :good start
                                    (assoc core/default-params :weights (vec (range 17))))))))
