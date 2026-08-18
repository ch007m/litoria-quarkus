# <img src="src/main/resources/templates/asciidoctor/image/litoria-chloris.jpg" width="80"> Litoria

**One tool — write, generate, present, deliver.**

Litoria is a command-line tool that turns Markdown or AsciiDoc source files into polished HTML pages, PDF documents, RevealJS slideshows, and email-ready reports. Scaffold a project, generate output, embed images and styles, and send it — all from the terminal.

Built on [Quarkus](https://quarkus.io/) with [Aesh](https://quarkus.io/guides/aesh) for fast startup and native-image readiness.

### What you can do

- **Write** — scaffold reports, minutes, docs, or slideshows with `litoria init`
- **Generate** — produce HTML, PDF, or RevealJS slides with `litoria generate`
- **Present** — serve slides locally with speaker view via `litoria serve`
- **Deliver** — send embedded HTML reports as email with `litoria send`

> Named after the frog genus **Litoria** — the Red-Eyed Tree Frog *Litoria chloris*, valued for its [medicinal properties](http://www.kaieteurnewsonline.com/2012/06/03/the-red-eyed-tree-frog-litoria-chloris-2/). Quarkus/Java port of the Node.js [litoria](https://github.com/ch007m/litoria), itself a rewrite of the Ruby [hyla](https://github.com/ch007m/hyla).

|                | Project Info                                        |
|----------------|-----------------------------------------------------|
| License:       | Apache-2.0                                          |
| Issue tracker: | https://github.com/ch007m/litoria-quarkus/issues    |
| JDK:           | Java >= 21                                          |

## Quick Start

```shell
./mvnw package
jbang app install --name litoria target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar

litoria init -t doc /tmp/my-doc                       # asciidoctor document
litoria generate --engine asciidoctor /tmp/my-doc

litoria init -t report /tmp/my-report                 # markdown report (minute + weekly)
litoria generate /tmp/my-report
litoria send /tmp/my-report

litoria init -t slideshow /tmp/my-slides              # RevealJS slideshow
litoria generate -r revealjs /tmp/my-slides
litoria serve /tmp/my-slides/generated/...
```

## When to use Markdown vs AsciiDoc

| Feature                 | Markdown                         | AsciiDoc                               |
|-------------------------|----------------------------------|----------------------------------------|
| **Best for**            | Reports, minutes, weekly updates | Technical docs, rich formatting        |
| **Project types**       | `report` (minute.md + report.md) | `doc` (doc.adoc)                       |
| **Syntax**              | Simple, widely known             | Richer: admonitions, tables, includes  |
| **PDF generation**      | `--embed` + `-r pdf`             | `--embed` + `-r pdf`                   |
| **Email support**       | Yes (`litoria send`)             | Yes (`litoria send`)                   |
| **Slideshows**          | RevealJS (`-r revealjs`)         | —                                      |
| **CSS/images**          | `report.css`, `quarkus-logo.png` | `font-awesome.min.css`, `litoria-chloris.jpg` |

## Documentation

- [Command Reference](docs/source/commands.adoc) — all commands, options, and arguments
- [RevealJS Slideshow Guide](docs/source/slideshow.adoc) — generating and serving slideshows
- [Slideshow Token Reference](docs/source/slideshow-tokens.adoc) — `{token}` shorthand syntax
- [Token Preview](docs/source/references/slides-tokens.html) — live RevealJS demo of all tokens
