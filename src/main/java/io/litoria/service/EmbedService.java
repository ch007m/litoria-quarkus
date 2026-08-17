package io.litoria.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import jakarta.enterprise.context.ApplicationScoped;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
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
    private static final Pattern FONT_URL_PATTERN = Pattern.compile(
            "url\\(['\"]?([^)'\"]+\\.woff2[^)'\"]*)['\"]?\\)");

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
            style.appendChild(new DataNode(String.join("\n", retainedRules)));
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
                String css = downloadText(href);
                if (css != null) {
                    String baseUrl = href.substring(0, href.lastIndexOf('/') + 1);
                    css = embedFontFiles(css, baseUrl);
                    applyCssRules(doc, css, retainedRules);
                    link.remove();
                }
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
        css = css.replaceAll("(?s)/\\*.*?\\*/", "");

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

    private static final Pattern FONT_FACE_PATTERN = Pattern.compile(
            "@font-face\\s*\\{[^}]*\\}", Pattern.DOTALL);

    private String embedFontFiles(String css, String baseUrl) {
        Matcher ffm = FONT_FACE_PATTERN.matcher(css);
        StringBuilder sb = new StringBuilder();
        while (ffm.find()) {
            String fontFace = ffm.group();
            Matcher urlm = FONT_URL_PATTERN.matcher(fontFace);
            if (urlm.find()) {
                String fontUrl = urlm.group(1);
                String absoluteUrl = resolveUrl(baseUrl, fontUrl);
                byte[] fontData = downloadBytes(absoluteUrl);
                if (fontData != null) {
                    String dataUri = "data:font/woff2;base64,"
                            + Base64.getEncoder().encodeToString(fontData);
                    Matcher familyM = Pattern.compile("font-family:\\s*'([^']+)'").matcher(fontFace);
                    String family = familyM.find() ? familyM.group(1) : "FontAwesome";
                    String replacement = "@font-face{font-family:'" + family
                            + "';src:url('" + dataUri + "') format('woff2');"
                            + "font-weight:normal;font-style:normal}";
                    ffm.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                    continue;
                }
            }
            ffm.appendReplacement(sb, Matcher.quoteReplacement(fontFace));
        }
        ffm.appendTail(sb);
        return sb.toString();
    }

    private String resolveUrl(String baseUrl, String relative) {
        if (relative.startsWith("http")) {
            return relative;
        }
        return URI.create(baseUrl).resolve(relative).toString();
    }

    private String downloadText(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] downloadBytes(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static final Map<String, Color> ADMONITION_COLORS = Map.of(
            "icon-note", new Color(0x20, 0x7d, 0x98),
            "icon-tip", new Color(0x11, 0x11, 0x11),
            "icon-warning", new Color(0xbf, 0x69, 0x00),
            "icon-caution", new Color(0xbf, 0x34, 0x00),
            "icon-important", new Color(0xbf, 0x00, 0x00)
    );

    private void fixFontAwesomeIcons(Document doc) {
        Elements faIcons = doc.select("i.fa");
        for (Element icon : faIcons) {
            String iconClass = null;
            for (String cls : icon.classNames()) {
                if (cls.startsWith("icon-") && ADMONITION_COLORS.containsKey(cls)) {
                    iconClass = cls;
                    break;
                }
            }
            if (iconClass != null) {
                Color color = ADMONITION_COLORS.get(iconClass);
                String pngDataUri = generateAdmonitionPng(iconClass, color);
                if (pngDataUri != null) {
                    Element img = new Element("img");
                    img.attr("src", pngDataUri);
                    img.attr("alt", icon.attr("title"));
                    img.attr("width", "28");
                    img.attr("height", "28");
                    img.attr("style", "vertical-align: middle;");
                    icon.replaceWith(img);
                }
            } else {
                String style = icon.attr("style")
                        .replaceAll("font-style:\\s*italic\\s*;?", "");
                icon.attr("style", style + "; font-style: normal");
            }
        }
    }

    private String generateAdmonitionPng(String iconClass, Color color) {
        int size = 48;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (iconClass) {
            case "icon-note":
            case "icon-important":
                g.setColor(color);
                g.fill(new Ellipse2D.Double(0, 0, size, size));
                g.setColor(Color.WHITE);
                if ("icon-note".equals(iconClass)) {
                    g.fill(new Ellipse2D.Double(20, 8, 8, 8));
                    g.fillRoundRect(20, 20, 8, 22, 4, 4);
                } else {
                    g.fillRoundRect(20, 8, 8, 22, 4, 4);
                    g.fill(new Ellipse2D.Double(20, 34, 8, 8));
                }
                break;
            case "icon-warning":
            case "icon-caution":
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(size / 2.0, 2);
                triangle.lineTo(size - 2, size - 2);
                triangle.lineTo(2, size - 2);
                triangle.closePath();
                g.setColor(color);
                g.fill(triangle);
                g.setColor(Color.WHITE);
                g.fillRoundRect(20, 14, 8, 18, 4, 4);
                g.fill(new Ellipse2D.Double(20, 35, 8, 8));
                break;
            case "icon-tip":
                g.setColor(color);
                g.setStroke(new BasicStroke(3f));
                g.draw(new Ellipse2D.Double(10, 4, 28, 28));
                g.fillRect(16, 32, 16, 4);
                g.fillRect(18, 38, 12, 4);
                g.fillRect(20, 44, 8, 3);
                break;
        }
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
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
