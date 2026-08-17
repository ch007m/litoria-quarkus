---
title: My Presentation
author: Your Name
---

{brand-bar}
{supertitle}Conference Name{/supertitle}

# {title}

{subtitle}A brief description of what this talk covers{/subtitle}

{footer}{author} &middot; {date}{/footer}

--

### Structure Tokens — syntax

```markdown
{brand-bar}
{supertitle}Conference Name{/supertitle}

# {title}

{subtitle}A brief description{/subtitle}

{footer}{author} &middot; {date}{/footer}
```

---

## Inline Colors

{brand-bar}

- {blue}Quarkus blue{/blue} for primary emphasis
- {green}Spring green{/green} for secondary emphasis
- {teal}Red Hat teal{/teal} for accents

--

### Inline Colors — syntax

```markdown
{blue}Quarkus blue{/blue} for primary emphasis
{green}Spring green{/green} for secondary emphasis
{teal}Red Hat teal{/teal} for accents
```

---

## More Colors

{brand-bar}

- {red}Red{/red} for warnings or alerts
- {orange}Orange{/orange} for highlights
- {highlight}Bold highlight{/highlight} for key points
- {dimmed}Dimmed text{/dimmed} for secondary details

--

### More Colors — syntax

```markdown
{red}Red{/red} for warnings or alerts
{orange}Orange{/orange} for highlights
{highlight}Bold highlight{/highlight} for key points
{dimmed}Dimmed text{/dimmed} for secondary details
```

---

## Table

{brand-bar}

| Criteria  | Project A         | Project B         |
|-----------|-------------------|-------------------|
| Tests     | {green}Pass{/green} | {red}Fail{/red}  |
| Perf      | {teal}Best{/teal}   | {green}Pass{/green} |
| Coverage  | {green}92%{/green}  | {red}41%{/red}   |

--

### Table — syntax

```markdown
| Criteria  | Project A           | Project B          |
|-----------|---------------------|--------------------|
| Tests     | {green}Pass{/green}  | {red}Fail{/red}   |
| Perf      | {teal}Best{/teal}    | {green}Pass{/green}|
```

---

## Two Columns

{brand-bar}
{subtitle}Side-by-side layout{/subtitle}

<div class="two-col" style="margin-top: 16px;">
<div>

### Left column

- {highlight}First point{/highlight} &middot; with context
- {highlight}Second point{/highlight} &middot; more detail
- {highlight}Third point{/highlight} &middot; additional info

</div>
<div>

### Right column

- {red}Warning item{/red}
- A medium task takes {highlight}2-4 weeks{/highlight}
- Hard to estimate ROI
- Teams defer &rarr; technical debt grows

</div>
</div>

--

### Two Columns — syntax

```markdown
<div class="two-col">
<div>

### Left column

- {highlight}First point{/highlight}
- {highlight}Second point{/highlight}

</div>
<div>

### Right column

- {red}Warning item{/red}
- Another point

</div>
</div>
```

Note:
The two-col layout uses CSS grid. It stays as HTML because it needs child structure.
A three-col variant is also available: `<div class="three-col">`.

---

## Callouts

{brand-bar}

{callout}Default callout — neutral gray border.{/callout}

{callout-teal}Teal callout — tips and recommendations.{/callout-teal}

{callout-red}Red callout — warnings and breaking changes.{/callout-red}

--

### Callouts — syntax

```markdown
{callout}Default callout — neutral gray border.{/callout}
{callout-teal}Teal callout — tips.{/callout-teal}
{callout-red}Red callout — warnings.{/callout-red}
{callout-orange}Orange callout — notices.{/callout-orange}
```

---

## Flow Diagram

{brand-bar}

<div class="flow" style="justify-content: center;">
{flow-step}Design{/flow-step}
{flow-arrow}
{flow-step}Implement{/flow-step}
{flow-arrow}
{flow-step}Test{/flow-step}
{flow-arrow}
{flow-step}Deploy{/flow-step}
</div>

--

### Flow Diagram — syntax

```markdown
<div class="flow" style="justify-content: center;">
{flow-step}Design{/flow-step}
{flow-arrow}
{flow-step}Implement{/flow-step}
</div>
```

---

## Image

{brand-bar}

{img src="image/quarkus.png" width="60%" center}

--

### Image — syntax

```markdown
{img src="image/quarkus.png" width="60%" center}
```

Note:
Parameters: src (required), width, alt, center/right/left, rounded

---

## Code

{brand-bar}

```java
@Path("/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus!";
    }
}
```

Note:
Use fenced code blocks with a language tag for syntax highlighting.

---

## Video

{brand-bar}

{video src="image/your-video.mp4" controls width="90%" center rounded}

--

### Video — syntax

```markdown
{video src="image/demo.mp4" controls width="90%" center rounded}
```

Note:
Parameters: src (required), controls, autoplay, loop, muted, width, center/right/left, rounded

---

## Summary

{brand-bar}

- Point 1 &middot; tested and validated
- Point 2 &middot; ready for production
- Next step: your feedback

{footer}{author} &middot; {date}{/footer}

---

## Questions?

{brand-bar}

{footer}{author} &middot; {date}{/footer}

Note:
Thank the audience and open for questions.
