package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestProfile(EmailServiceTest.SmtpTestProfile.class)
class EmailServiceTest {

    static final int SMTP_PORT = 3025;

    @Inject
    EmailService emailService;

    GreenMail greenMail;

    @BeforeEach
    void startSmtp() {
        greenMail = new GreenMail(new ServerSetup(SMTP_PORT, "localhost", "smtp"));
        greenMail.setUser("sender@test.com", "sender@test.com", "testpass");
        greenMail.start();
    }

    @AfterEach
    void stopSmtp() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    void sendEmailDeliversHtmlContent(@TempDir Path tempDir) throws Exception {
        Path destDir = tempDir.resolve("generated");
        Files.createDirectories(destDir);
        Files.writeString(destDir.resolve("report.html"),
                "<html><body><h1>{author}'s report</h1></body></html>");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "John Doe");
        metadata.put("title", "Engineer");
        metadata.put("email", "sender@test.com");
        metadata.put("to", "recipient@test.com");
        metadata.put("subject", "{author}'s weekly report");

        emailService.sendEmail(tempDir.toString(), "report", metadata);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage msg = received[0];
        assertThat(msg.getSubject()).isEqualTo("John Doe's weekly report");
        assertThat(msg.getFrom()[0].toString()).isEqualTo("sender@test.com");
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("recipient@test.com");
    }

    @Test
    void sendEmailResolvesPlaceholdersInBody(@TempDir Path tempDir) throws Exception {
        Path destDir = tempDir.resolve("generated");
        Files.createDirectories(destDir);
        Files.writeString(destDir.resolve("report.html"),
                "<html><body><p>Author: {author}, Title: {title}</p></body></html>");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "Jane Doe");
        metadata.put("title", "Architect");
        metadata.put("email", "sender@test.com");
        metadata.put("to", "recipient@test.com");

        emailService.sendEmail(tempDir.toString(), "report", metadata);

        MimeMessage msg = greenMail.getReceivedMessages()[0];
        String body = extractHtmlBody(msg);
        assertThat(body).contains("Author: Jane Doe");
        assertThat(body).contains("Title: Architect");
    }

    @Test
    void sendEmailAppendsSignatureFromMetadata(@TempDir Path tempDir) throws Exception {
        Path destDir = tempDir.resolve("generated");
        Files.createDirectories(destDir);
        Files.writeString(destDir.resolve("report.html"),
                "<html><body><p>Content</p></body></html>");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "John Doe");
        metadata.put("title", "Engineer");
        metadata.put("email", "sender@test.com");
        metadata.put("to", "recipient@test.com");
        metadata.put("signature", "Cheers\n----\n{author}\n{title}");

        emailService.sendEmail(tempDir.toString(), "report", metadata);

        MimeMessage msg = greenMail.getReceivedMessages()[0];
        String body = extractHtmlBody(msg);
        assertThat(body).contains("Cheers");
        assertThat(body).contains("John Doe");
        assertThat(body).contains("Engineer");
    }

    @Test
    void sendEmailThrowsWhenToMissing(@TempDir Path tempDir) throws Exception {
        Path destDir = tempDir.resolve("generated");
        Files.createDirectories(destDir);
        Files.writeString(destDir.resolve("report.html"), "<html><body>test</body></html>");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "John");
        metadata.put("email", "sender@test.com");

        assertThatThrownBy(() ->
                emailService.sendEmail(tempDir.toString(), "report", metadata))
                .hasMessageContaining("to");
    }

    @Test
    void sendEmailThrowsWhenSmtpUserAndEmailMissing(@TempDir Path tempDir) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("to", "recipient@test.com");

        assertThatThrownBy(() ->
                emailService.sendEmail(tempDir.toString(), "report", metadata))
                .hasMessageContaining("SMTP user");
    }

    @Test
    void sendEmailThrowsWhenHtmlFileNotFound(@TempDir Path tempDir) throws IOException {
        Path destDir = tempDir.resolve("generated");
        Files.createDirectories(destDir);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "John");
        metadata.put("email", "sender@test.com");
        metadata.put("to", "recipient@test.com");

        assertThatThrownBy(() ->
                emailService.sendEmail(tempDir.toString(), "nonexistent", metadata))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTML files");
    }

    private String extractHtmlBody(MimeMessage msg) throws Exception {
        Object content = msg.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                jakarta.mail.BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/html")) {
                    return (String) part.getContent();
                }
            }
        }
        return content.toString();
    }

    public static class SmtpTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("litoria.smtp.host", "localhost"),
                    Map.entry("litoria.smtp.port", String.valueOf(SMTP_PORT)),
                    Map.entry("litoria.smtp.secure", "false"),
                    Map.entry("litoria.smtp.require-tls", "false"),
                    Map.entry("litoria.smtp.user", ""),
                    Map.entry("litoria.smtp.pass", "testpass"),
                    Map.entry("litoria.smtp.oauth2.client-id", ""),
                    Map.entry("litoria.smtp.oauth2.client-secret", ""),
                    Map.entry("litoria.smtp.oauth2.refresh-token", "")
            );
        }
    }
}
