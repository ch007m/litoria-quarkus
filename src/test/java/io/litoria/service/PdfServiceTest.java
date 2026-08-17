package io.litoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfServiceTest {

    @TempDir
    Path tempDir;

    private final PdfService pdfService = new PdfService();

    @Test
    void convertHtmlToPdfProducesValidPdf() throws Exception {
        Path htmlFile = tempDir.resolve("report.html");
        Files.writeString(htmlFile, """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Test</title>
                <style>body { font-family: sans-serif; }</style>
                </head>
                <body><h1>Hello World</h1><p>Some content here.</p></body>
                </html>
                """);

        Path pdfFile = pdfService.convertHtmlToPdf(htmlFile);

        assertThat(pdfFile).exists();
        assertThat(pdfFile.getFileName().toString()).isEqualTo("report.pdf");
        byte[] pdfBytes = Files.readAllBytes(pdfFile);
        assertThat(pdfBytes.length).isGreaterThan(0);
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void convertHtmlWithEmbeddedImageToPdf() throws Exception {
        String tinyPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        Path htmlFile = tempDir.resolve("embedded.html");
        Files.writeString(htmlFile, """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Embedded</title></head>
                <body><h1>Report</h1><img src="%s" alt="dot"/></body>
                </html>
                """.formatted(tinyPng));

        Path pdfFile = pdfService.convertHtmlToPdf(htmlFile);

        assertThat(pdfFile).exists();
        assertThat(Files.size(pdfFile)).isGreaterThan(0);
    }
}
