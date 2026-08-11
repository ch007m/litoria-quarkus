package io.litoria.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.yaml.snakeyaml.Yaml;

@ApplicationScoped
public class ConfigService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadConfig(String configPath) {
        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(configPath)) {
            Map<String, Object> result = yaml.load(is);
            if (result == null) {
                return Collections.emptyMap();
            }
            resolvePlaceholders(result);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + configPath, e);
        }
    }

    public Map<String, Object> getGenerator(Map<String, Object> config) {
        return getMap(config, "generator");
    }

    public String resolveConfigFile(String projectDir, String configOption) {
        if (configOption != null && !configOption.isBlank()) {
            Path p = Path.of(configOption);
            if (p.isAbsolute()) {
                return p.toString();
            }
            return Path.of(projectDir).resolve(configOption).toString();
        }
        Path ymlPath = Path.of(projectDir, "config.yml");
        if (Files.exists(ymlPath)) {
            return ymlPath.toString();
        }
        Path yamlPath = Path.of(projectDir, "config.yaml");
        if (Files.exists(yamlPath)) {
            return yamlPath.toString();
        }
        throw new RuntimeException("No config.yml or config.yaml found in " + projectDir);
    }

    public String resolveProjectDir(String argument) {
        if (argument != null && !argument.isBlank()) {
            return Path.of(argument).toAbsolutePath().toString();
        }
        return Path.of("").toAbsolutePath().toString();
    }

    @SuppressWarnings("unchecked")
    public String getString(Map<String, Object> config, String key) {
        Object val = config.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(Map<String, Object> config, String key) {
        Object val = config.get(key);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return Collections.emptyMap();
    }

    public int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object val = config.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private void resolvePlaceholders(Map<String, Object> root) {
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> section = (Map<String, Object>) value;
                Map<String, Object> flat = flattenStrings(section);
                resolveSection(section, flat);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenStrings(Map<String, Object> section) {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                flat.put(entry.getKey(), val);
            } else if (val instanceof Map) {
                for (Map.Entry<String, Object> sub : ((Map<String, Object>) val).entrySet()) {
                    if (sub.getValue() instanceof String) {
                        flat.put(sub.getKey(), sub.getValue());
                    }
                }
            }
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private void resolveSection(Map<String, Object> section, Map<String, Object> flat) {
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                entry.setValue(replacePlaceholders((String) value, flat));
            } else if (value instanceof Map) {
                Map<String, Object> nested = (Map<String, Object>) value;
                for (Map.Entry<String, Object> sub : nested.entrySet()) {
                    if (sub.getValue() instanceof String) {
                        sub.setValue(replacePlaceholders((String) sub.getValue(), flat));
                    }
                }
            }
        }
    }

    private String replacePlaceholders(String text, Map<String, Object> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object replacement = values.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    replacement != null ? replacement.toString() : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
