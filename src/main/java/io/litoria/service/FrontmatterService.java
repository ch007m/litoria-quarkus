package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import jakarta.enterprise.context.ApplicationScoped;

import org.yaml.snakeyaml.Yaml;

@ApplicationScoped
public class FrontmatterService {

    private static final String DELIMITER = "---";

    public Map<String, String> parseFrontmatter(Path mdFile) throws IOException {
        return parseFrontmatter(Files.readString(mdFile));
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> parseFrontmatter(String markdown) {
        String yamlBlock = extractFrontmatterBlock(markdown);
        if (yamlBlock == null) {
            return Collections.emptyMap();
        }
        Yaml yaml = new Yaml();
        Map<String, Object> raw = yaml.load(yamlBlock);
        if (raw == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }

    public String stripFrontmatter(String markdown) {
        String trimmed = markdown.stripLeading();
        if (!trimmed.startsWith(DELIMITER)) {
            return markdown;
        }
        int afterFirst = DELIMITER.length();
        int closingDelim = findClosingDelimiter(trimmed, afterFirst);
        if (closingDelim < 0) {
            return markdown;
        }
        return trimmed.substring(closingDelim + DELIMITER.length()).stripLeading();
    }

    public void validateRequired(Map<String, String> metadata, String... requiredKeys) throws IOException {
        StringJoiner missing = new StringJoiner(", ");
        for (String key : requiredKeys) {
            String value = metadata.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        if (missing.length() > 0) {
            throw new IOException(
                    "Missing required fields in YAML frontmatter: " + missing
                            + ". Add them between --- delimiters at the top of your markdown file.");
        }
    }

    private String extractFrontmatterBlock(String markdown) {
        String trimmed = markdown.stripLeading();
        if (!trimmed.startsWith(DELIMITER)) {
            return null;
        }
        int start = DELIMITER.length();
        int end = findClosingDelimiter(trimmed, start);
        if (end < 0) {
            return null;
        }
        return trimmed.substring(start, end).trim();
    }

    private int findClosingDelimiter(String text, int fromIndex) {
        int pos = fromIndex;
        while (pos < text.length()) {
            int nl = text.indexOf('\n', pos);
            if (nl < 0) {
                break;
            }
            int lineStart = nl + 1;
            if (text.startsWith(DELIMITER, lineStart)) {
                int afterDelim = lineStart + DELIMITER.length();
                if (afterDelim >= text.length()
                        || text.charAt(afterDelim) == '\n'
                        || text.charAt(afterDelim) == '\r') {
                    return lineStart;
                }
            }
            pos = nl + 1;
        }
        return -1;
    }
}
