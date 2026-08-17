# LITORIA

## NAME

litoria -- Content management tool to generate HTML, PDF, and RevealJS slideshows from AsciiDoc or Markdown, and send reports via email

## SYNOPSIS

```
litoria [-h] [COMMAND]
```

## OPTIONS

### `-h`, `--help`

Display this help and exit

## COMMANDS

- [**init**](litoria-init.md) -- Create a new project with adoc or markdown files
- [**generate**](litoria-generate.md) -- Generate HTML, PDF, or RevealJS slideshow from source files
- [**send**](litoria-send.md) -- Send HTML content as email via SMTP
- [**serve**](litoria-serve.md) -- Serve generated slides over HTTP for RevealJS speaker view


# LITORIA-INIT

## NAME

litoria init -- Create a new project with adoc or markdown files

## SYNOPSIS

```
litoria init [-f] [-t=<type>] [-e=<engine>] [-l=<flavor>] <projectDir>
```

## OPTIONS

### `-f`, `--force`

Force use of an existing folder

### `-t`, `--type=<type>`

Type of project: simple, report, slideshow

Default: `simple`

### `-e`, `--engine=<engine>`

Template engine: markdown or asciidoctor

Default: `markdown`

### `-l`, `--flavor=<flavor>`

Slideshow flavor: default or tokens

Default: `default`

## ARGUMENTS

### `<projectDir>`

Project directory path (defaults to current directory)


# LITORIA-GENERATE

## NAME

litoria generate -- Generate HTML, PDF, or RevealJS slideshow from source files

## SYNOPSIS

```
litoria generate [-e] [-r=<rendering>] [-t=<theme>] [-d=<dest>] <projectDir>
```

## OPTIONS

### `-r`, `--rendering=<rendering>`

Rendering type: html, pdf, or revealjs

Default: `html`

### `-t`, `--theme=<theme>`

RevealJS theme: white, black, beige, blood, dracula, league, moon, night, serif, simple, sky, solarized

Default: `white`

### `-e`, `--embed`

Embed styles and images into a self-contained HTML after generation

### `-d`, `--dest=<dest>`

Custom destination directory (overrides config, no timestamp subfolder)

## ARGUMENTS

### `<projectDir>`

Project directory path


# LITORIA-SEND

## NAME

litoria send -- Send HTML content as email via SMTP

## SYNOPSIS

```
litoria send [-f=<file>] <projectDir>
```

## OPTIONS

### `-f`, `--file=<file>`

Name of the HTML file to send (without extension)

Default: `report`

## ARGUMENTS

### `<projectDir>`

Project directory path


# LITORIA-SERVE

## NAME

litoria serve -- Serve generated slides over HTTP for RevealJS speaker view

## SYNOPSIS

```
litoria serve [-p=<port>] <directory>
```

## OPTIONS

### `-p`, `--port=<port>`

HTTP port

Default: `8080`

## ARGUMENTS

### `<directory>`

Directory containing generated slides


