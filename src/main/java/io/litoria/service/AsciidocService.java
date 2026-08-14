package io.litoria.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

import io.litoria.config.LitoriaConfig;

@ApplicationScoped
public class AsciidocService {

    @Inject
    LitoriaConfig config;

    public void convertToHtml(String projectDir, String destination) throws IOException {
        String source = config.generator().source();

        Path sourcePath = Path.of(projectDir, source);
        Path destPath = Path.of(projectDir, destination);
        Files.createDirectories(destPath);

        Map<String, String> attrsMap = new HashMap<>(config.asciidoctor().attributes());

        Map<String, String> metadata = buildMetadataFromConfig();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            attrsMap.put(entry.getKey(), entry.getValue());
        }
        attrsMap.put("break", "<br/>");

        config.generator().image().ifPresent(imageDir ->
                attrsMap.put("imagesdir", Path.of(projectDir, imageDir).toAbsolutePath().toString()));

        Attributes attrs = buildAttributes(attrsMap);
        Map<String, String> optionsMap = config.asciidoctor().options();
        String doctype = optionsMap.getOrDefault("doctype", "article");
        SafeMode safe = parseSafeMode(optionsMap.getOrDefault("safe", "unsafe"));

        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> adocFiles = Files.list(sourcePath)
                        .filter(p -> p.toString().endsWith(".adoc"))) {
                    for (Path adocFile : adocFiles.toList()) {
                        String outputName = getFileNameWithoutExtension(adocFile) + ".html";
                        Options options = Options.builder()
                                .attributes(attrs)
                                .docType(doctype)
                                .safe(safe)
                                .toDir(destPath.toFile())
                                .toFile(new File(outputName))
                                .build();
                        asciidoctor.convertFile(adocFile.toFile(), options);
                    }
                }
            } else if (Files.isRegularFile(sourcePath)) {
                String outputName = getFileNameWithoutExtension(sourcePath) + ".html";
                Options options = Options.builder()
                        .attributes(attrs)
                        .docType(doctype)
                        .safe(safe)
                        .toDir(destPath.toFile())
                        .toFile(new File(outputName))
                        .build();
                asciidoctor.convertFile(sourcePath.toFile(), options);
            } else {
                throw new IOException("Source not found: " + sourcePath);
            }
        }
    }

    private Map<String, String> buildMetadataFromConfig() {
        Map<String, String> metadata = new HashMap<>();
        config.report().author().ifPresent(v -> metadata.put("author", v));
        config.report().title().ifPresent(v -> metadata.put("title", v));
        config.report().email().ifPresent(v -> metadata.put("email", v));
        if (!metadata.containsKey("date")) {
            metadata.put("date", LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("M/d/yyyy")));
        }
        return metadata;
    }

    private Attributes buildAttributes(Map<String, String> attrsMap) {
        AttributesBuilder builder = Attributes.builder();
        for (Map.Entry<String, String> entry : attrsMap.entrySet()) {
            builder.attribute(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return builder.build();
    }

    private SafeMode parseSafeMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "safe" -> SafeMode.SAFE;
            case "server" -> SafeMode.SERVER;
            case "secure" -> SafeMode.SECURE;
            default -> SafeMode.UNSAFE;
        };
    }

    private String getFileNameWithoutExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
