package io.litoria.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.w3c.dom.Document;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@ApplicationScoped
public class PdfService {

    public Path convertHtmlToPdf(Path htmlFile) throws IOException {
        String htmlContent = Files.readString(htmlFile);
        Path pdfFile = htmlFile.resolveSibling(
                htmlFile.getFileName().toString().replaceFirst("\\.html$", ".pdf"));

        Document w3cDoc = new W3CDom().fromJsoup(Jsoup.parse(htmlContent));

        try (OutputStream os = Files.newOutputStream(pdfFile)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3cDoc, htmlFile.toUri().toString());
            builder.toStream(os);
            builder.run();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to generate PDF from " + htmlFile, e);
        }

        return pdfFile;
    }
}
