package io.litoria.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import jakarta.inject.Inject;

import io.litoria.config.LitoriaConfig;
import io.litoria.service.EmailService;
import io.litoria.service.FrontmatterService;

@CommandDefinition(name = "send", description = "Send HTML content as email via SMTP")
public class SendCommand implements Command<CommandInvocation> {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(SendCommand.class);

    @Option(shortName = 'f', name = "file",
            description = "Name of the HTML file to send (without extension)",
            defaultValue = "report")
    private String file;

    @Argument(description = "Project directory path")
    private String projectDir;

    @Inject
    EmailService emailService;

    @Inject
    LitoriaConfig config;

    @Inject
    FrontmatterService frontmatterService;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        try {
            String resolvedDir = resolveProjectDir(projectDir);

            Map<String, String> metadata = loadMetadata(resolvedDir);

            String resolvedFile = emailService.resolveHtmlFile(resolvedDir, file);
            invocation.println("Sending " + resolvedFile + " ...");
            emailService.sendEmail(resolvedDir, file, metadata);
            invocation.println("Email sent successfully.");
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

    private Map<String, String> loadMetadata(String projectDir) throws Exception {
        String engine = config.generator().engine();
        LOG.debugf("Engine: %s", engine);
        if ("markdown".equalsIgnoreCase(engine)) {
            Path sourceDir = resolveSourceDir(projectDir);
            LOG.debugf("Source dir: %s (exists: %s)", sourceDir, sourceDir != null && java.nio.file.Files.isDirectory(sourceDir));
            if (sourceDir != null) {
                Path mdFile = findMarkdownFile(sourceDir);
                LOG.debugf("Markdown file found: %s", mdFile);
                if (mdFile != null) {
                    Map<String, String> metadata = frontmatterService.parseFrontmatter(mdFile);
                    LOG.debugf("Parsed frontmatter keys: %s", metadata.keySet());
                    return metadata;
                }
            }
        }
        LOG.debug("Falling back to application.properties for report metadata");
        Map<String, String> metadata = new HashMap<>();
        config.report().author().ifPresent(v -> metadata.put("author", v));
        config.report().title().ifPresent(v -> metadata.put("title", v));
        config.report().email().ifPresent(v -> metadata.put("email", v));
        config.report().mail().to().ifPresent(v -> metadata.put("to", v));
        return metadata;
    }

    private Path resolveSourceDir(String projectDir) {
        String source = config.generator().source();
        Path sourceDir = Path.of(projectDir, source);
        if (Files.isDirectory(sourceDir)) {
            return sourceDir;
        }
        Path current = Path.of(projectDir);
        for (int i = 0; i < 5; i++) {
            current = current.getParent();
            if (current == null) break;
            Path candidate = current.resolve(source);
            if (Files.isDirectory(candidate)) {
                LOG.debugf("Found source dir by walking up to: %s", current);
                return candidate;
            }
        }
        return null;
    }

    private Path findMarkdownFile(Path sourceDir) throws Exception {
        if (!Files.isDirectory(sourceDir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(sourceDir)
                .filter(p -> p.toString().endsWith(".md"))) {
            return files.findFirst().orElse(null);
        }
    }
}
