package io.litoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

@QuarkusTest
class TokenServiceTest {

    @Inject
    TokenService tokenService;

    @Test
    void resolvesInlineTokens() {
        String result = tokenService.resolveTokens("{blue}hello{/blue}");
        assertThat(result).isEqualTo("<span class=\"blue\">hello</span>");
    }

    @Test
    void resolvesMultipleInlineTokens() {
        String result = tokenService.resolveTokens("{green}a{/green} and {red}b{/red}");
        assertThat(result).isEqualTo("<span class=\"green\">a</span> and <span class=\"red\">b</span>");
    }

    @Test
    void resolvesBlockTokens() {
        String result = tokenService.resolveTokens("{subtitle}My subtitle{/subtitle}");
        assertThat(result).isEqualTo("<p class=\"subtitle\">My subtitle</p>");
    }

    @Test
    void resolvesSelfClosingBrandBar() {
        String result = tokenService.resolveTokens("{brand-bar}");
        assertThat(result).isEqualTo("<div class=\"brand-bar\"></div>");
    }

    @Test
    void resolvesSelfClosingFlowArrow() {
        String result = tokenService.resolveTokens("{flow-arrow}");
        assertThat(result).isEqualTo("<span class=\"flow-arrow\">&rarr;</span>");
    }

    @Test
    void resolvesCalloutWithNestedInline() {
        String result = tokenService.resolveTokens(
                "{callout}{highlight}Key:{/highlight} Some text{/callout}");
        assertThat(result).isEqualTo(
                "<div class=\"callout\"><span class=\"highlight\">Key:</span> Some text</div>");
    }

    @Test
    void resolvesCalloutVariants() {
        assertThat(tokenService.resolveTokens("{callout-red}warn{/callout-red}"))
                .isEqualTo("<div class=\"callout red-callout\">warn</div>");
        assertThat(tokenService.resolveTokens("{callout-teal}info{/callout-teal}"))
                .isEqualTo("<div class=\"callout teal-callout\">info</div>");
    }

    @Test
    void resolvesImageToken() {
        String result = tokenService.resolveTokens("{img src=\"image/logo.png\"}");
        assertThat(result).contains("<img src=\"image/logo.png\"");
    }

    @Test
    void resolvesImageTokenWithWidthAndCenter() {
        String result = tokenService.resolveTokens(
                "{img src=\"image/logo.png\" width=\"80%\" center}");
        assertThat(result).contains("text-align: center");
        assertThat(result).contains("max-width: 80%");
    }

    @Test
    void resolvesImageTokenWithRounded() {
        String result = tokenService.resolveTokens(
                "{img src=\"image/photo.jpg\" width=\"300\" rounded}");
        assertThat(result).contains("border-radius: 8px");
        assertThat(result).contains("max-width: 300px");
    }

    @Test
    void resolvesVideoToken() {
        String result = tokenService.resolveTokens("{video src=\"image/demo.mp4\" controls}");
        assertThat(result).contains("<video");
        assertThat(result).contains("controls");
        assertThat(result).contains("src=\"image/demo.mp4\"");
    }

    @Test
    void resolvesVideoTokenAutoplayDefault() {
        String result = tokenService.resolveTokens("{video src=\"image/demo.mp4\"}");
        assertThat(result).contains("data-autoplay");
    }

    @Test
    void resolvesVideoTokenWithPositionAndRounded() {
        String result = tokenService.resolveTokens(
                "{video src=\"demo.mp4\" controls center rounded width=\"90%\"}");
        assertThat(result).contains("text-align: center");
        assertThat(result).contains("border-radius: 8px");
        assertThat(result).contains("max-width: 90%");
    }

    @Test
    void resolvesCodeBlockToPreCode() {
        String input = "```java\npublic class Foo {}\n```";
        String result = tokenService.resolveTokens(input);
        assertThat(result).contains("<pre><code class=\"language-java\" data-trim data-noescape>");
        assertThat(result).contains("public class Foo {}");
        assertThat(result).contains("</code></pre>");
    }

    @Test
    void escapesHtmlInCodeBlocks() {
        String input = "```java\nList<String> items = new ArrayList<>();\n```";
        String result = tokenService.resolveTokens(input);
        assertThat(result).contains("List&lt;String&gt;");
        assertThat(result).doesNotContain("List<String>");
    }

    @Test
    void codeBlockWithoutLanguage() {
        String input = "```\nsome text\n```";
        String result = tokenService.resolveTokens(input);
        assertThat(result).contains("<pre><code data-trim data-noescape>");
        assertThat(result).doesNotContain("class=");
    }

    @Test
    void protectsTokensInsideInlineCode() {
        String input = "Use `{blue}text{/blue}` for blue text";
        String result = tokenService.resolveTokens(input);
        assertThat(result).contains("`{blue}text{/blue}`");
        assertThat(result).doesNotContain("<span class=\"blue\">");
    }

    @Test
    void protectsTokensInsideCodeBlocks() {
        String input = "```java\n{blue}not a token{/blue}\n```";
        String result = tokenService.resolveTokens(input);
        assertThat(result).contains("{blue}not a token{/blue}");
        assertThat(result).doesNotContain("<span class=\"blue\">");
    }

    @Test
    void preservesUnknownBraces() {
        String result = tokenService.resolveTokens("{unknown}text{/unknown}");
        assertThat(result).isEqualTo("{unknown}text{/unknown}");
    }

    @Test
    void resolvesFlowSequence() {
        String input = "{flow-step}Step 1{/flow-step}\n{flow-arrow}\n{flow-step}Step 2{/flow-step}";
        String result = tokenService.resolveTokens(input);
        assertThat(result).contains("<span class=\"flow-step\">Step 1</span>");
        assertThat(result).contains("<span class=\"flow-arrow\">&rarr;</span>");
        assertThat(result).contains("<span class=\"flow-step\">Step 2</span>");
    }

    @Test
    void resolvesFooterToken() {
        String result = tokenService.resolveTokens("{footer}Author &middot; 2026{/footer}");
        assertThat(result).isEqualTo("<p class=\"footer\">Author &middot; 2026</p>");
    }

    @Test
    void resolvesSupertitleToken() {
        String result = tokenService.resolveTokens("{supertitle}CONFERENCE{/supertitle}");
        assertThat(result).isEqualTo("<p class=\"supertitle\">CONFERENCE</p>");
    }
}
