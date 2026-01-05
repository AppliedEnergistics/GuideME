package guideme.siteexport.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HTMLFragment {
  private final List<HTMLNode> nodes = new ArrayList<>();

  public HTMLFragment() {
  }

  public HTMLFragment(HTMLNode... nodes) {
    Collections.addAll(this.nodes, nodes);
  }

  public List<HTMLNode> nodes() {
    return Collections.unmodifiableList(nodes);
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  public void append(HTMLNode node) {
    nodes.add(node);
  }

  public void append(String text) {
    nodes.add(HTMLNode.text(text));
  }

  public void append(HTMLFragment fragment) {
    nodes.addAll(fragment.nodes);
  }
}
