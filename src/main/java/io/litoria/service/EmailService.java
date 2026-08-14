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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.activation.DataHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import io.litoria.config.LitoriaConfig;

@ApplicationScoped
public class EmailService {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(EmailService.class);

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile(
            "\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    @Inject
    LitoriaConfig config;

    public void sendEmail(String projectDir, String fileName, Map<String, String> metadata)
            throws MessagingException, IOException {
        LitoriaConfig.SmtpConfig smtpConfig = config.smtp();

        LOG.debugf("SMTP host: %s", smtpConfig.host());
        LOG.debugf("SMTP port: %d", smtpConfig.port());
        LOG.debugf("SMTP user: %s", smtpConfig.user().orElse("<not set>"));
        LOG.debugf("SMTP pass: %s", smtpConfig.pass().map(p -> p.isBlank() ? "<blank>" : "****").orElse("<not set>"));
        LOG.debugf("SMTP secure: %s", smtpConfig.secure());
        LOG.debugf("SMTP requireTls: %s", smtpConfig.requireTls());
        LOG.debugf("SMTP tls.rejectUnauthorized: %s", smtpConfig.tls().rejectUnauthorized());
        LOG.debugf("SMTP oauth2.clientId: %s", smtpConfig.oauth2().clientId().map(v -> v.isBlank() ? "<blank>" : "****").orElse("<not set>"));
        LOG.debugf("SMTP oauth2.clientSecret: %s", smtpConfig.oauth2().clientSecret().map(v -> v.isBlank() ? "<blank>" : "****").orElse("<not set>"));
        LOG.debugf("SMTP oauth2.refreshToken: %s", smtpConfig.oauth2().refreshToken().map(v -> v.isBlank() ? "<blank>" : "****").orElse("<not set>"));
        LOG.debugf("OAuth2 detected: %s", isOAuth2(smtpConfig));

        if (smtpConfig.user().isEmpty() || smtpConfig.user().get().isBlank()) {
            throw new MessagingException(
                    "SMTP user not configured. Set the LITORIA_SMTP_USER environment variable.");
        }

        Properties props = buildSmtpProperties(smtpConfig);
        Session session = createSession(props, smtpConfig);

        Map<String, String> resolvedMetadata = new HashMap<>(metadata);
        if (!resolvedMetadata.containsKey("date")) {
            resolvedMetadata.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy")));
        }

        String subjectTemplate = resolvedMetadata.getOrDefault("subject",
                config.report().mail().subject().orElse("{author}'s weekly report : {date}"));
        String subject = resolveTemplate(subjectTemplate, resolvedMetadata);
        String from = resolveTemplate(config.report().mail().from(), resolvedMetadata);
        String to = config.report().mail().to()
                .map(t -> resolveTemplate(t, resolvedMetadata))
                .orElse(resolvedMetadata.getOrDefault("to", ""));

        if (to.isBlank()) {
            throw new MessagingException("Recipient 'to' address not configured. "
                    + "Set it in frontmatter or application.properties (litoria.report.mail.to).");
        }

        String htmlBody;
        Path embeddedFile = findHtmlFile(projectDir, fileName);
        if (embeddedFile != null && Files.exists(embeddedFile)) {
            htmlBody = resolveTemplate(Files.readString(embeddedFile), resolvedMetadata);
        } else {
            throw new IOException("No embedded file found in the destination directory."
                    + " Run 'generate --embed' first.");
        }

        String signatureTemplate = resolvedMetadata.getOrDefault("signature",
                config.report().signature());
        String signature = resolveTemplate(signatureTemplate, resolvedMetadata);
        signature = signature.replace("\n", "<br/>");
        htmlBody += "<br/><hr style=\"border:none;border-top:1px solid #ccc;margin:1em 0\"/>" + signature;

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);

        List<CidImage> cidImages = new ArrayList<>();
        htmlBody = convertDataUrisToCid(htmlBody, cidImages);

        if (cidImages.isEmpty()) {
            message.setContent(htmlBody, "text/html; charset=UTF-8");
        } else {
            MimeMultipart multipart = new MimeMultipart("related");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);
            for (CidImage cid : cidImages) {
                MimeBodyPart imagePart = new MimeBodyPart();
                imagePart.setDataHandler(new DataHandler(
                        new ByteArrayDataSource(cid.data, cid.mimeType)));
                imagePart.setHeader("Content-ID", "<" + cid.id + ">");
                imagePart.setDisposition(MimeBodyPart.INLINE);
                multipart.addBodyPart(imagePart);
            }
            message.setContent(multipart);
        }

        Transport.send(message);
    }

    public String resolveHtmlFile(String projectDir, String fileName) {
        try {
            Path path = findHtmlFile(projectDir, fileName);
            return path != null ? path.toString() : fileName + ".html";
        } catch (IOException e) {
            return fileName + ".html";
        }
    }

    private boolean isOAuth2(LitoriaConfig.SmtpConfig smtpConfig) {
        LitoriaConfig.SmtpConfig.Oauth2Config oauth2 = smtpConfig.oauth2();
        return oauth2.clientId().isPresent() && !oauth2.clientId().get().isBlank()
                && oauth2.clientSecret().isPresent() && !oauth2.clientSecret().get().isBlank()
                && oauth2.refreshToken().isPresent() && !oauth2.refreshToken().get().isBlank();
    }

    private Properties buildSmtpProperties(LitoriaConfig.SmtpConfig smtpConfig) {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpConfig.host());
        props.put("mail.smtp.port", String.valueOf(smtpConfig.port()));

        if (smtpConfig.secure()) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        if (smtpConfig.requireTls()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        if (!smtpConfig.tls().rejectUnauthorized()) {
            props.put("mail.smtp.ssl.trust", "*");
        }

        props.put("mail.smtp.auth", "true");

        if (isOAuth2(smtpConfig)) {
            props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        }

        return props;
    }

    private Session createSession(Properties props, LitoriaConfig.SmtpConfig smtpConfig) throws IOException {
        String user = smtpConfig.user().orElse("");

        String password;
        if (isOAuth2(smtpConfig)) {
            password = fetchAccessToken(
                    smtpConfig.oauth2().clientId().get(),
                    smtpConfig.oauth2().clientSecret().get(),
                    smtpConfig.oauth2().refreshToken().get());
        } else {
            password = smtpConfig.pass().orElse("");
        }

        String authPassword = password;
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, authPassword);
            }
        });
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
        String destination = config.generator().destination();
        Path destPath = Path.of(projectDir, destination);
        if (!Files.isDirectory(destPath)) {
            return null;
        }

        Path searchDir = findLatestSubfolder(destPath);
        Path target = searchDir.resolve(fileName + ".html");
        if (Files.exists(target)) {
            return target;
        }
        return null;
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

    private record CidImage(String id, String mimeType, byte[] data) {}
}
