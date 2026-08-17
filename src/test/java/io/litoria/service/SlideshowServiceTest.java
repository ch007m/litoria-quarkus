package io.litoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

@QuarkusTest
class SlideshowServiceTest {

    @Inject
    SlideshowService slideshowService;

    @TempDir
    Path tempDir;

    @Test
    void resolvesTokensInGeneratedSlideshow() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("slides.md"), """
                ---
                title: Token Test
                author: Tester
                ---

                {brand-bar}
                {supertitle}Conference{/supertitle}

                # {title}

                {subtitle}A {blue}colored{/blue} subtitle{/subtitle}

                {footer}{author} &middot; {date}{/footer}

                ---

                ## Slide 2

                - {highlight}Important{/highlight}
                - {pass}OK{/pass} vs {fail}Bad{/fail}

                {callout-teal}This is a callout{/callout-teal}
                """);

        slideshowService.convertToHtml(tempDir.toString(), "output");

        String html = Files.readString(tempDir.resolve("output/slides.html"));
        assertThat(html).contains("<div class=\"brand-bar\"></div>");
        assertThat(html).contains("<p class=\"supertitle\">Conference</p>");
        assertThat(html).contains("<span class=\"blue\">colored</span>");
        assertThat(html).contains("<p class=\"subtitle\">");
        assertThat(html).contains("<span class=\"highlight\">Important</span>");
        assertThat(html).contains("<span class=\"pass\">OK</span>");
        assertThat(html).contains("<span class=\"fail\">Bad</span>");
        assertThat(html).contains("<div class=\"callout teal-callout\">This is a callout</div>");
        String slideContent = html.substring(html.indexOf("<textarea data-template>"));
        assertThat(slideContent).doesNotContain("{brand-bar}");
        assertThat(slideContent).doesNotContain("{/blue}");
        assertThat(slideContent).doesNotContain("{highlight}");
    }

    @Test
    void generatesSlideshowHtmlFromMarkdown() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("slides.md"), """
                ---
                title: Test Presentation
                author: Test Author
                ---

                # {title}

                {author}

                ---

                ## Slide 2

                - Point A
                - Point B
                """);

        slideshowService.convertToHtml(tempDir.toString(), "output");

        Path outputDir = tempDir.resolve("output");
        assertThat(outputDir).isDirectory();

        Path slidesHtml = outputDir.resolve("slides.html");
        assertThat(slidesHtml).exists();

        String html = Files.readString(slidesHtml);
        assertThat(html).contains("reveal.js");
        assertThat(html).contains("RevealMarkdown");
        assertThat(html).contains("data-markdown");
        assertThat(html).contains("Test Presentation");
        assertThat(html).contains("Test Author");
        assertThat(html).doesNotContain("{title}");
        assertThat(html).doesNotContain("{author}");
    }
}
