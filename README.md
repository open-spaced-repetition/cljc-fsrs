# com.github.open-spaced-repetition/cljc-fsrs
<p align="center">
  <a href="https://github.com/open-spaced-repetition/cljc-fsrs/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-informational" alt="License"></a>
</p>

A Clojure(script) implementation of [Free Spaced Repetition Scheduler algorithm](https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler)

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
io.github.vedang/cljc-fsrs {:git/sha "<PUT-LATEST-SHA-HERE>"}
```

## 🚀 Usage

```clojure
(require '[open-spaced-repetition.cljc-fsrs.core :as core]
         '[tick.core :as t])

(def card (core/new-card!))
;; =>
{:last-repeat
 #time/instant "2023-07-15T14:42:14.706482Z",
 :lapses 0,
 :stability 0,
 :difficulty 0,
 :reps 0,
 :state :new,
 :due
 #time/instant "2023-07-15T14:42:14.706482Z",
 :elapsed-days 0,
 :scheduled-days 0}
```

We studied the card immediately as part of creating it, as suggested in the response `:due`. Our recall rating was `:good`.

```clojure
(-> card
    (core/repeat-card! :good))

;; =>
{:last-repeat
 #time/instant "2023-07-15T14:45:26.271152Z",
 :lapses 0,
 :stability 3,
 :difficulty 5.0,
 :reps 1,
 :state :learning,
 :due
 #time/instant "2023-07-18T14:45:26.274199Z",
 :elapsed-days 0,
 :scheduled-days 3}
```

You can see how the `:difficulty`, `:stability` are given initial values based on your rating. The `:state` of the card is now `:learning`. We have also been told to repeat the card after 3 days.

We waited three days and studied the card again. This time we forgot the card and our rating was `:again`
```clojure
(-> card
    (core/repeat-card! :good)
    ;; This arity should be considered private. It's helpful to be
    ;; able to control time during tests, but real usage should use
    ;; the version above, not the one below
    (core/repeat-card! :again (t/>> (t/now) (t/new-period 3 :days)) core/default-params))

;; =>
{:lapses 1,
 :stability 3,
 :difficulty 5.0,
 :reps 2,
 :state :learning,
 :due
 #time/instant "2023-07-18T17:51:22.976950Z",
 :elapsed-days 3,
 :scheduled-days 0,
 :last-repeat
 #time/instant "2023-07-18T17:46:22.976950Z"}
```

Until we move into `:review` state, the `:stability` and `:difficulty` settings are not affected. Since we forgot the card (hence `:again` rating), we can see this reflected in `:lapses` and the fact that we've been asked to repeat the card in 5 minutes. Let's re-review the card and this time rate it as `:good`

```clojure
(-> card
    (core/repeat-card! :good  #time/instant "2023-07-15T14:42:14.706482Z" core/default-params)
    (core/repeat-card! :again #time/instant "2023-07-18T14:42:14.706482Z" core/default-params)
    (core/repeat-card! :good  #time/instant "2023-07-18T14:47:14.706482Z" core/default-params))
;; =>
{:lapses 1,
 :stability 3,
 :difficulty 5.0,
 :reps 3,
 :state :review,
 :due #time/instant "2023-07-22T14:47:14.706482Z",
 :elapsed-days 0,
 :scheduled-days 4,
 :last-repeat #time/instant "2023-07-18T14:47:14.706482Z"}
```

We are now in `:review` state and will start tracking the stability and difficulty of the item! We have a testing utility called `simulate-repeats`, which you can use to see how these numbers change over time.
```clojure
(require '[open-spaced-repetition.cljc-fsrs.simulate :refer [simulate-repeats]])

(-> {:lapses 1,
     :stability 3,
     :difficulty 5.0,
     :reps 3,
     :state :review,
     :due #time/instant "2023-07-22T14:47:14.706482Z",
     :elapsed-days 0,
     :scheduled-days 4,
     :last-repeat #time/instant "2023-07-18T14:47:14.706482Z"}
    (simulate-repeats [:hard :good :easy :good]))
;; =>
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
  :rating :good}]
```

## ⚙️ Contributing (This Section TBD)
### 🛠 Code Development

Start a REPL against `cljc-fsrs`

    $ clojure -M:cider:dev:test

Run `cljc-fsrs` tests:

    $ clojure -T:build test

Run `cljc-fsrs` CI pipeline and build a JAR:

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables (requires the `ci` task be run first):

    $ clojure -T:build deploy

Your library will be deployed to com.github.open-spaced-repetition/cljc-fsrs on clojars.org by default.

## License

Copyright © 2023 Vedang Manerikar

Distributed under the MIT License.
