# <img src="src/main/resources/templates/asciidoctor/image/litoria-chloris.jpg" width="80"> litoria-quarkus

Command Line Tool to manage AsciiDoc and Markdown projects (create, generate HTML content), embed CSS & images, and send reports as email — built on [Quarkus](https://quarkus.io/) with [Aesh](https://quarkus.io/guides/aesh).

|                 | Project Info                                        |
| --------------- |-----------------------------------------------------|
| License:        | Apache-2.0                                          |
| Build:          | Maven                                               |
| Documentation:  | N/A                                                 |
| Issue tracker:  | https://github.com/ch007m/litoria-quarkus/issues    |
| Engines:        | Java >= 21                                          |

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

All project settings are defined in a single `config.yml` file. The structure depends on the engine selected.

**Markdown** (default):

```yaml
generator:
  engine: "markdown"
  source: "./source"
  destination: "generated"
  image: "${source}/image"

report:
  author: "First & Last Name"
  title: "Your Title"
  email: "user@domain"
  signature: "Cheers{break}----{break}{author}{break}{title}"

  mail:
    from: "{email}"
    to: "recipient@domain"
    subject: "{author}'s weekly report : {date}"
```

**AsciiDoc**:

```yaml
generator:
  engine: "asciidoctor"
  source: "./source"
  destination: "generated"
  image: "${source}/image"

report:
  author: "First & Last Name"
  title: "Your Title"
  email: "user@domain"
  signature: "Cheers{break}----{break}{author}{break}{title}"

  mail:
    from: "{email}"
    to: "recipient@domain"
    subject: "{author}'s weekly report : {date}"

asciidoctor:
  attributes:
    showtitle: ''
    stylesheet: 'foundation.css'
    stylesdir: 'css'
    nofooter: 'yes'
    icons: 'font'
  options:
    doctype: 'article'
    safe: 'unsafe'
```

Config values support `${key}` placeholders that resolve against sibling fields within the same section (e.g., `${source}` in `image` resolves to the value of `generator.source`).

Template placeholders (`{author}`, `{title}`, `{email}`, `{date}`, `{break}`) are resolved from the `report` section. The `{date}` variable is auto-filled with today's date if not set. Use `{break}` for line breaks in signatures.

## Commands

### init

Create a project containing a default `config.yml` and template(s):

```shell
litoria init /path/to/project
litoria init -t report /path/to/project
litoria init -t report -e asciidoctor /path/to/project
```

Several project types are supported via the `-t` option (default: `simple`):

* **simple** — a simple adoc example
* **report** — a **report** template (markdown by default, or adoc with `-e asciidoctor`)
* **slideshow** — RevealJS slideshow project *(not yet supported)*

The `-e` option selects the template engine (default: `markdown`):

* **markdown** — uses [CommonMark](https://commonmark.org/) to generate HTML
* **asciidoctor** — uses [AsciidoctorJ](https://github.com/asciidoctor/asciidoctorj) to generate HTML

Use `-f` to force creation in an existing non-empty directory:

```shell
litoria init -f /path/to/project
```

### generate

Render the source file(s) into HTML. The engine is selected automatically based on `generator.engine` in the config. Each run creates a timestamped subfolder under the destination directory (e.g., `generated/2026-08-11_14-30`):

```shell
litoria generate                      # uses current directory
litoria generate ./report/quarkus
```

To use a specific config file, pass it with `-c`:

```shell
litoria generate -c my-config.yml
```

To override the destination directory (no timestamp subfolder), use `-d`:

```shell
litoria generate -d ./custom-output
```

To embed CSS and images as base64 into self-contained HTML after generation, use the `--embed` (`-e`) flag:

```shell
litoria generate --embed
litoria generate -e -c config.yml
```

The `--embed` option inlines CSS from linked stylesheets and `<style>` blocks into `style` attributes, converts image references to embedded base64 data URIs, and embeds Font Awesome icons (downloading the woff2 font from CDN and encoding it as base64) — producing fully self-contained, email-ready HTML that renders correctly without any network access.

### send

Send the generated HTML content as an email via SMTP:

```shell
litoria send ./report/quarkus
litoria send -f minute ./report/quarkus
```

Use `-f` / `--file` to specify which HTML file to send (without the `.html` extension). Defaults to `report`.

The `send` command automatically finds the latest timestamped subfolder and looks for the specified HTML file to use as the email body. Run `generate --embed` before sending.

Configure the `smtp` section in your `config.yml`. Two authentication methods are supported:

**App Password:**

```yaml
smtp:
  host: "smtp.gmail.com"
  port: 587
  secure: false
  requireTLS: true
  user: "{email}"
  pass: "your-app-password"
```

**OAuth2:**

```yaml
smtp:
  host: "smtp.gmail.com"
  port: 587
  secure: false
  requireTLS: true
  user: "{email}"
  oauth2:
    clientId: "your-client-id"
    clientSecret: "your-client-secret"
    refreshToken: "your-refresh-token"
```

**smtp fields:**

| Field            | Description                                     | Required |
|------------------|-------------------------------------------------|:--------:|
| host             | SMTP server hostname                            |    x     |
| port             | SMTP port (587 for TLS, 465 for SSL)            |    x     |
| secure           | `true` for port 465, `false` for 587            |    x     |
| requireTLS       | Force STARTTLS upgrade                          |          |
| tls              | TLS options (e.g., `rejectUnauthorized: false`)  |          |
| user             | Email account username                          |    x     |
| pass             | App Password                                    |          |

**smtp.oauth2 fields** (alternative to `pass`):

| Field            | Description                                     | Required |
|------------------|-------------------------------------------------|:--------:|
| clientId         | Google OAuth2 Client ID                         |    x     |
| clientSecret     | Google OAuth2 Client Secret                     |    x     |
| refreshToken     | Google OAuth2 Refresh Token                     |    x     |

**report fields:**

| Field     | Description                                                     | Required |
|-----------|-----------------------------------------------------------------|:--------:|
| author    | Author name, used in templates and subject                      |    x     |
| title     | Job title, used in signature                                    |          |
| email     | Email address, used in `mail.from` and `smtp.user`              |    x     |
| date      | Report date (auto-filled with today if not set)                 |          |
| signature | Appended after the body (supports `{variable}` and `{break}`)  |          |

**report.mail fields:**

| Field     | Description                                                     | Required |
|-----------|-----------------------------------------------------------------|:--------:|
| from      | Sender email address (supports `{variable}` placeholders)       |    x     |
| to        | Recipient email address(es)                                     |    x     |
| subject   | Email subject (supports `{variable}` placeholders)              |    x     |
| body      | Email body as inline HTML (supports `{variable}` and `{break}`) |          |

If `body` is not set, the embedded HTML file is used as the email body.

**Gmail authentication** (see [nodemailer Gmail guide](https://nodemailer.com/guides/using-gmail)):

* **App Password** (simpler): Enable 2-Step Verification, then generate an app password at https://myaccount.google.com/apppasswords. Set `user` and `pass`.
* **OAuth2** (recommended): Set `user`, `clientId`, `clientSecret`, and `refreshToken`.

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

## Extra information

> This project is the Quarkus/Java port of the Node.js [litoria](https://github.com/ch007m/litoria) tool,
> which itself is a refactoring of the Ruby [hyla tool](https://github.com/ch007m/hyla).

The project name corresponds to the frog genus **Litoria** which contains many species like the Red Eye Tree Frog **Litoria Chloris**, valuable for humans due to its [medical capacities](http://www.kaieteurnewsonline.com/2012/06/03/the-red-eyed-tree-frog-litoria-chloris-2/).

## References

| Library                          | Link                                                       |
|----------------------------------|-----------------------------------------------------------:|
| Quarkus framework                | https://quarkus.io/                                        |
| Aesh CLI framework               | https://quarkus.io/guides/aesh                             |
| CommonMark (Markdown parser)     | https://github.com/commonmark/commonmark-java              |
| AsciiDoc processor (AsciidoctorJ)| https://github.com/asciidoctor/asciidoctorj                |
| HTML parser (Jsoup)              | https://github.com/jhy/jsoup                               |
| SMTP email (Angus Mail)          | https://eclipse-ee4j.github.io/angus-mail/                 |
| YAML config (SnakeYAML)          | https://github.com/snakeyaml/snakeyaml                     |
