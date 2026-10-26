# Tibu Collector Starter

This is the official starter project to clone when building a Tibu collector.
A collector connects a website to Tibu and collects data for the user.

The project provides a collection flow built on the Tibu Collector API and collector utilities.
Website authentication and data extraction are still to be implemented.
Later, it will include a working collector connected to the Tibu test website
and serve as the reference implementation for the Tibu Collector API.

## Requirements

- JDK 21 or newer. The build targets Java 21.
- Apache Maven 3.9 or newer. Maven is the reference build system.

Check your tools with `java -version` and `mvn -version`.
Maven must use a JDK, not only a Java runtime. Set `JAVA_HOME` if needed.

## Get started

Clone this repository and open its root directory in your terminal or IDE.
Read [AGENTS.md](AGENTS.md) before making changes.
When starting your own collector, update the Maven coordinates and project name
in `pom.xml` to match your project.

Build and verify the project:

```sh
mvn verify
```

Maven downloads its build plugins on the first run, so internet access is needed.
Build output is written to `target/`.
The build runs tests for incremental history, interruption, progress, and plugin discovery.
The starter is not yet a working collector: website methods explicitly throw
`UnsupportedOperationException` until they are implemented.

## Project structure

```text
pom.xml                 Maven configuration, targeting Java 21
AGENTS.md               Development rules for humans and coding agents
src/main/java/          Collector flow and website methods to complete
src/main/resources/     Plugin registration, messages, and logo
src/test/java/          Collection flow tests
src/test/resources/     Test resources (currently empty)
docs/                   Collection flow documentation
LICENSE                 Existing project license
```

Empty test resource directories contain `.gitkeep` files so Git preserves them.
Remove these placeholders when adding files to a directory.

See [the collection flow](docs/collection-flow.md) for the implementation steps,
stop behavior, and progress rules. The Tibu API is provided by the host app;
collector utilities and test utilities are configured in `pom.xml`.

## License

See [LICENSE](LICENSE) for the existing license terms.
