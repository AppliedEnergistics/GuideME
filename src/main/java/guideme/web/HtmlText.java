package guideme.web;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class HtmlText extends HtmlNode {
    private String content;

    public HtmlText(String content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public String name() {
        return "";
    }

    public String content() {
        return content;
    }

    @Override
    public String textContent() {
        return content();
    }

    @Override
    public String outerHtml() {
        return escapeHtml(textContent());
    }

    @Override
    public @Nullable String attribute(String name) {
        return null;
    }

    @Override
    public HtmlTag setAttribute(String name, @Nullable String value) {
        throw new UnsupportedOperationException("Cannot set attributes on text nodes.");
    }

    @Override
    public List<HtmlNode> children() {
        return List.of();
    }
}
