package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.test.junit.QuarkusTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class MarkdownServiceTest {

    @Inject
    MarkdownService markdownService;

    @Test
    void convertToHtmlGeneratesHtmlWithResolvedMetadata(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: John Doe
                title: Software Engineer
                email: john@example.com
                to: team@example.com
                ---

                # {author}'s report

                ## Section
                - Item 1
                """);

        Map<String, String> metadata = markdownService.convertToHtml(tempDir.toString(), "output");

        Path output = tempDir.resolve("output/report.html");
        assertThat(output).exists();

        String html = Files.readString(output);
        assertThat(html).contains("John Doe's report");
        assertThat(html).contains("<title>");
        assertThat(html).contains("report-card");
        assertThat(html).contains("<li>Item 1</li>");
    }

    @Test
    void convertToHtmlResolvesDatePlaceholder(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: John Doe
                title: Engineer
                email: john@example.com
                to: team@example.com
                ---

                # Report: {date}
                Content
                """);

        markdownService.convertToHtml(tempDir.toString(), "output");

        String html = Files.readString(tempDir.resolve("output/report.html"));
        assertThat(html).doesNotContain("{date}");
        assertThat(html).containsPattern("\\d{1,2}/\\d{1,2}/\\d{4}");
    }

    @Test
    void convertToHtmlRendersGfmTables(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: John Doe
                title: Engineer
                email: john@example.com
                to: team@example.com
                ---

                # Report

                | Header 1 | Header 2 |
                |----------|----------|
                | Cell 1   | Cell 2   |
                """);

        markdownService.convertToHtml(tempDir.toString(), "output");

        String html = Files.readString(tempDir.resolve("output/report.html"));
        assertThat(html).contains("<table>");
        assertThat(html).contains("<th>Header 1</th>");
        assertThat(html).contains("<td>Cell 1</td>");
    }

    @Test
    void convertToHtmlReturnsMetadataFromFrontmatter(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: Jane Doe
                title: Architect
                email: jane@example.com
                to: team@example.com
                subject: "Weekly report"
                ---

                # Report
                """);

        Map<String, String> metadata = markdownService.convertToHtml(tempDir.toString(), "output");

        assertThat(metadata).containsEntry("author", "Jane Doe")
                .containsEntry("email", "jane@example.com")
                .containsEntry("subject", "Weekly report")
                .containsKey("date");
    }

    @Test
    void convertToHtmlThrowsOnMissingRequiredFields(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: John Doe
                ---

                # Report
                """);

        assertThatThrownBy(() -> markdownService.convertToHtml(tempDir.toString(), "output"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("email")
                .hasMessageContaining("to");
    }

    @Test
    void convertToHtmlCopiesImages(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Path imageDir = sourceDir.resolve("image");
        Files.createDirectories(imageDir);

        Files.writeString(imageDir.resolve("logo.png"), "fake-png-data");
        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: John Doe
                title: Engineer
                email: john@example.com
                to: team@example.com
                ---

                # Report
                ![Logo](image/logo.png)
                """);

        markdownService.convertToHtml(tempDir.toString(), "output");

        assertThat(tempDir.resolve("output/image/logo.png")).exists();
    }

    @Test
    void convertToHtmlStripsImageSyntaxFromTitle(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("report.md"), """
                ---
                author: John Doe
                title: Engineer
                email: john@example.com
                to: team@example.com
                ---

                # ![Logo](image/logo.png) {author}'s report
                """);

        markdownService.convertToHtml(tempDir.toString(), "output");

        String html = Files.readString(tempDir.resolve("output/report.html"));
        assertThat(html).contains("<title>John Doe's report</title>");
        assertThat(html).doesNotContain("<title>![");
    }
}
