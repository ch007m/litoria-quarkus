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
