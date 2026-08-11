package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ApplicationScoped
public class EmailService {

    @Inject
    ConfigService configService;

    @SuppressWarnings("unchecked")
    public void sendEmail(Map<String, Object> config, String projectDir) throws MessagingException, IOException {
        Map<String, Object> generator = configService.getGenerator(config);
        Map<String, Object> smtpConfig = configService.getMap(config, "smtp");
        Map<String, Object> mailConfig = configService.getMap(config, "mail");

        if (smtpConfig.isEmpty()) {
            throw new MessagingException(
                    "No 'smtp' section defined in the config file. The send command requires a report project type.");
        }

        Properties props = buildSmtpProperties(smtpConfig);
        Session session = createSession(props, smtpConfig);

        Map<String, Object> metadata = new java.util.HashMap<>(configService.getMap(generator, "metadata"));

        if (!metadata.containsKey("date")) {
            metadata.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy")));
        }

        String subject = resolveTemplate(getString(mailConfig, "subject"), metadata);
        String from = getString(mailConfig, "from");
        String to = getString(mailConfig, "to");

        String htmlBody;
        if (mailConfig.containsKey("body") && mailConfig.get("body") != null) {
            htmlBody = resolveTemplate(mailConfig.get("body").toString(), metadata);
        } else {
            Path embeddedFile = findEmbeddedFile(generator, projectDir);
            if (embeddedFile != null && Files.exists(embeddedFile)) {
                htmlBody = resolveTemplate(Files.readString(embeddedFile), metadata);
            } else {
                throw new IOException("No embedded file found in the destination directory."
                        + " Run 'generate --embed' first.");
            }
        }

        if (mailConfig.containsKey("signature") && mailConfig.get("signature") != null) {
            String sig = resolveTemplate(mailConfig.get("signature").toString(), metadata);
            sig = sig.replace("\n", "<br/>");
            htmlBody += "<br/><hr style=\"border:none;border-top:1px solid #ccc;margin:1em 0\"/>" + sig;
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setContent(htmlBody, "text/html; charset=UTF-8");

        Transport.send(message);
    }

    private Properties buildSmtpProperties(Map<String, Object> smtpConfig) {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpConfig.getOrDefault("host", "localhost").toString());
        props.put("mail.smtp.port", smtpConfig.getOrDefault("port", 587).toString());

        boolean secure = Boolean.parseBoolean(smtpConfig.getOrDefault("secure", false).toString());
        if (secure) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        boolean requireTLS = Boolean.parseBoolean(smtpConfig.getOrDefault("requireTLS", false).toString());
        if (requireTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tls = smtpConfig.containsKey("tls")
                ? (Map<String, Object>) smtpConfig.get("tls")
                : java.util.Collections.emptyMap();
        if (Boolean.parseBoolean(tls.getOrDefault("rejectUnauthorized", true).toString()) == false) {
            props.put("mail.smtp.ssl.trust", "*");
        }

        props.put("mail.smtp.auth", "true");
        return props;
    }

    private Session createSession(Properties props, Map<String, Object> smtpConfig) {
        String user = smtpConfig.getOrDefault("user", "").toString();
        String pass = smtpConfig.getOrDefault("pass", "").toString();

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });
    }

    private String resolveTemplate(String text, Map<String, Object> metadata) {
        if (text == null) {
            return "";
        }
        text = text.replace("{break}", "<br/>");
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            text = text.replaceAll(Pattern.quote("{" + entry.getKey() + "}"), value);
        }
        return text;
    }

    private Path findEmbeddedFile(Map<String, Object> generator, String projectDir) throws IOException {
        String destination = configService.getString(generator, "destination");
        if (destination == null) {
            return null;
        }
        Path destPath = Path.of(projectDir, destination);
        if (!Files.isDirectory(destPath)) {
            return null;
        }

        Path searchDir = findLatestSubfolder(destPath);
        try (var files = Files.list(searchDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".html"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private Path findLatestSubfolder(Path destPath) throws IOException {
        try (var subdirs = Files.list(destPath)) {
            return subdirs
                    .filter(Files::isDirectory)
                    .max(java.util.Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElse(destPath);
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
