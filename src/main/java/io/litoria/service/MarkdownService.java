package io.litoria.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

@ApplicationScoped
public class MarkdownService {

    private static final String DEFAULT_CSS_RESOURCE = "templates/markdown/css/report.css";
    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile("!\\[[^]]*]\\([^)]*\\)\\s*");

    @Inject
    ConfigService configService;

    public void convertToHtml(Map<String, Object> config, String projectDir) throws IOException {
        Map<String, Object> generator = configService.getGenerator(config);

        String source = configService.getString(generator, "source");
        String destination = configService.getString(generator, "destination");
        if (source == null || destination == null) {
            throw new IOException("Config must define 'generator.source' and 'generator.destination'");
        }

        Path sourcePath = Path.of(projectDir, source);
        Path destPath = Path.of(projectDir, destination);
        Files.createDirectories(destPath);

        Map<String, Object> metadata = configService.getMap(config, "report");
        if (!metadata.containsKey("date")) {
            metadata = new java.util.HashMap<>(metadata);
            metadata.put("date", java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")));
        }

        String css = loadCss(generator, projectDir);
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();

        if (Files.isDirectory(sourcePath)) {
            try (Stream<Path> mdFiles = Files.list(sourcePath)
                    .filter(p -> p.toString().endsWith(".md"))) {
                for (Path mdFile : mdFiles.toList()) {
                    String outputName = getFileNameWithoutExtension(mdFile) + ".html";
                    convertFile(mdFile, destPath.resolve(outputName), parser, renderer, metadata, css);
                }
            }
            copyImages(sourcePath, destPath);
        } else if (Files.isRegularFile(sourcePath)) {
            String outputName = getFileNameWithoutExtension(sourcePath) + ".html";
            convertFile(sourcePath, destPath.resolve(outputName), parser, renderer, metadata, css);
            copyImages(sourcePath.getParent(), destPath);
        } else {
            throw new IOException("Source not found: " + sourcePath);
        }
    }

    private void convertFile(Path input, Path output, Parser parser, HtmlRenderer renderer,
            Map<String, Object> metadata, String css) throws IOException {
        String markdown = Files.readString(input);
        markdown = resolveMetadata(markdown, metadata);

        Node document = parser.parse(markdown);
        String bodyHtml = renderer.render(document);

        String title = extractTitle(markdown);
        String fullHtml = wrapInHtmlDocument(bodyHtml, title, css);
        Files.writeString(output, fullHtml);
    }

    private String loadCss(Map<String, Object> generator, String projectDir) throws IOException {
        String cssPath = configService.getString(generator, "css");
        if (cssPath != null) {
            Path externalCss = Path.of(projectDir, cssPath);
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

    private String resolveMetadata(String text, Map<String, Object> metadata) {
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            text = text.replace("{" + entry.getKey() + "}", value);
        }
        return text;
    }

    private String extractTitle(String markdown) {
        for (String line : markdown.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                String title = trimmed.substring(2).trim();
                return MD_IMAGE_PATTERN.matcher(title).replaceAll("").trim();
            }
        }
        return "Report";
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

    private String wrapInHtmlDocument(String bodyHtml, String title, String css) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <style>
                %s
                    </style>
                </head>
                <body>
                <div class="report-card">
                %s
                </div>
                </body>
                </html>
                """.formatted(title, css, bodyHtml);
    }

    private String getFileNameWithoutExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
