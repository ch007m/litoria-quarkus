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
    private static final String[] CSS_FILES = {"asciidoctor.css", "font-awesome.min.css", "foundation.css"};
    private static final String[] IMAGE_FILES = {"litoria-chloris.jpg", "quarkus-logo.png"};

    public void createProject(ProjectType type, boolean force, String dir) throws IOException {
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
            try (var adocFiles = Files.list(projectDir.resolve("source"))) {
                adocFiles.filter(p -> p.toString().endsWith(".adoc"))
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }

        Files.createDirectories(projectDir);
        Files.createDirectories(projectDir.resolve("source"));
        Files.createDirectories(projectDir.resolve("source/css"));
        Files.createDirectories(projectDir.resolve("source/image"));

        copyResource(TEMPLATES_BASE + "config.yml",
                projectDir.resolve("config.yml"));

        List<String> templates = getTemplatesForType(type);
        for (String template : templates) {
            copyResource(TEMPLATES_BASE + template,
                    projectDir.resolve("source/" + template));
        }

        for (String css : CSS_FILES) {
            copyResource(TEMPLATES_BASE + "css/" + css,
                    projectDir.resolve("source/css/" + css));
        }

        for (String image : IMAGE_FILES) {
            copyResource(TEMPLATES_BASE + "image/" + image,
                    projectDir.resolve("source/image/" + image));
        }
    }

    private List<String> getTemplatesForType(ProjectType type) {
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
