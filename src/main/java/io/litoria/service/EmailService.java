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

@ApplicationScoped
public class EmailService {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile(
            "\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    @Inject
    ConfigService configService;

    @SuppressWarnings("unchecked")
    public void sendEmail(Map<String, Object> config, String projectDir, String fileName) throws MessagingException, IOException {
        Map<String, Object> generator = configService.getGenerator(config);
        Map<String, Object> smtpConfig = configService.getMap(config, "smtp");
        Map<String, Object> reportConfig = configService.getMap(config, "report");
        Map<String, Object> mailConfig = configService.getMap(reportConfig, "mail");

        if (smtpConfig.isEmpty()) {
            throw new MessagingException(
                    "No 'smtp' section defined in the config file. The send command requires a report project type.");
        }

        Properties props = buildSmtpProperties(smtpConfig);
        Session session = createSession(props, smtpConfig);

        Map<String, Object> metadata = new java.util.HashMap<>(reportConfig);
        metadata.remove("mail");

        if (!metadata.containsKey("date")) {
            metadata.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy")));
        }

        String subject = resolveTemplate(getString(mailConfig, "subject"), metadata);
        String from = resolveTemplate(getString(mailConfig, "from"), metadata);
        String to = resolveTemplate(getString(mailConfig, "to"), metadata);

        String htmlBody;
        if (mailConfig.containsKey("body") && mailConfig.get("body") != null) {
            htmlBody = resolveTemplate(mailConfig.get("body").toString(), metadata);
        } else {
            Path embeddedFile = findHtmlFile(generator, projectDir, fileName);
            if (embeddedFile != null && Files.exists(embeddedFile)) {
                htmlBody = resolveTemplate(Files.readString(embeddedFile), metadata);
            } else {
                throw new IOException("No embedded file found in the destination directory."
                        + " Run 'generate --embed' first.");
            }
        }

        String signature = configService.getString(reportConfig, "signature");
        if (signature != null) {
            String sig = resolveTemplate(signature, metadata);
            sig = sig.replace("\n", "<br/>");
            htmlBody += "<br/><hr style=\"border:none;border-top:1px solid #ccc;margin:1em 0\"/>" + sig;
        }

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

    private boolean isOAuth2(Map<String, Object> smtpConfig) {
        Map<String, Object> oauth2 = configService.getMap(smtpConfig, "oauth2");
        return oauth2.containsKey("clientId")
                && oauth2.containsKey("clientSecret")
                && oauth2.containsKey("refreshToken");
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

        if (isOAuth2(smtpConfig)) {
            props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        }

        return props;
    }

    private Session createSession(Properties props, Map<String, Object> smtpConfig) throws IOException {
        String user = smtpConfig.getOrDefault("user", "").toString();

        String password;
        if (isOAuth2(smtpConfig)) {
            Map<String, Object> oauth2 = configService.getMap(smtpConfig, "oauth2");
            password = fetchAccessToken(
                    oauth2.get("clientId").toString(),
                    oauth2.get("clientSecret").toString(),
                    oauth2.get("refreshToken").toString());
        } else {
            password = smtpConfig.getOrDefault("pass", "").toString();
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

    public String resolveHtmlFile(Map<String, Object> config, String projectDir, String fileName) {
        try {
            Map<String, Object> generator = configService.getGenerator(config);
            Path path = findHtmlFile(generator, projectDir, fileName);
            return path != null ? path.toString() : fileName + ".html";
        } catch (IOException e) {
            return fileName + ".html";
        }
    }

    private Path findHtmlFile(Map<String, Object> generator, String projectDir, String fileName)
            throws IOException {
        String destination = configService.getString(generator, "destination");
        if (destination == null) {
            return null;
        }
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

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
