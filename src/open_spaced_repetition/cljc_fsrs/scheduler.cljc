(ns open-spaced-repetition.cljc-fsrs.scheduler
  (:require [open-spaced-repetition.cljc-fsrs.parameters :as p]
            [tick.core :as t]))

;; Keep the public four-rating workflow. :step records the learning or
;; relearning step used by the official FSRS-6 scheduler.
(defn- memory-after-review [{:keys [state elapsed-days stability difficulty]} rating weights]
  (cond
    (= state :new)
    [(p/init-stability weights rating) (p/init-difficulty weights rating)]

    (< elapsed-days 1)
    [(p/short-term-stability weights stability rating)
     (p/next-difficulty weights difficulty rating)]

    :else
    [(p/next-stability weights difficulty stability
                       (p/retrievability weights elapsed-days stability) rating)
     (p/next-difficulty weights difficulty rating)]))

(defn- next-state-and-delay [{:keys [state step]} rating]
  (case state
    :new (case rating
           :again [:learning 0 60]
           :hard [:learning 0 330]
           :good [:learning 1 600]
           :easy [:review nil nil])

    :learning (case rating
                :again [:learning 0 60]
                :hard [:learning step (if (zero? step) 330 600)]
                :good (if (zero? step)
                        [:learning 1 600]
                        [:review nil nil])
                :easy [:review nil nil])

    :review (if (= rating :again)
              [:relearning 0 600]
              [:review nil nil])

    :relearning (case rating
                  :again [:relearning 0 600]
                  :hard [:relearning 0 900]
                  :good [:review nil nil]
                  :easy [:review nil nil])))

(defn- next-card [card rating now params]
  (let [[stability difficulty] (memory-after-review card rating (:weights params))
        [state step delay-seconds] (next-state-and-delay card rating)
        scheduled-days (if delay-seconds 0 (p/next-interval params stability))
        due (if delay-seconds
              (t/>> now (t/new-duration delay-seconds :seconds))
              (t/>> now (t/new-period scheduled-days :days)))]
    (cond-> (assoc card
                   :stability stability
                   :difficulty difficulty
                   :state state
                   :step step
                   :scheduled-days scheduled-days
                   :due due)
      (= rating :again) (update :lapses inc))))

(defn next-repeat-schedule [card repeat-time-instant params]
  (into {} (map (fn [rating] [rating (next-card card rating repeat-time-instant params)])
                [:again :hard :good :easy])))
