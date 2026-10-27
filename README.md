# Tibu Collector Starter

This is the official starter project to clone when building a Tibu collector.
It implements a collector for the Tibu test website, Maison démo, and shows how
to connect a website to the Tibu Collector API.

## Requirements

- JDK 21 or newer. The build targets Java 21.
- Apache Maven 3.9 or newer. Maven is the reference build system.

Check your tools with `java -version` and `mvn -version`.
Read [AGENTS.md](AGENTS.md) before making changes.

## Build and test

```sh
mvn verify
```

The first build needs internet access to download dependencies and build plugins.
Build output is written to `target/`. Normal tests use saved demo pages and do not
need a running website.

To test the full collector against the local demo website, start that website at
`http://localhost:18743`, then run:

```sh
mvn -Dstarter.live=true -Dtest=StarterLiveTest test
```

The live test uses the public demo login `camille@example.test`, password
`demo1234`, and OTP code `1234`. It checks collection and a second run with saved
settings, accounts, recurring payments, and the same session. Downloads and cookies
stay in the test's temporary directory.

## Project structure

```text
pom.xml                 Java 21 Maven configuration
AGENTS.md               Development rules for humans and coding agents
src/main/java/          Collector and website methods
src/main/resources/     Plugin registration, messages, and logo
src/test/java/          Flow, website, and optional live tests
src/test/resources/     Saved public demo pages
docs/                  Collector documentation
LICENSE                 Existing project license
```

## Create your own collector

Update the Maven coordinates and project name in `pom.xml`, then adapt
`StarterCollector` and its resources to your website. Keep the main steps:
sign in, synchronize accounts, synchronize recurring payments, and collect
transactions in date order. Use the linked API documentation in each method
for the DTO fields to fill in.

See [the test website implementation](docs/test-site.md) for the demo's behavior
and choices to replace for a real website. The Tibu API is provided by the host
app; collector utilities and test utilities are configured in `pom.xml`.

## License

See [LICENSE](LICENSE) for the existing license terms.
