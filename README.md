# <img src="src/main/resources/templates/asciidoctor/image/litoria-chloris.jpg" width="80"> Quarkus litoria reporting tool and more

Command Line Tool to manage AsciiDoc and Markdown projects (create, generate HTML content), embed CSS & images, and send reports as email — built on [Quarkus](https://quarkus.io/) with [Aesh](https://quarkus.io/guides/aesh).

> **Note:** The project is named after the frog genus **Litoria**, which includes the Red-Eyed Tree Frog *Litoria chloris* — a species valued for its [medicinal properties](http://www.kaieteurnewsonline.com/2012/06/03/the-red-eyed-tree-frog-litoria-chloris-2/). This project is the Quarkus/Java port of the Node.js [litoria](https://github.com/ch007m/litoria), itself a rewrite of the Ruby [hyla](https://github.com/ch007m/hyla) tool.

|                | Project Info                                        |
|----------------|-----------------------------------------------------|
| License:       | Apache-2.0                                          |
| Issue tracker: | https://github.com/ch007m/litoria-quarkus/issues    |
| JDK:           | Java >= 21                                          |

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

First, build the uber-jar:

```shell
./mvnw package
```

Then register it as an app:

```shell
jbang app install --name litoria target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar
```

You can now use `litoria` directly:

```shell
litoria init -t report /tmp/my-report
litoria init -t report -e asciidoctor /tmp/my-report
litoria generate /tmp/my-report
litoria generate --embed /tmp/my-report
litoria send /tmp/my-report
litoria send -f minute /tmp/my-report
```

To uninstall:

```shell
jbang app uninstall litoria
```

## Usage

```shell
litoria <cmd> [options]
```

where `<cmd>` corresponds to one of the available commands: `init`, `generate`, `send`.

### Configuration

Configuration comes from three sources (in priority order):

1. **YAML frontmatter** in Markdown files — per-report metadata (author, title, email, recipient, subject, signature)
2. **Environment variables** — SMTP credentials and sensitive data
3. **`application.properties`** — application defaults (generator settings, SMTP host/port, asciidoctor attributes)

#### Report metadata (YAML frontmatter)

For **markdown** projects, each `.md` file starts with a YAML frontmatter block between `---` delimiters:

```yaml
---
author: Charles Moulliard
title: Sr. Pr. Software Engineer
email: cmoullia@redhat.com
to: team@redhat.com
subject: "{author}'s weekly report : {date}"
signature: |
  Cheers
  ----
  {author}
  {title}
---

# ![Quarkus](image/quarkus-logo.png) {author}'s report: {date}
...
```

| Field     | Description                                                     | Required |
|-----------|-----------------------------------------------------------------|:--------:|
| author    | Author name, used in templates and subject                      |    x     |
| title     | Job title, used in signature                                    |    x     |
| email     | Email address, used as default sender (`from`)                  |    x     |
| to        | Recipient email address(es)                                     |    x     |
| subject   | Email subject (supports `{author}`, `{date}` placeholders)     |          |
| signature | Appended after the email body (supports `{author}`, `{title}`)  |          |

Template placeholders (`{author}`, `{title}`, `{email}`, `{date}`) are resolved from the frontmatter values. The `{date}` variable is autofilled with today's date if not set. Signatures use YAML multi-line syntax (`|`) — newlines are converted to `<br/>` in the email.

For **asciidoctor** projects, report metadata is set via system properties or environment variables (see below).

#### Application properties

Default settings are defined in `application.properties` (bundled with the app). Override any property at runtime using system properties or environment variables:

```shell
# System property
java -Dlitoria.generator.engine=asciidoctor -jar litoria.jar generate /path

# Environment variable (replace dots with underscores, uppercase)
LITORIA_GENERATOR_ENGINE=asciidoctor litoria generate /path
```

Key properties and their defaults:

| Property                       | Default          | Description                        |
|--------------------------------|------------------|------------------------------------|
| `litoria.generator.engine`     | `markdown`       | Template engine: markdown or asciidoctor |
| `litoria.generator.source`     | `./source`       | Source file(s) directory           |
| `litoria.generator.destination`| `generated`      | Output directory base path         |
| `litoria.generator.footer`     | `true`           | Enable/disable the footer in generated HTML |
| `litoria.generator.footer-text`| `Generated by <a href="...">litoria</a>` | Footer HTML content (supports links and inline HTML) |
| `litoria.report.mail.from`     | `{email}`        | Sender address (resolved from frontmatter `email`) |

#### SMTP configuration (environment variables)

SMTP credentials are configured via environment variables to keep sensitive data out of config files:

```shell
# Required
export LITORIA_SMTP_HOST=smtp.gmail.com
export LITORIA_SMTP_PORT=587
export LITORIA_SMTP_USER=your-email@gmail.com

# Auth option 1: App Password
export LITORIA_SMTP_PASS=your-app-password

# Auth option 2: OAuth2 (instead of LITORIA_SMTP_PASS)
export LITORIA_SMTP_OAUTH2_CLIENT_ID=your-client-id
export LITORIA_SMTP_OAUTH2_CLIENT_SECRET=your-client-secret
export LITORIA_SMTP_OAUTH2_REFRESH_TOKEN=your-refresh-token
```

Additional SMTP properties can be overridden if needed:

| Environment variable                   | Default          | Description                |
|----------------------------------------|------------------|----------------------------|
| `LITORIA_SMTP_HOST`                    |`smtp.gmail.com`  | SMTP server hostname       |
| `LITORIA_SMTP_PORT`                    | `587`            | SMTP port                  |
| `LITORIA_SMTP_USER`                    | —                | SMTP username (must be the email address used to create the OAuth2 Client ID) |
| `LITORIA_SMTP_PASS`                    | —                | App password (only needed when OAuth2 is not used) |
| `LITORIA_SMTP_OAUTH2_CLIENT_ID`        | —                | OAuth2 Client ID           |
| `LITORIA_SMTP_OAUTH2_CLIENT_SECRET`    | —                | OAuth2 Client Secret       |
| `LITORIA_SMTP_OAUTH2_REFRESH_TOKEN`    | —                | OAuth2 Refresh Token       |

**Gmail authentication** (see [nodemailer Gmail guide](https://nodemailer.com/guides/using-gmail)):

* **App Password** (simpler): Enable 2-Step Verification, then generate an app password at https://myaccount.google.com/apppasswords. Set `LITORIA_SMTP_USER` and `LITORIA_SMTP_PASS`.
* **OAuth2** (recommended): Set `LITORIA_SMTP_USER`, `LITORIA_SMTP_OAUTH2_CLIENT_ID`, `LITORIA_SMTP_OAUTH2_CLIENT_SECRET`, and `LITORIA_SMTP_OAUTH2_REFRESH_TOKEN`. See [Sending mail with Gmail using XOAUTH2](https://masashi-k.blogspot.com/2013/06/sending-mail-with-gmail-using-xoauth2.html) for a step-by-step guide on creating the OAuth2 client and obtaining the refresh token.

## Commands

```shell
litoria init -t report /path/to/project        # create a report project
litoria init -t slideshow /path/to/slides       # create a slideshow project
litoria generate /path/to/project               # generate HTML
litoria generate -r pdf --embed /path/to/project # generate embedded PDF
litoria generate -r revealjs /path/to/slides    # generate RevealJS slides
litoria generate --embed /path/to/project       # self-contained HTML for email
litoria send /path/to/project                   # send report via email
```

For the full command reference (options, defaults, arguments), see [docs/commands.md](docs/commands.md).

For RevealJS slideshow documentation, see [docs/slideshow.md](docs/slideshow.md) and [docs/slideshow-tokens.md](docs/slideshow-tokens.md). Preview the token slideshow live: [docs/slides-tokens.html](https://ch007m.github.io/litoria-quarkus/slides-tokens.html).

To regenerate the commands documentation after changing command definitions:

```shell
java -Dquarkus.log.level=OFF -Dquarkus.banner.enabled=false \
  -jar target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar --help=md > docs/commands.md
```

## Running in dev mode

You can run the application in dev mode with live coding using:

```shell
./mvnw quarkus:dev
```

> **Note:** Quarkus ships with a Dev UI, available in dev mode at <http://localhost:8080/q/dev/>.

## Creating a native executable

You can create a native executable using:

```shell
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, build in a container:

```shell
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Then run: `./target/litoria-quarkus-1.0.0-SNAPSHOT-runner`

For more on native executables, see the [Quarkus Maven tooling guide](https://quarkus.io/guides/maven-tooling).

## References

| Library                          | Link                                                       |
|----------------------------------|-----------------------------------------------------------:|
| Quarkus framework                | https://quarkus.io/                                        |
| Aesh CLI framework               | https://quarkus.io/guides/aesh                             |
| CommonMark (Markdown parser)     | https://github.com/commonmark/commonmark-java              |
| AsciiDoc processor (AsciidoctorJ)| https://github.com/asciidoctor/asciidoctorj                |
| HTML parser (Jsoup)              | https://github.com/jhy/jsoup                               |
| Quarkus Mailer (SMTP)            | https://quarkus.io/guides/mailer                           |
| YAML frontmatter (SnakeYAML)     | https://github.com/snakeyaml/snakeyaml                     |
| PDF generation (openhtmltopdf)   | https://github.com/nicehash/openhtmltopdf                  |
| RevealJS (HTML slideshows)       | https://revealjs.com/                                      |
