package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontmatterServiceTest {

    FrontmatterService service;

    @BeforeEach
    void setUp() {
        service = new FrontmatterService();
    }

    @Test
    void parseFrontmatterExtractsAllFields() {
        String markdown = """
                ---
                author: John Doe
                title: Software Engineer
                email: john@example.com
                to: team@example.com
                ---

                # Report
                """;

        Map<String, String> result = service.parseFrontmatter(markdown);

        assertThat(result).containsEntry("author", "John Doe")
                .containsEntry("title", "Software Engineer")
                .containsEntry("email", "john@example.com")
                .containsEntry("to", "team@example.com");
    }

    @Test
    void parseFrontmatterHandlesMultiLineSignature() {
        String markdown = """
                ---
                author: John Doe
                signature: |
                  Cheers
                  ----
                  {author}
                  {title}
                ---

                # Report
                """;

        Map<String, String> result = service.parseFrontmatter(markdown);

        assertThat(result).containsKey("author")
                .containsKey("signature");
        assertThat(result.get("signature")).contains("Cheers")
                .contains("----")
                .contains("{author}");
    }

    @Test
    void parseFrontmatterReturnsEmptyMapWhenNoFrontmatter() {
        String markdown = "# Just a heading\nSome content";

        Map<String, String> result = service.parseFrontmatter(markdown);

        assertThat(result).isEmpty();
    }

    @Test
    void parseFrontmatterFromFile(@TempDir Path tempDir) throws IOException {
        Path mdFile = tempDir.resolve("test.md");
        Files.writeString(mdFile, """
                ---
                author: Jane Doe
                email: jane@example.com
                ---

                # Content
                """);

        Map<String, String> result = service.parseFrontmatter(mdFile);

        assertThat(result).containsEntry("author", "Jane Doe")
                .containsEntry("email", "jane@example.com");
    }

    @Test
    void stripFrontmatterRemovesFrontmatterBlock() {
        String markdown = """
                ---
                author: John Doe
                title: Engineer
                ---

                # Report heading
                Content here.
                """;

        String stripped = service.stripFrontmatter(markdown);

        assertThat(stripped).startsWith("# Report heading");
        assertThat(stripped).doesNotContain("author: John Doe");
        assertThat(stripped).doesNotContain("---");
    }

    @Test
    void stripFrontmatterWithMultiLineDoesNotBreakOnDashes() {
        String markdown = """
                ---
                author: John Doe
                signature: |
                  Cheers
                  ----
                  {author}
                ---

                # Report
                """;

        String stripped = service.stripFrontmatter(markdown);

        assertThat(stripped).startsWith("# Report");
        assertThat(stripped).doesNotContain("author: John Doe");
        assertThat(stripped).doesNotContain("signature:");
    }

    @Test
    void stripFrontmatterReturnsOriginalWhenNoFrontmatter() {
        String markdown = "# No frontmatter\nJust content";

        String result = service.stripFrontmatter(markdown);

        assertThat(result).isEqualTo(markdown);
    }

    @Test
    void validateRequiredPassesWhenAllPresent() throws IOException {
        Map<String, String> metadata = Map.of(
                "author", "John",
                "title", "Engineer",
                "email", "john@example.com",
                "to", "team@example.com");

        service.validateRequired(metadata, "author", "title", "email", "to");
    }

    @Test
    void validateRequiredThrowsWhenFieldsMissing() {
        Map<String, String> metadata = Map.of("author", "John");

        assertThatThrownBy(() ->
                service.validateRequired(metadata, "author", "title", "email", "to"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("email")
                .hasMessageContaining("to")
                .hasMessageNotContaining("author");
    }

    @Test
    void validateRequiredThrowsForBlankValues() {
        Map<String, String> metadata = Map.of(
                "author", "John",
                "title", "",
                "email", "john@example.com",
                "to", "  ");

        assertThatThrownBy(() ->
                service.validateRequired(metadata, "author", "title", "email", "to"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("to");
    }
}
