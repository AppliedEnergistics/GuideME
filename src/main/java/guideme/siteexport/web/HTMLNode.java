package guideme.siteexport.web;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public sealed abstract class HTMLNode permits HTMLTag, HTMLText {

  /**
   * {@return the name of the HTML tag or an empty string for text nodes}
   */
  public abstract String name();

  @Nullable
  public abstract String attribute(String name);

  public final boolean hasAttribute(String name) {
    return attribute(name) != null;
  }

  public abstract HTMLTag setAttribute(String name, String value);

  public final HTMLTag setAttribute(String name, float value) {
    return setAttribute(name, "" + value);
  }

  public final HTMLTag setAttribute(String name, int value) {
    return setAttribute(name, "" + value);
  }

  public static HTMLTag tag(String name) {
    return new HTMLTag(name);
  }

  public static HTMLTag tag(String name, HTMLFragment fragment) {
    var tag = tag(name);
    tag.append(fragment);
    return tag;
  }

  public static HTMLTag tag(String name, HTMLNode... children) {
    return tag(name, Arrays.asList(children));
  }

  public static HTMLTag tag(String name, List<? extends HTMLNode> children) {
    var tag = tag(name);
    for (var node : children) {
      tag.append(node);
    }
    return tag;
  }

  public static HTMLText text(String content) {
    return new HTMLText(content);
  }

  public abstract List<HTMLNode> children();

  public static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public static String escapeAttribute(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public abstract String textContent();
}

