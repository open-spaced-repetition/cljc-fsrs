# com.github.open-spaced-repetition/clj-fsrs
<p align="center">
  <a href="https://github.com/open-spaced-repetition/clj-fsrs/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-informational" alt="License"></a>
</p>

A Clojure implementation of [Free Spaced Repetition Scheduler algorithm](https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler)

## Table of Contents

- [🔧 Installation](#-installation)
- [🚀 Usage](#-usage)
- [⚙️ Contributing](#️-contributing)

## 🔧 Installation

At the moment, the project is not available on Clojars / Maven, and can only be installed as a Git Dependency. I will add instructions for installing from Maven once I upload the artifact.

This project is a straightforward re-implementation of the algorithm, inspired from the following two code-bases:
- https://github.com/open-spaced-repetition/fsrs4anki/blob/884741bc9f804ff428540184e0c971875f128fd1/fsrs4anki_scheduler.js
- https://github.com/open-spaced-repetition/go-fsrs/blob/70232f222d0c0ef7523ffa7ceb3de91feddf6030/fsrs.go

There is a lot of scope to make this more Clojure-y, which I will do as and when time permits.

### Deps.edn
```edn
io.github.vedang/clj-fsrs {:git/sha "<PUT-LATEST-SHA-HERE>"}
```

## 🚀 Usage

```clojure
(require '[open-spaced-repetition.clj-fsrs.core :as core])

(def card (core/new-card!))
;;; =>
;; {:last-review
;;  #object[java.time.Instant 0x33433635 "2023-07-15T14:42:14.706482Z"],
;;  :lapses 0,
;;  :stability 0,
;;  :difficulty 0,
;;  :reps 0,
;;  :state :new,
;;  :due
;;  #object[java.time.Instant 0x33433635 "2023-07-15T14:42:14.706482Z"],
;;  :elapsed-days 0,
;;  :scheduled-days 0}
```

We reviewed the card immediately after creating it, as suggested in the response `:due`. Our recall rating was `:good`.

```clojure
(-> card
    (core/review-card! :good))

;;; =>
;; {:last-review
;;  #object[java.time.Instant 0x53f66932 "2023-07-15T14:45:26.271152Z"],
;;  :lapses 0,
;;  :stability 3,
;;  :difficulty 5.0,
;;  :reps 1,
;;  :state :learning,
;;  :due
;;  #object[java.time.Instant 0x1fa27e00 "2023-07-18T14:45:26.274199Z"],
;;  :elapsed-days 0,
;;  :scheduled-days 3}
```

You can see how the `:difficulty`, `:stability` are given initial values based on your rating. The `:state` of the card is now `:learning`. We have also been told to review it again after 3 days.

We waited three days and reviewed it again. This time we forgot the card and our rating was `:again`
```clojure
(import
 '(java.time Instant)
 '(java.time.temporal ChronoUnit))

(-> card
    (core/review-card! :good)
    ;; This arity should be considered private. It's helpful to be
    ;; able to control time during tests, but real usage should use
    ;; the version above, not the one below
    (core/review-card! :again (.plus (Instant/now) 3 ChronoUnit/DAYS) core/default-params))

;; =>
;; {:lapses 1,
;;  :stability 3,
;;  :difficulty 5.0,
;;  :reps 2,
;;  :state :learning,
;;  :due
;;  #object[java.time.Instant 0x11ed2b5 "2023-07-18T17:51:22.976950Z"],
;;  :elapsed-days 3,
;;  :scheduled-days 0,
;;  :last-review
;;  #object[java.time.Instant 0x6fea1722 "2023-07-18T17:46:22.976950Z"]}
```

Until we move into `:review` state, the `:stability` and `:difficulty` settings are not affected. Let's re-review the card and this time rate it as `:good`

## ⚙️ Contributing
### 🛠 Code Development

Run the project's tests:

    $ clojure -T:build test

Run the project's CI pipeline and build a JAR:

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables (requires the `ci` task be run first):

    $ clojure -T:build deploy

Your library will be deployed to com.github.open-spaced-repetition/clj-fsrs on clojars.org by default.

## License

Copyright © 2023 Vedang Manerikar

Distributed under the MIT License.
