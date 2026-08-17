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
    private static final String[] ASCIIDOCTOR_CSS = {"font-awesome.min.css"};
    private static final String[] MARKDOWN_CSS = {"report.css"};
    private static final String[] ASCIIDOCTOR_IMAGES = {"litoria-chloris.jpg"};
    private static final String[] MARKDOWN_IMAGES = {"quarkus-logo.png"};

    public void createProject(ProjectType type, boolean force, String dir) throws IOException {
        createProject(type, force, dir, false);
    }

    public void createProject(ProjectType type, boolean force, String dir, boolean markdown) throws IOException {
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

        String engineBase = markdown ? MARKDOWN_BASE : ASCIIDOCTOR_BASE;

        List<String> templates = getTemplatesForType(type, markdown);
        for (String template : templates) {
            copyResource(engineBase + template,
                    projectDir.resolve("source/" + template));
        }

        Files.createDirectories(projectDir.resolve("source/css"));
        Files.createDirectories(projectDir.resolve("source/image"));

        if (markdown) {
            for (String css : MARKDOWN_CSS) {
                copyResource(MARKDOWN_BASE + "css/" + css,
                        projectDir.resolve("source/css/" + css));
            }
            for (String image : MARKDOWN_IMAGES) {
                copyResource(MARKDOWN_BASE + "image/" + image,
                        projectDir.resolve("source/image/" + image));
            }
        } else {
            for (String css : ASCIIDOCTOR_CSS) {
                copyResource(ASCIIDOCTOR_BASE + "css/" + css,
                        projectDir.resolve("source/css/" + css));
            }
            for (String image : ASCIIDOCTOR_IMAGES) {
                copyResource(ASCIIDOCTOR_BASE + "image/" + image,
                        projectDir.resolve("source/image/" + image));
            }
        }
    }

    public List<String> getTemplatesForType(ProjectType type, boolean markdown) {
        if (markdown) {
            return switch (type) {
                case SIMPLE -> List.of("simple.adoc");
                case REPORT -> List.of("report.md");
                case SLIDESHOW -> List.of("slideshow.adoc");
            };
        }
        return switch (type) {
            case SIMPLE -> List.of("simple.adoc");
            case REPORT -> List.of("minute.adoc", "report.adoc");
            case SLIDESHOW -> List.of("slideshow.adoc");
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
