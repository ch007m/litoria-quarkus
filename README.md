# <img src="src/main/resources/templates/asciidoctor/image/litoria-chloris.jpg" width="80"> Quarkus litoria reporting tool and more

Command Line Tool to manage AsciiDoc and Markdown projects, generate HTML/PDF/RevealJS slideshows, embed CSS & images, and send reports as email — built on [Quarkus](https://quarkus.io/) with [Aesh](https://quarkus.io/guides/aesh).

|                | Project Info                                        |
|----------------|-----------------------------------------------------|
| License:       | Apache-2.0                                          |
| Issue tracker: | https://github.com/ch007m/litoria-quarkus/issues    |
| JDK:           | Java >= 21                                          |

## Quick Start

```shell
./mvnw package
jbang app install --name litoria target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar

litoria init -t report /tmp/my-report
litoria generate /tmp/my-report
litoria send /tmp/my-report

litoria init -t slideshow /tmp/my-slides
litoria generate -r revealjs /tmp/my-slides
litoria serve /tmp/my-slides/generated/...
```

## Documentation

Full documentation is published at **[ch007m.github.io/litoria-quarkus](https://ch007m.github.io/litoria-quarkus/)**:

- [Command Reference](https://ch007m.github.io/litoria-quarkus/commands) — all commands, options, and arguments
- [RevealJS Slideshow Guide](https://ch007m.github.io/litoria-quarkus/slideshow) — generating and serving slideshows
- [Slideshow Token Reference](https://ch007m.github.io/litoria-quarkus/slideshow-tokens) — `{token}` shorthand syntax
- [Token Slideshow Preview](https://ch007m.github.io/litoria-quarkus/slides-tokens.html) — live RevealJS demo
