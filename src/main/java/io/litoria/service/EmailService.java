package io.litoria.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.litoria.config.LitoriaConfig;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.mail.MailClient;

@ApplicationScoped
public class EmailService {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(EmailService.class);

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile(
            "\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    @Inject
    Mailer mailer;

    @Inject
    Vertx vertx;

    @Inject
    LitoriaConfig config;

    @ConfigProperty(name = "quarkus.mailer.host", defaultValue = "localhost")
    String mailHost;

    @ConfigProperty(name = "quarkus.mailer.port", defaultValue = "25")
    int mailPort;

    @ConfigProperty(name = "quarkus.mailer.start-tls", defaultValue = "DISABLED")
    String mailStartTls;

    @ConfigProperty(name = "quarkus.mailer.trust-all", defaultValue = "false")
    boolean mailTrustAll;

    @ConfigProperty(name = "quarkus.mailer.username")
    java.util.Optional<String> mailUsername;

    public void sendEmail(String projectDir, String fileName, Map<String, String> metadata)
            throws IOException {
        Map<String, String> resolvedMetadata = new HashMap<>(metadata);
        if (!resolvedMetadata.containsKey("date")) {
            resolvedMetadata.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy")));
        }

        String to = config.report().mail().to()
                .filter(t -> !t.isBlank())
                .map(t -> resolveTemplate(t, resolvedMetadata))
                .orElse(resolvedMetadata.getOrDefault("to", ""));
        String from = resolveTemplate(config.report().mail().from(), resolvedMetadata);

        LOG.debugf("From: %s", from);
        LOG.debugf("To: %s", to);

        if (from.isBlank() || from.contains("{")) {
            throw new IOException(
                    "Sender address not configured. Set 'email' in frontmatter or quarkus.mailer.from.");
        }

        if (to.isBlank()) {
            throw new IOException("Recipient 'to' address not configured. "
                    + "Set 'to' in frontmatter or litoria.report.mail.to.");
        }

        String subjectTemplate = resolvedMetadata.getOrDefault("subject",
                config.report().mail().subject().orElse("{author}'s weekly report : {date}"));
        String subject = resolveTemplate(subjectTemplate, resolvedMetadata);

        String htmlBody;
        Path embeddedFile = findHtmlFile(projectDir, fileName);
        if (embeddedFile != null && Files.exists(embeddedFile)) {
            htmlBody = resolveTemplate(Files.readString(embeddedFile), resolvedMetadata);
        } else {
            String available = listAvailableHtmlFiles(projectDir);
            if (available.isEmpty()) {
                throw new IOException("No HTML files found in the destination directory."
                        + " Run 'generate --embed' first.");
            }
            throw new IOException("File '" + fileName + ".html' not found. Available report(s):\n"
                    + available
                    + "\nUse: litoria send -f <name> " + projectDir);
        }

        String signatureTemplate = resolvedMetadata.getOrDefault("signature",
                config.report().signature());
        String signature = resolveTemplate(signatureTemplate, resolvedMetadata);
        signature = signature.replace("\n", "<br/>");
        htmlBody += "<br/><hr style=\"border:none;border-top:1px solid #ccc;margin:1em 0\"/>" + signature;

        List<CidImage> cidImages = new ArrayList<>();
        htmlBody = convertDataUrisToCid(htmlBody, cidImages);

        if (isOAuth2()) {
            sendWithOAuth2(from, to, subject, htmlBody, cidImages);
        } else {
            sendWithMailer(from, to, subject, htmlBody, cidImages);
        }
    }

    public String resolveHtmlFile(String projectDir, String fileName) {
        try {
            Path path = findHtmlFile(projectDir, fileName);
            return path != null ? path.toString() : fileName + ".html";
        } catch (IOException e) {
            return fileName + ".html";
        }
    }

    private void sendWithMailer(String from, String to, String subject, String htmlBody, List<CidImage> cidImages) {
        Mail mail = Mail.withHtml(to, subject, htmlBody).setFrom(from);
        for (CidImage cid : cidImages) {
            mail.addInlineAttachment(cid.id() + extensionForMimeType(cid.mimeType()), cid.data(), cid.mimeType(), "<" + cid.id() + ">");
        }
        mailer.send(mail);
    }

    private void sendWithOAuth2(String from, String to, String subject, String htmlBody, List<CidImage> cidImages)
            throws IOException {
        LitoriaConfig.SmtpConfig.Oauth2Config oauth2 = config.smtp().oauth2();
        String accessToken = fetchAccessToken(
                oauth2.clientId().get(),
                oauth2.clientSecret().get(),
                oauth2.refreshToken().get());

        String username = mailUsername.filter(u -> !u.isBlank()).orElse(from);

        MailConfig mailConfig = new MailConfig()
                .setHostname(mailHost)
                .setPort(mailPort)
                .setStarttls(StartTLSOptions.valueOf(mailStartTls.toUpperCase()))
                .setTrustAll(mailTrustAll)
                .setAuthMethods("XOAUTH2")
                .setUsername(username)
                .setPassword(accessToken);

        MailClient client = MailClient.create(vertx, mailConfig);
        try {
            MailMessage message = new MailMessage()
                    .setFrom(from)
                    .setTo(List.of(to))
                    .setSubject(subject)
                    .setHtml(htmlBody);

            if (!cidImages.isEmpty()) {
                List<MailAttachment> attachments = new ArrayList<>();
                for (CidImage cid : cidImages) {
                    MailAttachment att = MailAttachment.create()
                            .setContentType(cid.mimeType())
                            .setContentId("<" + cid.id() + ">")
                            .setData(Buffer.buffer(cid.data()))
                            .setDisposition("inline")
                            .setName(cid.id() + extensionForMimeType(cid.mimeType()));
                    attachments.add(att);
                }
                message.setInlineAttachment(attachments);
            }

            client.sendMail(message).await().indefinitely();
        } finally {
            client.close().await().indefinitely();
        }
    }

    private boolean isOAuth2() {
        LitoriaConfig.SmtpConfig.Oauth2Config oauth2 = config.smtp().oauth2();
        return oauth2.clientId().isPresent() && !oauth2.clientId().get().isBlank()
                && oauth2.clientSecret().isPresent() && !oauth2.clientSecret().get().isBlank()
                && oauth2.refreshToken().isPresent() && !oauth2.refreshToken().get().isBlank();
    }

    private String fetchAccessToken(String clientId, String clientSecret, String refreshToken) throws IOException {
        String body = "client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&grant_type=refresh_token";

        HttpURLConnection conn = (HttpURLConnection) URI.create(GOOGLE_TOKEN_URL).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String response = new String(
                (status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()).readAllBytes(),
                StandardCharsets.UTF_8);

        if (status != 200) {
            throw new IOException("Failed to obtain OAuth2 access token (HTTP " + status + "): " + response);
        }

        Matcher m = ACCESS_TOKEN_PATTERN.matcher(response);
        if (!m.find()) {
            throw new IOException("No access_token in OAuth2 response: " + response);
        }
        return m.group(1);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveTemplate(String text, Map<String, String> metadata) {
        if (text == null) {
            return "";
        }
        text = text.replace("{break}", "<br/>");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            text = text.replaceAll(Pattern.quote("{" + entry.getKey() + "}"), value);
        }
        return text;
    }

    private Path findHtmlFile(String projectDir, String fileName) throws IOException {
        Path searchDir = resolveHtmlSearchDir(projectDir);
        if (searchDir == null || !Files.isDirectory(searchDir)) {
            return null;
        }

        Path target = searchDir.resolve(fileName + ".html");
        if (Files.exists(target)) {
            return target;
        }

        try (var htmlFiles = Files.list(searchDir)) {
            List<Path> found = htmlFiles
                    .filter(p -> p.toString().endsWith(".html"))
                    .toList();
            if (found.size() == 1) {
                return found.get(0);
            }
        }
        return null;
    }

    private Path resolveHtmlSearchDir(String projectDir) throws IOException {
        String destination = config.generator().destination();
        Path destPath = Path.of(projectDir, destination);
        if (Files.isDirectory(destPath)) {
            return findLatestSubfolder(destPath);
        }
        Path dirPath = Path.of(projectDir);
        if (Files.isDirectory(dirPath) && containsHtmlFiles(dirPath)) {
            return dirPath;
        }
        return null;
    }

    private boolean containsHtmlFiles(Path dir) {
        try (var files = Files.list(dir)) {
            return files.anyMatch(p -> p.toString().endsWith(".html"));
        } catch (IOException e) {
            return false;
        }
    }

    private String listAvailableHtmlFiles(String projectDir) {
        try {
            Path searchDir = resolveHtmlSearchDir(projectDir);
            if (searchDir == null || !Files.isDirectory(searchDir)) {
                return "";
            }
            try (var htmlFiles = Files.list(searchDir)) {
                return htmlFiles
                        .filter(p -> p.toString().endsWith(".html"))
                        .map(p -> {
                            String name = p.getFileName().toString();
                            return "  - " + name.substring(0, name.length() - 5);
                        })
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
            }
        } catch (IOException e) {
            return "";
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

    private static final Pattern DATA_URI_PATTERN = Pattern.compile(
            "src\\s*=\\s*\"(data:([^;]+);base64,([^\"]+))\"");

    private String convertDataUrisToCid(String html, List<CidImage> cidImages) {
        Matcher m = DATA_URI_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        int counter = 0;
        while (m.find()) {
            String mimeType = m.group(2);
            String base64Data = m.group(3);
            String cid = "img" + counter++ + "@litoria";
            byte[] data = Base64.getDecoder().decode(base64Data);
            cidImages.add(new CidImage(cid, mimeType, data));
            m.appendReplacement(sb, Matcher.quoteReplacement("src=\"cid:" + cid + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extensionForMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }

    private record CidImage(String id, String mimeType, byte[] data) {}
}
