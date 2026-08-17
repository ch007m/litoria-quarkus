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
import io.litoria.service.PdfService;
import io.litoria.service.SlideshowService;

@CommandDefinition(name = "generate", description = "Generate the (embedded) HTML")
public class GenerateCommand implements Command<CommandInvocation> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    @Option(shortName = 'r', name = "rendering",
            description = "Rendering type: html, pdf, or revealjs",
            defaultValue = "html")
    private String rendering;

    @Option(shortName = 't', name = "theme",
            description = "RevealJS theme: white, black, beige, blood, dracula, league, moon, night, serif, simple, sky, solarized",
            defaultValue = "white")
    private String theme;

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

    @Inject
    PdfService pdfService;

    @Inject
    SlideshowService slideshowService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        try {
            String resolvedDir = resolveProjectDir(projectDir);
            String resolvedDest = resolveDestination();

            boolean isPdf = "pdf".equalsIgnoreCase(rendering);
            boolean isRevealJs = "revealjs".equalsIgnoreCase(rendering);

            if (isRevealJs) {
                invocation.println("Generating RevealJS slideshow (theme: " + theme + ")...");
                slideshowService.convertToHtml(resolvedDir, resolvedDest, theme);
            } else {
                String engine = config.generator().engine();
                if ("markdown".equalsIgnoreCase(engine)) {
                    invocation.println("Generating HTML from Markdown files...");
                    markdownService.convertToHtml(resolvedDir, resolvedDest);
                } else {
                    invocation.println("Generating HTML from AsciiDoc files...");
                    asciidocService.convertToHtml(resolvedDir, resolvedDest);
                }
            }

            Path outputDir = Path.of(resolvedDir, resolvedDest).normalize().toAbsolutePath();

            if (embed && !isRevealJs) {
                String sourceDir = config.generator().source();
                String resolvedSourceDir = Path.of(resolvedDir, sourceDir).toString();
                String resolvedImageDir = config.generator().image()
                        .map(img -> Path.of(resolvedDir, img).toString())
                        .orElse(null);

                invocation.println("Embedding styles and images...");
                embedHtmlFiles(Path.of(resolvedDir, resolvedDest), resolvedSourceDir, resolvedImageDir);
            }

            if (isPdf) {
                invocation.println("Converting to PDF...");
                convertToPdf(outputDir);
            }

            printSummary(invocation, outputDir, isPdf);
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

    private void convertToPdf(Path outputDir) throws IOException {
        List<Path> htmlFiles;
        try (Stream<Path> files = Files.list(outputDir)
                .filter(p -> p.toString().endsWith(".html"))
                .sorted()) {
            htmlFiles = files.toList();
        }
        for (Path htmlFile : htmlFiles) {
            pdfService.convertHtmlToPdf(htmlFile);
        }
    }

    private void printSummary(CommandInvocation invocation, Path outputDir, boolean isPdf) {
        String ext = isPdf ? ".pdf" : ".html";
        try (Stream<Path> files = Files.list(outputDir)
                .filter(p -> p.toString().endsWith(ext))
                .sorted()) {
            Path cwd = Path.of("").toAbsolutePath();
            String relativeOutputDir = cwd.relativize(outputDir).toString();

            StringBuilder sb = new StringBuilder();
            sb.append("\nReport generated here: ").append(outputDir);
            for (Path file : files.toList()) {
                sb.append("\n  file://").append(file.toAbsolutePath());
            }
            if (!isPdf) {
                sb.append("\n\nTo send your report: litoria send ").append(relativeOutputDir);
            }
            invocation.println(sb.toString());
        } catch (IOException ignored) {
        }
    }
}
