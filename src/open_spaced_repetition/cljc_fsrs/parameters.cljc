(ns open-spaced-repetition.cljc-fsrs.parameters)

;; FSRS-6 defaults from the official py-fsrs scheduler.
(def default-params
  {:weights [0.212 1.2931 2.3065 8.2956 6.4133 0.8334 3.0194
             0.001 1.8722 0.1666 0.796 1.4835 0.0614 0.2629
             1.6483 0.6014 1.8729 0.5425 0.0912 0.0658 0.1542]
   :request-retention 0.9
   :maximum-interval 36500})

(def ->rating {:again 1 :hard 2 :good 3 :easy 4})
(def minimum-stability 0.001)
(def lower-bounds
  [0.001 0.001 0.001 0.001 1.0 0.001 0.001 0.001 0.0 0.0
   0.001 0.001 0.001 0.001 0.0 0.0 1.0 0.0 0.0 0.0 0.1])
(def upper-bounds
  [100.0 100.0 100.0 100.0 10.0 4.0 4.0 0.75 4.5 0.8
   3.5 5.0 0.25 0.9 4.0 1.0 6.0 2.0 2.0 0.8 0.8])

(defn validate-params! [{:keys [weights request-retention maximum-interval]}]
  (when-not (= 21 (count weights))
    (throw (ex-info "FSRS-6 requires 21 weights; FSRS v4 weights must be reoptimized or replaced"
                    {:weight-count (count weights)})))
  (doseq [i (range 21)]
    (let [weight (nth weights i)]
      (when-not (and (number? weight)
                     (<= (nth lower-bounds i) weight (nth upper-bounds i)))
        (throw (ex-info "FSRS-6 weight is outside the official bounds"
                        {:index i :weight weight})))))
  (when-not (and (number? request-retention) (< 0 request-retention 1))
    (throw (ex-info "request-retention must be between 0 and 1" {})))
  (when-not (and (integer? maximum-interval) (pos? maximum-interval))
    (throw (ex-info "maximum-interval must be a positive integer" {}))))

(defn validate-rating! [rating]
  (when-not (->rating rating)
    (throw (ex-info "Unknown card rating" {:rating rating}))))

(defn clamp [value low high] (min high (max low value)))
(defn clamp-stability [value] (max minimum-stability value))
(defn decay [weights] (- (nth weights 20)))
(defn factor [weights] (- (Math/pow 0.9 (/ 1.0 (decay weights))) 1.0))

(defn init-stability [weights rating]
  (clamp-stability (nth weights (dec (->rating rating)))))

(defn init-difficulty [weights rating]
  (clamp (- (+ (nth weights 4) 1.0)
            (Math/exp (* (nth weights 5) (dec (->rating rating)))))
         1.0 10.0))

(defn retrievability [weights elapsed-days stability]
  (Math/pow (+ 1.0 (* (factor weights) (/ (max 0 elapsed-days) stability)))
            (decay weights)))

(defn next-difficulty [weights difficulty rating]
  (let [delta (- (* (nth weights 6) (- (->rating rating) 3)))
        damped (+ difficulty (/ (* (- 10.0 difficulty) delta) 9.0))
        easy-difficulty (- (+ (nth weights 4) 1.0)
                           (Math/exp (* (nth weights 5) 3.0)))
        reverted (+ (* (nth weights 7) easy-difficulty)
                    (* (- 1.0 (nth weights 7)) damped))]
    (clamp reverted 1.0 10.0)))

(defn short-term-stability [weights stability rating]
  (let [increase (* (Math/exp (* (nth weights 17)
                                 (+ (- (->rating rating) 3) (nth weights 18))))
                    (Math/pow stability (- (nth weights 19))))]
    (clamp-stability (* stability
                        (if (= rating :again) increase (max 1.0 increase))))))

(defn next-stability [weights difficulty stability retrievability rating]
  (clamp-stability
   (if (= rating :again)
     (min (* (nth weights 11)
             (Math/pow difficulty (- (nth weights 12)))
             (- (Math/pow (+ stability 1.0) (nth weights 13)) 1.0)
             (Math/exp (* (- 1.0 retrievability) (nth weights 14))))
          (/ stability (Math/exp (* (nth weights 17) (nth weights 18)))))
     (* stability
        (+ 1.0 (* (Math/exp (nth weights 8))
                  (- 11.0 difficulty)
                  (Math/pow stability (- (nth weights 9)))
                  (- (Math/exp (* (- 1.0 retrievability) (nth weights 10))) 1.0)
                  (if (= rating :hard) (nth weights 15) 1.0)
                  (if (= rating :easy) (nth weights 16) 1.0)))))))

(defn next-interval [{:keys [weights request-retention maximum-interval]} stability]
  (let [days (* (/ stability (factor weights))
                (- (Math/pow request-retention (/ 1.0 (decay weights))) 1.0))
        ;; Python's round() uses ties to even, unlike Math/round.
        floor (Math/floor days)
        rounded (cond
                  (< (- days floor) 0.5) floor
                  (> (- days floor) 0.5) (inc floor)
                  (zero? (mod floor 2)) floor
                  :else (inc floor))]
    (min maximum-interval (max 1 rounded))))
