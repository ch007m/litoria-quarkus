package io.litoria.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenService {

    private static final Map<String, TokenDef> PAIRED_TOKENS = new LinkedHashMap<>();
    private static final Map<String, String> SELF_CLOSING_TOKENS = new LinkedHashMap<>();

    private static final Pattern IMG_TOKEN = Pattern.compile("\\{img\\s+([^}]+)}");
    private static final Pattern VIDEO_TOKEN = Pattern.compile("\\{video\\s+([^}]+)}");
    private static final Pattern CODE_BLOCK = Pattern.compile("```(\\w*)\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE = Pattern.compile("(`{1,2})([^`]+?)\\1");
    private static final Pattern QUOTED_PARAM = Pattern.compile("(\\w[\\w-]*)=\"([^\"]*)\"");

    static {
        PAIRED_TOKENS.put("blue", new TokenDef("span", "blue"));
        PAIRED_TOKENS.put("green", new TokenDef("span", "green"));
        PAIRED_TOKENS.put("teal", new TokenDef("span", "teal"));
        PAIRED_TOKENS.put("orange", new TokenDef("span", "orange"));
        PAIRED_TOKENS.put("red", new TokenDef("span", "red"));
        PAIRED_TOKENS.put("highlight", new TokenDef("span", "highlight"));
        PAIRED_TOKENS.put("dimmed", new TokenDef("span", "dimmed"));
        PAIRED_TOKENS.put("pass", new TokenDef("span", "pass"));
        PAIRED_TOKENS.put("fail", new TokenDef("span", "fail"));
        PAIRED_TOKENS.put("best", new TokenDef("span", "best"));
        PAIRED_TOKENS.put("flow-step", new TokenDef("span", "flow-step"));

        PAIRED_TOKENS.put("subtitle", new TokenDef("p", "subtitle"));
        PAIRED_TOKENS.put("supertitle", new TokenDef("p", "supertitle"));
        PAIRED_TOKENS.put("footer", new TokenDef("p", "footer"));
        PAIRED_TOKENS.put("callout", new TokenDef("div", "callout"));
        PAIRED_TOKENS.put("callout-red", new TokenDef("div", "callout red-callout"));
        PAIRED_TOKENS.put("callout-teal", new TokenDef("div", "callout teal-callout"));
        PAIRED_TOKENS.put("callout-orange", new TokenDef("div", "callout orange-callout"));

        SELF_CLOSING_TOKENS.put("brand-bar", "<div class=\"brand-bar\"></div>");
        SELF_CLOSING_TOKENS.put("flow-arrow", "<span class=\"flow-arrow\">&rarr;</span>");
    }

    record TokenDef(String tag, String cssClass) {}

    public String resolveTokens(String markdown) {
        List<String> codeBlocks = new ArrayList<>();
        Matcher codeMatcher = CODE_BLOCK.matcher(markdown);
        StringBuilder protected_ = new StringBuilder();
        while (codeMatcher.find()) {
            String lang = codeMatcher.group(1);
            String code = escapeHtml(codeMatcher.group(2));
            String langAttr = lang.isEmpty() ? "" : " class=\"language-" + lang + "\"";
            codeBlocks.add("<pre><code" + langAttr + " data-trim data-noescape>\n"
                    + code + "\n</code></pre>");
            codeMatcher.appendReplacement(protected_,
                    Matcher.quoteReplacement("§CODEBLOCK_" + (codeBlocks.size() - 1) + "§"));
        }
        codeMatcher.appendTail(protected_);
        markdown = protected_.toString();

        List<String> inlineCode = new ArrayList<>();
        Matcher inlineMatcher = INLINE_CODE.matcher(markdown);
        StringBuilder protected2 = new StringBuilder();
        while (inlineMatcher.find()) {
            inlineCode.add(inlineMatcher.group(0));
            inlineMatcher.appendReplacement(protected2,
                    Matcher.quoteReplacement("§INLINECODE_" + (inlineCode.size() - 1) + "§"));
        }
        inlineMatcher.appendTail(protected2);
        markdown = protected2.toString();

        markdown = resolveMediaTokens(markdown, IMG_TOKEN, true);
        markdown = resolveMediaTokens(markdown, VIDEO_TOKEN, false);

        for (var entry : PAIRED_TOKENS.entrySet()) {
            String name = entry.getKey();
            TokenDef def = entry.getValue();
            markdown = markdown.replace(
                    "{" + name + "}", "<" + def.tag + " class=\"" + def.cssClass + "\">");
            markdown = markdown.replace(
                    "{/" + name + "}", "</" + def.tag + ">");
        }

        for (var entry : SELF_CLOSING_TOKENS.entrySet()) {
            markdown = markdown.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        for (int i = 0; i < inlineCode.size(); i++) {
            markdown = markdown.replace("§INLINECODE_" + i + "§", inlineCode.get(i));
        }
        for (int i = 0; i < codeBlocks.size(); i++) {
            markdown = markdown.replace("§CODEBLOCK_" + i + "§", codeBlocks.get(i));
        }

        return markdown;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String resolveMediaTokens(String text, Pattern pattern, boolean isImage) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String params = matcher.group(1);
            String replacement = isImage ? buildImageHtml(params) : buildVideoHtml(params);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String buildImageHtml(String params) {
        Map<String, String> attrs = parseQuotedParams(params);
        String src = attrs.getOrDefault("src", "");
        String width = attrs.get("width");
        String alt = attrs.getOrDefault("alt", "");
        String position = parsePosition(params);
        boolean rounded = params.contains(" rounded");

        StringBuilder style = new StringBuilder();
        if (width != null) {
            style.append("max-width: ").append(normalizeWidth(width)).append(";");
        }
        if (rounded) {
            style.append(" border-radius: 8px;");
        }

        String imgTag = "<img src=\"" + src + "\" alt=\"" + alt + "\""
                + (style.length() > 0 ? " style=\"" + style.toString().trim() + "\"" : "")
                + ">";

        return wrapWithPosition(imgTag, position);
    }

    private String buildVideoHtml(String params) {
        Map<String, String> attrs = parseQuotedParams(params);
        String src = attrs.getOrDefault("src", "");
        String width = attrs.get("width");
        String preload = attrs.get("preload");
        String type = attrs.get("type");
        String position = parsePosition(params);
        boolean controls = params.matches(".*\\bcontrols\\b.*");
        boolean autoplay = params.matches(".*\\bautoplay\\b.*");
        boolean loop = params.matches(".*\\bloop\\b.*");
        boolean muted = params.matches(".*\\bmuted\\b.*");
        boolean rounded = params.matches(".*\\brounded\\b.*");

        StringBuilder tag = new StringBuilder("<video");
        if (controls) tag.append(" controls");
        if (autoplay || !controls) tag.append(" data-autoplay");
        if (loop) tag.append(" loop");
        if (muted) tag.append(" muted");
        if (preload != null) tag.append(" preload=\"").append(preload).append("\"");
        tag.append(" src=\"").append(src).append("\"");
        if (type != null) tag.append(" type=\"").append(type).append("\"");

        StringBuilder style = new StringBuilder();
        if (width != null) {
            style.append("max-width: ").append(normalizeWidth(width)).append(";");
        }
        if (rounded) {
            style.append(" border-radius: 8px;");
        }
        if (style.length() > 0) {
            tag.append(" style=\"").append(style.toString().trim()).append("\"");
        }
        tag.append("></video>");

        return wrapWithPosition(tag.toString(), position);
    }

    private Map<String, String> parseQuotedParams(String params) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = QUOTED_PARAM.matcher(params);
        while (m.find()) {
            result.put(m.group(1), m.group(2));
        }
        return result;
    }

    private String parsePosition(String params) {
        if (params.matches(".*\\bcenter\\b.*")) return "center";
        if (params.matches(".*\\bright\\b.*")) return "right";
        if (params.matches(".*\\bleft\\b.*")) return "left";
        return null;
    }

    private String normalizeWidth(String width) {
        if (width.endsWith("%") || width.endsWith("px") || width.endsWith("em")) {
            return width;
        }
        return width + "px";
    }

    private String wrapWithPosition(String element, String position) {
        if (position != null) {
            return "<div style=\"text-align: " + position + ";\">" + element + "</div>";
        }
        return element;
    }
}
