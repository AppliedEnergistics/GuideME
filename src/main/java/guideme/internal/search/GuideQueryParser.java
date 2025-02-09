package guideme.internal.search;

import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

public class GuideQueryParser {
    private GuideQueryParser() {
    }

    /**
     * This method will create a query of the following form:
     * <ul>
     * <li>A query that matches every document where a field contains all terms.</li>
     * <li>OR A query that matches every document where a field contains any of the terms, but boosted to 0.1.</li>
     * </ul>
     */
    public static Query parse(String queryString) {
        var tokens = QueryStringSplitter.split(queryString);

        var textField = IndexSchema.getTextField("en");
        var titleField = IndexSchema.getTitleField("en");

        var builder = new BooleanQuery.Builder();

        // Exact occurrences in the title are scored with 20% boost
        builder.add(new BoostQuery(buildFieldQuery(titleField, tokens, false, BooleanClause.Occur.SHOULD), 1.2f),
                BooleanClause.Occur.SHOULD);
        // Exact occurrences in the body are scored normally
        builder.add(buildFieldQuery(textField, tokens, false, BooleanClause.Occur.SHOULD), BooleanClause.Occur.SHOULD);
        // Occurrences in the title, where the last token is expanded to a wildcard are scored at 40%
        builder.add(new BoostQuery(buildFieldQuery(titleField, tokens, true, BooleanClause.Occur.SHOULD), 0.4f),
                BooleanClause.Occur.SHOULD);
        // Occurrences in the body, where the last token is expanded to a wildcard are scored at 20%
        builder.add(new BoostQuery(buildFieldQuery(textField, tokens, true, BooleanClause.Occur.SHOULD), 0.2f),
                BooleanClause.Occur.SHOULD);

        return builder.build();
    }

    private static BooleanQuery buildFieldQuery(String fieldName, List<String> tokens, boolean makeLastTokenWildcard,
            BooleanClause.Occur clause) {
        // Prepare a BooleanQuery to combine terms with OR
        var booleanQueryBuilder = new BooleanQuery.Builder();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (token.contains(" ")) {
                // Phrase query
                var splitToken = QueryStringSplitter.split(token);
                booleanQueryBuilder.add(new PhraseQuery(fieldName, splitToken.toArray(String[]::new)), clause);
                continue;
            }

            // Make the last token a wildcard
            if (makeLastTokenWildcard && i == tokens.size() - 1 && !token.endsWith("*")) {
                token += "*";
            }

            Term term = new Term(fieldName, token);

            Query q;
            if (token.contains("*")) {
                q = new WildcardQuery(term);
            } else {
                q = new TermQuery(term);
            }

            booleanQueryBuilder.add(q, clause);
        }

        // Return the constructed BooleanQuery
        return booleanQueryBuilder.build();
    }

}
