package guideme.siteexport.web;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects the text of all pages for the search function of the website.
 */
final class SearchIndex {
    static final String FILENAME = "search-index.json";

    /**
     * Text in these elements continues the surrounding text. All other elements are separated from their surroundings
     * by whitespace.
     */
    private static final Set<String> INLINE_ELEMENTS = Set.of("a", "abbr", "b", "code", "del", "em", "i", "kbd",
            "mark", "s", "small", "span", "strong", "sub", "sup", "u");

    /**
     * Elements whose content is not part of the page text.
     */
    private static final Set<String> IGNORED_ELEMENTS = Set.of("template", "script", "style");

    /**
     * @param url Path to the page relative to the root of the website.
     */
    record Entry(String url, String title, String text) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    void add(String pageId, String url, String title, HtmlFragment content) {
        var text = new StringBuilder();
        for (var node : content.nodes()) {
            appendText(node, text);
        }
        entries.put(pageId, new Entry(url, title, text.toString().replaceAll("\\s+", " ").trim()));
    }

    private static void appendText(HtmlNode node, StringBuilder text) {
        if (node instanceof HtmlText textNode) {
            text.append(textNode.content());
            return;
        }

        var name = node.name();
        if (IGNORED_ELEMENTS.contains(name)) {
            return;
        }
        var separate = !INLINE_ELEMENTS.contains(name);
        if (separate) {
            text.append(' ');
        }
        for (var child : node.children()) {
            appendText(child, text);
        }
        if (separate) {
            text.append(' ');
        }
    }

    void write(Path outputFolder) throws IOException {
        var sortedEntries = entries.values().stream()
                .sorted(Comparator.comparing(Entry::url))
                .toList();
        Files.writeString(outputFolder.resolve(FILENAME), new Gson().toJson(sortedEntries), StandardCharsets.UTF_8);
    }
}
