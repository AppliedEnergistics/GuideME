package guideme.siteexport.web;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class HTMLText extends HTMLNode {
  private String content;

  public HTMLText(String content) {
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
  public @Nullable String attribute(String name) {
    return null;
  }

  @Override
  public HTMLTag setAttribute(String name, @Nullable String value) {
    throw new UnsupportedOperationException("Cannot set attributes on text nodes.");
  }

  @Override
  public List<HTMLNode> children() {
    return List.of();
  }
}
