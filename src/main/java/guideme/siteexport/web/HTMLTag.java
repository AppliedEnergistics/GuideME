package guideme.siteexport.web;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class HTMLTag extends HTMLNode {
  private static final Pattern VALID_TAG_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9-]*$");
  private final Map<String, String> attributes = new LinkedHashMap<>();
  private final List<HTMLNode> children = new ArrayList<>();
  private String name;

  HTMLTag(String name) {
    this.name = Objects.requireNonNull(name, "tagName");
    if (!VALID_TAG_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid HTML tag name: " + name);
    }
  }

  @Override
  public @Nullable String name() {
    return name;
  }

  @Override
  public @Nullable String attribute(String name) {
    return attributes.get(name);
  }

  @Override
  public HTMLTag setAttribute(String name, @Nullable String value) {
    attributes.put(name, value);
    return this;
  }

  public HTMLTag setClassName(String className) {
    return setAttribute("class", className);
  }

  @Override
  public List<HTMLNode> children() {
    return Collections.unmodifiableList(children);
  }

  public HTMLTag append(HTMLFragment fragment) {
    for (var node : fragment.nodes()) {
      append(node);
    }
    return this;
  }

  public HTMLTag append(String text) {
    return append(HTMLNode.text(text));
  }

  public HTMLTag append(HTMLNode node) {
    children.add(Objects.requireNonNull(node, "node"));
    return this;
  }

  public HTMLTag setStyles(Map<String, String> styles) {
    var styleString = new StringBuilder();
    for (var entry : styles.entrySet()) {
      if (!styleString.isEmpty()) {
        styleString.append("; ");
      }
      styleString.append(entry.getKey()).append(": ").append(entry.getValue());
    }
    setAttribute("style", styleString.toString());
    return this;
  }

  private static final Set<String> VOID_ELEMENTS = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input",
      "link", "meta", "source", "track", "wbr");

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<").append(name);
    for (var entry : attributes.entrySet()) {
      sb.append(" ").append(entry.getKey()).append("=\"")
          .append(escapeAttribute(String.valueOf(entry.getValue())))
          .append("\"");
    }
    if (VOID_ELEMENTS.contains(name) && children.isEmpty()) {
      sb.append("/>");
    } else {
      sb.append(">");
      for (var child : children) {
        sb.append(child).append("\n");
      }
      sb.append("</").append(name).append(">");
    }
    return sb.toString();
  }

  @Override
  public String textContent() {
    var result = new StringBuilder();
    for (HTMLNode child : children) {
      result.append(child.textContent());
    }
    return result.toString();
  }
}
