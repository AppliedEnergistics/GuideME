package guideme.internal.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QueryStringSplitterTest {

    @ParameterizedTest
    @CsvSource({
            "'apple \"orange juice\" banana \"grape fruit\"', 'apple|orange juice|banana|grape fruit'",
            "'apple orange banana', 'apple|orange|banana'",
            "'\"quoted term\"', 'quoted term'",
            "'''quoted term''', 'quoted term'",
            "'term with spaces', 'term|with|spaces'",
            "'  apple  banana  ', 'apple|banana'",
            "'\"apple banana\" orange', 'apple banana|orange'"
    })
    public void testParseQuery(String query, String expected) {
        List<String> parsedTerms = QueryStringSplitter.split(query);

        String[] expectedTermsArray = expected.split("\\|");
        List<String> expectedTerms = List.of(expectedTermsArray);

        assertEquals(expectedTerms, parsedTerms);
    }

    @Test
    public void testEmptyQuery() {
        List<String> parsedTerms = QueryStringSplitter.split("");
        assertTrue(parsedTerms.isEmpty(), "Empty query should return an empty list.");
    }

    @Test
    public void testSingleTerm() {
        List<String> parsedTerms = QueryStringSplitter.split("apple");
        assertEquals(List.of("apple"), parsedTerms, "The query should contain exactly one term.");
    }

    @Test
    public void testMultipleSpacesBetweenTerms() {
        List<String> parsedTerms = QueryStringSplitter.split("apple   orange    banana");
        assertEquals(List.of("apple", "orange", "banana"), parsedTerms, "Extra spaces should be ignored.");
    }

    @Test
    public void testMultipleSpacesAroundQuotedTerm() {
        List<String> parsedTerms = QueryStringSplitter.split("   \"apple pie\"   banana   ");
        assertEquals(List.of("apple pie", "banana"), parsedTerms,
                "Spaces around quoted terms should not affect the output.");
    }

}
