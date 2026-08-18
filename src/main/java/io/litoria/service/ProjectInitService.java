package io.litoria.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.litoria.model.ProjectType;

@ApplicationScoped
public class ProjectInitService {

    private static final String TEMPLATES_BASE = "templates/";
    private static final String ASCIIDOCTOR_BASE = TEMPLATES_BASE + "asciidoctor/";
    private static final String MARKDOWN_BASE = TEMPLATES_BASE + "markdown/";
    private static final String SLIDESHOW_BASE = TEMPLATES_BASE + "slideshow/";
    private static final String[] ASCIIDOCTOR_CSS = {"font-awesome.min.css"};
    private static final String[] MARKDOWN_CSS = {"report.css"};
    private static final String[] SLIDESHOW_CSS_DEFAULT = {"slides.css"};
    private static final String[] SLIDESHOW_CSS_TOKENS = {"slides.css", "tokens.css"};
    private static final String[] ASCIIDOCTOR_IMAGES = {"litoria-chloris.jpg"};
    private static final String[] MARKDOWN_IMAGES = {"quarkus-logo.png"};
    private static final String[] SLIDESHOW_IMAGES = {"quarkus.png"};

    public void createProject(ProjectType type, boolean force, String dir) throws IOException {
        createProject(type, force, dir, false, false);
    }

    public void createProject(ProjectType type, boolean force, String dir, boolean markdown) throws IOException {
        createProject(type, force, dir, markdown, false);
    }

    public void createProject(ProjectType type, boolean force, String dir, boolean markdown, boolean useTokens) throws IOException {
        Path projectDir = Path.of(dir).toAbsolutePath();

        if (Files.exists(projectDir) && !force) {
            try (var entries = Files.list(projectDir)) {
                if (entries.findAny().isPresent()) {
                    throw new IOException("Directory already exists and is not empty: " + projectDir
                            + ". Use --force to override.");
                }
            }
        }

        if (force && Files.isDirectory(projectDir.resolve("source"))) {
            try (var sourceFiles = Files.list(projectDir.resolve("source"))) {
                sourceFiles.filter(p -> p.toString().endsWith(".adoc") || p.toString().endsWith(".md"))
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }

        Files.createDirectories(projectDir);
        Files.createDirectories(projectDir.resolve("source"));

        String engineBase;
        String[] cssFiles;
        String[] imageFiles;

        if (type == ProjectType.SLIDESHOW) {
            engineBase = SLIDESHOW_BASE;
            cssFiles = useTokens ? SLIDESHOW_CSS_TOKENS : SLIDESHOW_CSS_DEFAULT;
            imageFiles = SLIDESHOW_IMAGES;
        } else if (type == ProjectType.DOC) {
            engineBase = ASCIIDOCTOR_BASE;
            cssFiles = ASCIIDOCTOR_CSS;
            imageFiles = ASCIIDOCTOR_IMAGES;
        } else {
            engineBase = MARKDOWN_BASE;
            cssFiles = MARKDOWN_CSS;
            imageFiles = MARKDOWN_IMAGES;
        }

        List<String> templates = getTemplatesForType(type, markdown, useTokens);
        for (String template : templates) {
            copyResource(engineBase + template,
                    projectDir.resolve("source/" + template));
        }

        Files.createDirectories(projectDir.resolve("source/css"));
        Files.createDirectories(projectDir.resolve("source/image"));

        for (String css : cssFiles) {
            copyResource(engineBase + "css/" + css,
                    projectDir.resolve("source/css/" + css));
        }
        for (String image : imageFiles) {
            copyResource(engineBase + "image/" + image,
                    projectDir.resolve("source/image/" + image));
        }
    }

    public List<String> getTemplatesForType(ProjectType type, boolean markdown, boolean useTokens) {
        if (type == ProjectType.SLIDESHOW) {
            return useTokens
                    ? List.of("slides.md", "slides-tokens.md")
                    : List.of("slides.md");
        }
        return switch (type) {
            case DOC -> List.of("doc.adoc");
            case REPORT -> List.of("minute.md", "report.md");
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    private void copyResource(String resourcePath, Path destination) throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Template resource not found: " + resourcePath);
            }
            try (OutputStream os = Files.newOutputStream(destination)) {
                is.transferTo(os);
            }
        }
    }
}
