package io.litoria.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestProfile(EmailServiceTest.SmtpTestProfile.class)
class EmailServiceTest {

    @Inject
    EmailService emailService;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
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

        List<Mail> sent = mailbox.getMailsSentTo("recipient@test.com");
        assertThat(sent).hasSize(1);

        Mail msg = sent.get(0);
        assertThat(msg.getSubject()).isEqualTo("John Doe's weekly report");
        assertThat(msg.getFrom()).isEqualTo("sender@test.com");
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

        Mail msg = mailbox.getMailsSentTo("recipient@test.com").get(0);
        assertThat(msg.getHtml()).contains("Author: Jane Doe");
        assertThat(msg.getHtml()).contains("Title: Architect");
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

        Mail msg = mailbox.getMailsSentTo("recipient@test.com").get(0);
        assertThat(msg.getHtml()).contains("Cheers");
        assertThat(msg.getHtml()).contains("John Doe");
        assertThat(msg.getHtml()).contains("Engineer");
    }

    @Test
    void sendEmailConvertsDataUriToInlineAttachmentWithCorrectExtension(@TempDir Path tempDir) throws Exception {
        Path destDir = tempDir.resolve("generated");
        Files.createDirectories(destDir);
        String pngDataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        Files.writeString(destDir.resolve("report.html"),
                "<html><body><img src=\"" + pngDataUri + "\"/></body></html>");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "John Doe");
        metadata.put("email", "sender@test.com");
        metadata.put("to", "recipient@test.com");

        emailService.sendEmail(tempDir.toString(), "report", metadata);

        Mail msg = mailbox.getMailsSentTo("recipient@test.com").get(0);
        assertThat(msg.getHtml()).contains("cid:");
        assertThat(msg.getHtml()).doesNotContain("data:image");
        assertThat(msg.getAttachments()).hasSize(1);
        assertThat(msg.getAttachments().get(0).getName()).endsWith(".png");
        assertThat(msg.getAttachments().get(0).getContentType()).isEqualTo("image/png");
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
    void sendEmailThrowsWhenSenderMissing(@TempDir Path tempDir) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("to", "recipient@test.com");

        assertThatThrownBy(() ->
                emailService.sendEmail(tempDir.toString(), "report", metadata))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Sender address");
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

    public static class SmtpTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("litoria.smtp.oauth2.client-id", ""),
                    Map.entry("litoria.smtp.oauth2.client-secret", ""),
                    Map.entry("litoria.smtp.oauth2.refresh-token", "")
            );
        }
    }
}
