package guideme.internal.search;

import java.util.Objects;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public class LanguageSpecificAnalyzerWrapper extends DelegatingAnalyzerWrapper {
    private final Analyzer defaultAnalyzer = new StandardAnalyzer();

    public LanguageSpecificAnalyzerWrapper() {
        super(PER_FIELD_REUSE_STRATEGY);
    }

    @Override
    protected Analyzer getWrappedAnalyzer(String fieldName) {
        if (fieldName == null) {
            return defaultAnalyzer;
        }
        for (String language : Analyzers.LANGUAGES) {
            if (fieldName.endsWith("_" + language)) {
                return Objects.requireNonNull(Analyzers.ANALYZERS.get(language), "analyzer for " + language).get();
            }
        }
        return defaultAnalyzer;
    }
}
