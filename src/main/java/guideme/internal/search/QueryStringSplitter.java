package guideme.internal.search;

import java.util.ArrayList;
import java.util.List;

public final class QueryStringSplitter {
    private QueryStringSplitter() {
    }

    public static List<String> split(String query) {
        List<String> terms = new ArrayList<>();
        StringBuilder currentTerm = new StringBuilder();
        boolean insideQuotes = false; // Flag to track if we're inside a quoted phrase

        int i = 0;
        while (i < query.length()) {
            char ch = query.charAt(i);

            if (ch == '"' || ch == '\'') {
                if (insideQuotes) {
                    // We found a closing quote, add the term and reset
                    terms.add(currentTerm.toString());
                    currentTerm.setLength(0); // Reset the current term
                    insideQuotes = false;
                } else {
                    // Opening quote, start a quoted phrase
                    insideQuotes = true;
                }
                i++; // Move past the quote
            } else if (Character.isWhitespace(ch)) {
                if (insideQuotes) {
                    // Inside quotes, we don't split on spaces
                    currentTerm.append(ch); // Keep spaces inside quotes
                } else {
                    // We found a space outside quotes
                    if (!currentTerm.isEmpty()) {
                        terms.add(currentTerm.toString());
                        currentTerm.setLength(0); // Reset the current term
                    }
                }
                i++; // Skip the whitespace
            } else {
                // Accumulate characters for the current term
                currentTerm.append(ch);
                i++;
            }
        }

        // Add the last term if there's any remaining text
        if (!currentTerm.isEmpty()) {
            terms.add(currentTerm.toString());
        }

        return terms;
    }
}
