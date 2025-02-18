package guideme.internal.search;

import java.util.Collection;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;

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
    public static Query parse(String queryString, Analyzer analyzer, Collection<String> searchLanguages) {
        var tokens = QueryStringSplitter.split(queryString);

        var builder = new BooleanQuery.Builder();

        var queryBuilder = new QueryBuilder(analyzer);

        for (String searchLanguage : searchLanguages) {
            var textField = IndexSchema.getTextField(searchLanguage);
            var titleField = IndexSchema.getTitleField(searchLanguage);

            // Exact occurrences in the title are scored with 20% boost
            builder.add(
                    new BoostQuery(buildFieldQuery(queryBuilder, titleField, tokens, false, BooleanClause.Occur.SHOULD),
                            1.2f),
                    BooleanClause.Occur.SHOULD);
            // Exact occurrences in the body are scored normally
            builder.add(buildFieldQuery(queryBuilder, textField, tokens, false, BooleanClause.Occur.SHOULD),
                    BooleanClause.Occur.SHOULD);
            // Occurrences in the title, where the last token is expanded to a wildcard are scored at 40%
            builder.add(
                    new BoostQuery(buildFieldQuery(queryBuilder, titleField, tokens, true, BooleanClause.Occur.SHOULD),
                            0.4f),
                    BooleanClause.Occur.SHOULD);
            // Occurrences in the body, where the last token is expanded to a wildcard are scored at 20%
            builder.add(
                    new BoostQuery(buildFieldQuery(queryBuilder, textField, tokens, true, BooleanClause.Occur.SHOULD),
                            0.2f),
                    BooleanClause.Occur.SHOULD);
        }

        return builder.build();
    }

    private static BooleanQuery buildFieldQuery(QueryBuilder queryBuilder,
            String fieldName,
            List<String> tokens,
            boolean makeLastTokenWildcard,
            BooleanClause.Occur clause) {

        // Prepare a BooleanQuery to combine terms with OR
        var booleanQueryBuilder = new BooleanQuery.Builder();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (token.contains(" ")) {
                // Phrase query
                booleanQueryBuilder.add(queryBuilder.createPhraseQuery(fieldName, token), clause);
                continue;
            }

            // Make the last token a wildcard
            if (makeLastTokenWildcard && i == tokens.size() - 1 && !token.endsWith("*")) {
                token += "*";
            }

            Query q;
            if (token.contains("*")) {
                BytesRef normalizedTerm = queryBuilder.getAnalyzer().normalize(fieldName, token);
                q = new WildcardQuery(new Term(fieldName, normalizedTerm));
            } else {
                q = queryBuilder.createBooleanQuery(fieldName, token);
            }

            booleanQueryBuilder.add(q, clause);
        }

        // Return the constructed BooleanQuery
        return booleanQueryBuilder.build();
    }

}
