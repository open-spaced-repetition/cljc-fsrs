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

At the moment, the project is not available on Clojars / Maven, and can only be installed as a Git Dependency. I will add instructions for installing from Maven once I upload the artifact

### Deps.edn
```edn
io.github.vedang/clj-fsrs {:git/sha "<PUT-LATEST-SHA-HERE>"}
```

## 🚀 Usage
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
