package io.litoria.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

@ApplicationScoped
public class AsciidocService {

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

        Map<String, Object> asciidoctorConfig = configService.getMap(config, "asciidoctor");
        Map<String, Object> attrsMap = new java.util.HashMap<>(configService.getMap(asciidoctorConfig, "attributes"));

        Map<String, Object> metadata = configService.getMap(config, "report");
        if (!metadata.containsKey("date")) {
            metadata = new java.util.HashMap<>(metadata);
            metadata.put("date", java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")));
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getValue() != null) {
                attrsMap.put(entry.getKey(), entry.getValue().toString());
            }
        }
        attrsMap.put("break", "<br/>");

        String imageDir = configService.getString(generator, "image");
        if (imageDir != null && !imageDir.isBlank()) {
            attrsMap.put("imagesdir", Path.of(projectDir, imageDir).toAbsolutePath().toString());
        }
        Attributes attrs = buildAttributes(attrsMap);
        Map<String, Object> optionsMap = configService.getMap(asciidoctorConfig, "options");
        String doctype = optionsMap.getOrDefault("doctype", "article").toString();
        SafeMode safe = parseSafeMode(optionsMap.getOrDefault("safe", "unsafe").toString());

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

    private Attributes buildAttributes(Map<String, Object> attrsMap) {
        AttributesBuilder builder = Attributes.builder();
        for (Map.Entry<String, Object> entry : attrsMap.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            builder.attribute(entry.getKey(), value);
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
