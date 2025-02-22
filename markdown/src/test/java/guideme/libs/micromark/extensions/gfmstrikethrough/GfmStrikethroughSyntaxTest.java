package guideme.libs.micromark.extensions.gfmstrikethrough;

import static org.junit.jupiter.api.Assertions.assertEquals;

import guideme.libs.micromark.Extension;
import guideme.libs.micromark.Micromark;
import guideme.libs.micromark.html.CompileOptions;
import guideme.libs.micromark.html.HtmlCompiler;
import guideme.libs.micromark.html.ParseOptions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

public class GfmStrikethroughSyntaxTest {

    @TestFactory
    public List<DynamicNode> generateTests() {
        var fixtureNames = new String[] {
                "balance",
                "basic.comment",
                "basic",
                "flank",
                "interplay"
        };

        var tests = new ArrayList<DynamicNode>();
        for (var fixtureName : fixtureNames) {
            tests.add(DynamicTest.dynamicTest(fixtureName, () -> {
                String markdown, expectedHtml;
                try (var input = GfmStrikethroughSyntaxTest.class.getResourceAsStream(fixtureName + ".md")) {
                    markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                try (var input = GfmStrikethroughSyntaxTest.class.getResourceAsStream(fixtureName + ".html")) {
                    expectedHtml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }

                var html = toHtmlWithGfm(markdown);

                if (!html.endsWith("\n")) {
                    html += "\n";
                }

                assertEquals(expectedHtml, html);
            }));
        }
        return tests;
    }

    @Test
    public void testMixingEmphasisAndStrikethrough() {
        String markdown = "a ~~two *marker two~~ marker* b";
        String expected = "<p>a <del>two *marker two</del> marker* b</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldSupportStrikethroughWithOneTilde() {
        String markdown = "a ~b~";
        String expected = "<p>a <del>b</del></p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldSupportStrikethroughWithTwoTildes() {
        String markdown = "a ~~b~~";
        String expected = "<p>a <del>b</del></p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldNotSupportStrikethroughWithThreeTildes() {
        String markdown = "a ~~~b~~~";
        String expected = "<p>a ~~~b~~~</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldSupportStrikethroughAfterEscapedTilde() {
        String markdown = "a \\~~~b~~ c";
        String expected = "<p>a ~<del>b</del> c</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldSupportNestedStrikethrough() {
        String markdown = "a ~~b ~~c~~ d~~ e";
        String expected = "<p>a <del>b <del>c</del> d</del> e</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldOpenIfPrecededByWhitespaceAndFollowedByPunctuation() {
        String markdown = "a ~-1~ b";
        String expected = "<p>a <del>-1</del> b</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldCloseIfPrecededByPunctuationAndFollowedByWhitespace() {
        String markdown = "a ~b.~ c";
        String expected = "<p>a <del>b.</del> c</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldCloseIfPrecededAndFollowedByPunctuation() {
        String markdown = "~b.~.";
        String expected = "<p><del>b.</del>.</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldNotSupportStrikethroughWithOneTildeIfSingleTildeIsFalse() {
        String markdown = "a ~b~ ~~c~~ d";
        String expected = "<p>a ~b~ <del>c</del> d</p>";
        assertEquals(expected, toHtmlWithGfm(markdown, false));
    }

    @Test
    public void shouldSupportStrikethroughWithOneTildeIfSingleTildeIsTrue() {
        String markdown = "a ~b~ ~~c~~ d";
        String expected = "<p>a <del>b</del> <del>c</del> d</p>";
        assertEquals(expected, toHtmlWithGfm(markdown));
    }

    @Test
    public void shouldSkipStrikethroughIfDisabled() {
        var markdown = "a ~~b~~";
        var parseOptions = new ParseOptions();
        parseOptions.withSyntaxExtension(GfmStrikethroughSyntax.INSTANCE);
        parseOptions.withSyntaxExtension(new Extension() {
            {
                nullDisable.add("strikethrough");
            }
        });

        var compileOptions = new CompileOptions();
        compileOptions.withExtension(GfmStrikethroughHtml.EXTENSION);

        var html = new HtmlCompiler(compileOptions)
                .compile(Micromark.parseAndPostprocess(markdown, parseOptions));

        assertEquals("<p>a ~~b~~</p>", html);
    }

    @Test
    public void testShouldSkipStrikethroughIfDisabled() {
        var markdown = "a ~~b~~";
        var parseOptions = new ParseOptions();
        parseOptions.withSyntaxExtension(GfmStrikethroughSyntax.INSTANCE);
        parseOptions.withSyntaxExtension(new Extension() {
            {
                nullDisable.add("strikethrough");
            }
        });

        var compileOptions = new CompileOptions();
        compileOptions.withExtension(GfmStrikethroughHtml.EXTENSION);

        var html = new HtmlCompiler(compileOptions)
                .compile(Micromark.parseAndPostprocess(markdown, parseOptions));

        assertEquals("<p>a ~~b~~</p>", html);
    }

    private String toHtmlWithGfm(String markdown) {
        return toHtmlWithGfm(markdown, true);
    }

    private String toHtmlWithGfm(String markdown, boolean allowSingleTilde) {
        var parseOptions = new ParseOptions();
        parseOptions
                .withSyntaxExtension(new GfmStrikethroughSyntax(new GfmStrikethroughSyntax.Options(allowSingleTilde)));

        var compileOptions = new CompileOptions();
        compileOptions.withExtension(GfmStrikethroughHtml.EXTENSION);
        compileOptions.allowDangerousHtml();
        compileOptions.allowDangerousProtocol();

        return new HtmlCompiler(compileOptions).compile(Micromark.parseAndPostprocess(markdown, parseOptions));
    }
}
