package io.litoria.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.litoria.config.LitoriaConfig;

@ApplicationScoped
public class SlideshowService {

    private static final String DEFAULT_CSS_RESOURCE = "templates/slideshow/css/slides.css";
    private static final String REVEALJS_VERSION = "6.0.1";

    @Inject
    LitoriaConfig config;

    @Inject
    FrontmatterService frontmatterService;

    public Map<String, String> convertToHtml(String projectDir, String destination) throws IOException {
        return convertToHtml(projectDir, destination, "white");
    }

    public Map<String, String> convertToHtml(String projectDir, String destination, String theme) throws IOException {
        String source = config.generator().source();

        Path sourcePath = Path.of(projectDir, source);
        Path destPath = Path.of(projectDir, destination);
        Files.createDirectories(destPath);

        String css = loadCss(projectDir);
        Map<String, String> lastMetadata = Map.of();

        if (Files.isDirectory(sourcePath)) {
            try (Stream<Path> mdFiles = Files.list(sourcePath)
                    .filter(p -> p.toString().endsWith(".md"))) {
                for (Path mdFile : mdFiles.toList()) {
                    String outputName = getFileNameWithoutExtension(mdFile) + ".html";
                    lastMetadata = convertFile(mdFile, destPath.resolve(outputName), css, theme);
                }
            }
            copyImages(sourcePath, destPath);
        } else if (Files.isRegularFile(sourcePath)) {
            String outputName = getFileNameWithoutExtension(sourcePath) + ".html";
            lastMetadata = convertFile(sourcePath, destPath.resolve(outputName), css, theme);
            copyImages(sourcePath.getParent(), destPath);
        } else {
            throw new IOException("Source not found: " + sourcePath);
        }

        return lastMetadata;
    }

    private Map<String, String> convertFile(Path input, Path output, String css, String theme) throws IOException {
        String rawMarkdown = Files.readString(input);

        Map<String, String> metadata = frontmatterService.parseFrontmatter(rawMarkdown);

        Map<String, String> resolvedMetadata = new HashMap<>(metadata);
        if (!resolvedMetadata.containsKey("date")) {
            resolvedMetadata.put("date", LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("M/d/yyyy")));
        }

        String markdown = frontmatterService.stripFrontmatter(rawMarkdown);
        markdown = resolveMetadata(markdown, resolvedMetadata);

        String title = resolvedMetadata.getOrDefault("title", "Presentation");
        String fullHtml = wrapInRevealJsDocument(markdown, title, css, theme);
        Files.writeString(output, fullHtml);

        return resolvedMetadata;
    }

    private String loadCss(String projectDir) throws IOException {
        if (config.generator().css().isPresent()) {
            Path externalCss = Path.of(projectDir, config.generator().css().get());
            if (Files.exists(externalCss)) {
                return Files.readString(externalCss);
            }
        }
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(DEFAULT_CSS_RESOURCE)) {
            if (is == null) {
                throw new IOException("Default CSS resource not found: " + DEFAULT_CSS_RESOURCE);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String resolveMetadata(String text, Map<String, String> metadata) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            text = text.replace("{" + entry.getKey() + "}", value);
        }
        return text;
    }

    private void copyImages(Path sourceDir, Path destDir) throws IOException {
        Path imageDir = sourceDir.resolve("image");
        if (!Files.isDirectory(imageDir)) {
            return;
        }
        Path destImageDir = destDir.resolve("image");
        Files.createDirectories(destImageDir);
        try (Stream<Path> images = Files.list(imageDir)) {
            for (Path img : images.toList()) {
                if (Files.isRegularFile(img)) {
                    Files.copy(img, destImageDir.resolve(img.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String wrapInRevealJsDocument(String markdown, String title, String css, String theme) {
        String v = REVEALJS_VERSION;
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/reveal.css">
                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/theme/%s.css">
                    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/plugin/highlight/monokai.css">
                    <style>
                %s
                    </style>
                </head>
                <body>
                <div class="reveal">
                    <div class="slides">
                        <section data-markdown data-separator="^---$" data-separator-vertical="^--$" data-separator-notes="^Note:">
                            <textarea data-template>
                %s
                            </textarea>
                        </section>
                    </div>
                </div>
                <script src="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/reveal.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/plugin/highlight.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/plugin/markdown.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/reveal.js@%s/dist/plugin/notes.js"></script>
                <script>
                    Reveal.initialize({
                        hash: true,
                        slideNumber: true,
                        transition: 'slide',
                        width: 1280,
                        height: 800,
                        margin: 0.04,
                        plugins: [RevealHighlight, RevealMarkdown, RevealNotes]
                    });
                </script>
                </body>
                </html>
                """.formatted(title, v, v, theme, v, css, markdown, v, v, v, v);
    }

    private String getFileNameWithoutExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
