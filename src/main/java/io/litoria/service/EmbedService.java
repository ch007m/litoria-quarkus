package io.litoria.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@ApplicationScoped
public class EmbedService {

    private static final Pattern CSS_RULE_PATTERN = Pattern.compile(
            "([^{}]+)\\{([^}]+)}", Pattern.MULTILINE);
    private static final Pattern AT_RULE_BLOCK = Pattern.compile(
            "(@[^{}]+\\{[^}]*})", Pattern.MULTILINE);
    private static final Pattern AT_RULE_SINGLE = Pattern.compile(
            "(@[^{;]+;)");

    public void embedStylesAndImages(String inputFile, String outputFile, String sourceDir, String imageDir)
            throws IOException {
        Document doc = Jsoup.parse(new File(inputFile), "UTF-8");
        Path inputDir = Path.of(inputFile).getParent();

        List<String> retainedRules = new ArrayList<>();

        embedCssFromLinkedStylesheets(doc, inputDir, retainedRules);
        embedCssFromStyleBlocks(doc, retainedRules);

        if (!retainedRules.isEmpty()) {
            Element head = doc.head();
            Element style = head.appendElement("style");
            style.text(String.join("\n", retainedRules));
        }

        fixFontAwesomeIcons(doc);

        embedImagesAsBase64(doc, inputDir,
                sourceDir != null ? Path.of(sourceDir) : null,
                imageDir != null ? Path.of(imageDir) : null);

        Files.writeString(Path.of(outputFile), doc.html());
    }

    private void embedCssFromLinkedStylesheets(Document doc, Path baseDir, List<String> retainedRules)
            throws IOException {
        Elements links = doc.select("link[rel=stylesheet]");
        for (Element link : links) {
            String href = link.attr("href");
            if (href.startsWith("http")) {
                continue;
            }
            Path cssPath = baseDir.resolve(href);
            if (Files.exists(cssPath)) {
                String css = Files.readString(cssPath);
                applyCssRules(doc, css, retainedRules);
                link.remove();
            }
        }
    }

    private void embedCssFromStyleBlocks(Document doc, List<String> retainedRules) {
        Elements styles = doc.select("style");
        for (Element style : styles) {
            String css = style.html();
            applyCssRules(doc, css, retainedRules);
            style.remove();
        }
    }

    private void applyCssRules(Document doc, String css, List<String> retainedRules) {
        css = css.replaceAll("/\\*.*?\\*/", "");

        Matcher atBlock = AT_RULE_BLOCK.matcher(css);
        while (atBlock.find()) {
            retainedRules.add(atBlock.group(1));
        }
        css = AT_RULE_BLOCK.matcher(css).replaceAll("");

        Matcher atSingle = AT_RULE_SINGLE.matcher(css);
        while (atSingle.find()) {
            retainedRules.add(atSingle.group(1));
        }
        css = AT_RULE_SINGLE.matcher(css).replaceAll("");

        Matcher matcher = CSS_RULE_PATTERN.matcher(css);
        while (matcher.find()) {
            String selector = matcher.group(1).trim();
            String declarations = matcher.group(2).trim();

            if (selector.contains(":") || selector.contains("@")) {
                retainedRules.add(selector + " { " + declarations + " }");
                continue;
            }

            String[] selectors = selector.split(",");
            for (String sel : selectors) {
                sel = sel.trim();
                if (sel.isEmpty()) {
                    continue;
                }
                try {
                    Elements elements = doc.select(sel);
                    for (Element el : elements) {
                        String existingStyle = el.attr("style");
                        String newStyle = existingStyle.isEmpty()
                                ? declarations
                                : existingStyle + "; " + declarations;
                        el.attr("style", newStyle);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void fixFontAwesomeIcons(Document doc) {
        Elements faIcons = doc.select("i.fa");
        for (Element icon : faIcons) {
            String style = icon.attr("style")
                    .replaceAll("font-style:\\s*italic\\s*;?", "");
            icon.attr("style", style + "; font-style: normal");
        }
    }

    private void embedImagesAsBase64(Document doc, Path baseDir, Path sourceDir, Path imageDir)
            throws IOException {
        Elements images = doc.select("img");
        for (Element img : images) {
            String src = img.attr("src");
            if (src.startsWith("data:") || src.startsWith("http")) {
                continue;
            }

            Path imgPath = baseDir.resolve(src);
            if (!Files.exists(imgPath) && imageDir != null) {
                imgPath = imageDir.resolve(Path.of(src).getFileName());
            }
            if (!Files.exists(imgPath) && sourceDir != null) {
                imgPath = sourceDir.resolve(src);
            }

            if (Files.exists(imgPath)) {
                String ext = getExtension(imgPath.toString());
                if ("jpg".equals(ext)) {
                    ext = "jpeg";
                }
                byte[] data = Files.readAllBytes(imgPath);
                String base64 = Base64.getEncoder().encodeToString(data);
                img.attr("src", "data:image/" + ext + ";base64," + base64);
            }
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
