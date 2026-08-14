package io.litoria.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import jakarta.inject.Inject;

import io.litoria.config.LitoriaConfig;
import io.litoria.service.AsciidocService;
import io.litoria.service.EmbedService;
import io.litoria.service.MarkdownService;

@CommandDefinition(name = "generate", description = "Generate the (embedded) HTML")
public class GenerateCommand implements Command<CommandInvocation> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    @Option(shortName = 'r', name = "rendering",
            description = "Rendering type: html or pdf",
            defaultValue = "html")
    private String rendering;

    @Option(shortName = 'e', name = "embed",
            description = "Embed styles and images into a self-contained HTML after generation",
            hasValue = false)
    private boolean embed;

    @Option(shortName = 'd', name = "dest",
            description = "Custom destination directory (overrides config, no timestamp subfolder)")
    private String dest;

    @Argument(description = "Project directory path")
    private String projectDir;

    @Inject
    AsciidocService asciidocService;

    @Inject
    MarkdownService markdownService;

    @Inject
    LitoriaConfig config;

    @Inject
    EmbedService embedService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        try {
            String resolvedDir = resolveProjectDir(projectDir);
            String resolvedDest = resolveDestination();

            if ("pdf".equalsIgnoreCase(rendering)) {
                invocation.println("PDF rendering is not yet supported. Use '-r html' for now.");
                return CommandResult.FAILURE;
            }

            String engine = config.generator().engine();
            boolean isMarkdown = "markdown".equalsIgnoreCase(engine);

            if (isMarkdown) {
                invocation.println("Generating HTML from Markdown files...");
                markdownService.convertToHtml(resolvedDir, resolvedDest);
            } else {
                invocation.println("Generating HTML from AsciiDoc files...");
                asciidocService.convertToHtml(resolvedDir, resolvedDest);
            }
            invocation.println("HTML generation complete.");
            Path outputDir = Path.of(resolvedDir, resolvedDest).normalize().toAbsolutePath();
            invocation.println("Output: " + outputDir);
            listHtmlLinks(invocation, outputDir);

            if (embed) {
                String sourceDir = config.generator().source();
                String resolvedSourceDir = Path.of(resolvedDir, sourceDir).toString();
                String resolvedImageDir = config.generator().image()
                        .map(img -> Path.of(resolvedDir, img).toString())
                        .orElse(null);

                invocation.println("Embedding styles and images...");
                int count = embedHtmlFiles(Path.of(resolvedDir, resolvedDest), resolvedSourceDir, resolvedImageDir);
                invocation.println(count + " embedded file(s) saved.");
            }

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    private String resolveProjectDir(String argument) {
        if (argument != null && !argument.isBlank()) {
            return Path.of(argument).toAbsolutePath().toString();
        }
        return Path.of("").toAbsolutePath().toString();
    }

    private String resolveDestination() {
        if (dest != null && !dest.isBlank()) {
            return dest;
        }
        String baseDest = config.generator().destination();
        if (baseDest == null || baseDest.isBlank()) {
            baseDest = "generated";
        }
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return baseDest + "/" + timestamp;
    }

    private int embedHtmlFiles(Path destPath, String sourceDir, String imageDir)
            throws IOException {
        int count = 0;
        List<Path> sourceHtmlFiles;
        try (Stream<Path> htmlFiles = Files.list(destPath)
                .filter(p -> p.toString().endsWith(".html"))) {
            sourceHtmlFiles = htmlFiles.toList();
        }
        for (Path htmlFile : sourceHtmlFiles) {
            Path tempFile = destPath.resolve(htmlFile.getFileName() + ".tmp");
            embedService.embedStylesAndImages(
                    htmlFile.toString(),
                    tempFile.toString(),
                    sourceDir,
                    imageDir);
            Files.delete(htmlFile);
            Files.move(tempFile, htmlFile);
            count++;
        }
        return count;
    }

    private void listHtmlLinks(CommandInvocation invocation, Path outputDir) {
        try (Stream<Path> htmlFiles = Files.list(outputDir)
                .filter(p -> p.toString().endsWith(".html"))
                .sorted()) {
            for (Path htmlFile : htmlFiles.toList()) {
                invocation.println("  -> file://" + htmlFile.toAbsolutePath());
            }
        } catch (IOException ignored) {
        }
    }
}
