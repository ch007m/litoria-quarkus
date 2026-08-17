# RevealJS Slideshow Generation

Generate [RevealJS](https://revealjs.com/) slideshows from Markdown using `litoria generate -r revealjs`.

See [slideshow-tokens.md](slideshow-tokens.md) for the full token reference and [slides-tokens.html](slides-tokens.html) for a live preview.

## Quick Start

```shell
litoria init -t slideshow /path/to/slides
litoria generate -r revealjs /path/to/slides
litoria serve /path/to/generated/slides
```

## Usage

```shell
litoria generate -r revealjs ./slides
litoria generate -r revealjs -t dracula ./slides
```

The `-t` option selects the RevealJS theme (default: `white`). Available themes: `white`, `black`, `beige`, `blood`, `dracula`, `league`, `moon`, `night`, `serif`, `simple`, `sky`, `solarized`.

## Markdown Conventions

The source Markdown uses:
- `---` to separate horizontal slides
- `--` for vertical (nested) slides
- `Note:` for speaker notes

YAML frontmatter (`title`, `author`, `date`) is resolved into `{placeholder}` tokens in the content. Images referenced as `image/...` are copied to the output directory automatically.

**Example:**

```markdown
---
title: My Presentation
author: Your Name
date: August 2026
---

# {title}

{author} · {date}

Note:
Welcome everyone to the presentation.

---

## Slide Two

- Point A
- Point B

--

### Vertical Sub-slide

Additional detail without cluttering the main slide.
```

## Templates

Two templates are available when scaffolding with `litoria init -t slideshow`:

| Template | Description |
|---|---|
| `slides.md` | Plain Markdown without tokens |
| `slides-tokens.md` | Demonstrates the [token syntax](slideshow-tokens.md) |

## CSS Files

| File | Description |
|---|---|
| `css/slides.css` | Base RevealJS theme overrides (fonts, headings, tables) |
| `css/tokens.css` | Token CSS classes + complex layout classes |

## Token Syntax

Slide Markdown files support a `{token}` shorthand that replaces common HTML/CSS patterns. The token name maps directly to a CSS class in `tokens.css` — if the class exists, the token works.

| Category | Examples | Description |
|----------|---------|-------------|
| Colors | `{blue}text{/blue}`, `{green}`, `{teal}`, `{red}`, `{orange}` | Colored bold text |
| Emphasis | `{highlight}text{/highlight}`, `{dimmed}text{/dimmed}` | Bold dark or muted gray |
| Badges | `{pass}OK{/pass}`, `{fail}ERR{/fail}`, `{best}TOP{/best}` | Status badges for tables |
| Blocks | `{brand-bar}`, `{subtitle}text{/subtitle}`, `{supertitle}`, `{footer}` | Slide-level elements |
| Callouts | `{callout}text{/callout}`, `{callout-red}`, `{callout-teal}`, `{callout-orange}` | Colored callout boxes |
| Flow | `{flow-step}Step{/flow-step}`, `{flow-arrow}` | Process diagrams |
| Images | `{img src="image/logo.png" width="80%" center rounded}` | Image with size/position |
| Video | `{video src="demo.mp4" controls width="90%" center}` | Video with [RevealJS media](https://revealjs.com/media/) attributes |

Complex layouts (grids, cards, stat rows) remain as HTML.

For the full token reference including all parameters, resolution order, and before/after examples, see [slideshow-tokens.md](slideshow-tokens.md).

## Speaker View

RevealJS includes a [speaker view](https://revealjs.com/speaker-view/) that shows the current slide, next slide, speaker notes, and a timer. It requires HTTP serving (not `file://`).

```shell
litoria generate -r revealjs /path/to/slides
litoria serve /path/to/slides/generated/2026-08-17_10-30
```

Open `http://localhost:8080` and press **S** to open the speaker view.

| Option | Default | Description |
|--------|---------|-------------|
| `-p` / `--port` | `8080` | HTTP port |

Press `Ctrl+C` to stop the server.
