---
title: Litoria Quarkus
layout: page
---

Command Line Tool to manage AsciiDoc and Markdown projects (create, generate HTML content), embed CSS & images, and send reports as email — built on [Quarkus](https://quarkus.io/) with [Aesh](https://quarkus.io/guides/aesh).

> **Note:** The project is named after the frog genus **Litoria**, which includes the Red-Eyed Tree Frog *Litoria chloris* — a species valued for its [medicinal properties](http://www.kaieteurnewsonline.com/2012/06/03/the-red-eyed-tree-frog-litoria-chloris-2/). This project is the Quarkus/Java port of the Node.js [litoria](https://github.com/ch007m/litoria), itself a rewrite of the Ruby [hyla](https://github.com/ch007m/hyla) tool.

|                | Project Info                                        |
|----------------|-----------------------------------------------------|
| Source:        | [github.com/ch007m/litoria-quarkus](https://github.com/ch007m/litoria-quarkus) |
| License:       | Apache-2.0                                          |
| Issue tracker: | [Issues](https://github.com/ch007m/litoria-quarkus/issues) |
| JDK:           | Java >= 21                                          |

## Documentation

- [Command Reference](commands) — full CLI usage for all commands
- [RevealJS Slideshow Guide](slideshow) — generating and serving slideshows
- [Slideshow Token Reference](slideshow-tokens) — `{token}` shorthand syntax
- [Token Slideshow Preview](/slides-tokens.html) — live RevealJS demo of all tokens

## Installation

Build the project and create an uber-jar:

```shell
./mvnw package
```

The application is then runnable using:

```shell
java -jar target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar
```

### Register with JBang

[JBang](https://www.jbang.dev/) can register the uber-jar as a system-wide command so you can run `litoria` from anywhere.

```shell
./mvnw package
jbang app install --name litoria target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar
```

## Commands

```shell
litoria init -t report /path/to/project        # create a report project
litoria init -t slideshow /path/to/slides       # create a slideshow project
litoria generate /path/to/project               # generate HTML
litoria generate -r pdf --embed /path/to/project # generate embedded PDF
litoria generate -r revealjs /path/to/slides    # generate RevealJS slides
litoria generate --embed /path/to/project       # self-contained HTML for email
litoria send /path/to/project                   # send report via email
litoria serve /path/to/generated/slides         # serve slides for speaker view
```

For the full command reference, see [commands](commands).

## Configuration

Configuration comes from three sources (in priority order):

1. **YAML frontmatter** in Markdown files — per-report metadata (author, title, email, recipient, subject, signature)
2. **Environment variables** — SMTP credentials and sensitive data
3. **`application.properties`** — application defaults (generator settings, SMTP host/port, asciidoctor attributes)

### Key properties

| Property                       | Default          | Description                        |
|--------------------------------|------------------|------------------------------------|
| `litoria.generator.engine`     | `markdown`       | Template engine: markdown or asciidoctor |
| `litoria.generator.source`     | `./source`       | Source file(s) directory           |
| `litoria.generator.destination`| `generated`      | Output directory base path         |
| `litoria.generator.footer`     | `true`           | Enable/disable the footer          |
| `litoria.report.mail.from`     | `{email}`        | Sender address (from frontmatter)  |

### SMTP configuration

SMTP credentials are configured via environment variables:

```shell
export LITORIA_SMTP_HOST=smtp.gmail.com
export LITORIA_SMTP_PORT=587
export LITORIA_SMTP_USER=your-email@gmail.com
export LITORIA_SMTP_PASS=your-app-password
```

OAuth2 is also supported — see the [README](https://github.com/ch007m/litoria-quarkus#smtp-configuration-environment-variables) for details.

## References

| Library                          | Link                                                       |
|----------------------------------|-----------------------------------------------------------:|
| Quarkus framework                | https://quarkus.io/                                        |
| Aesh CLI framework               | https://quarkus.io/guides/aesh                             |
| CommonMark (Markdown parser)     | https://github.com/commonmark/commonmark-java              |
| RevealJS (HTML slideshows)       | https://revealjs.com/                                      |
