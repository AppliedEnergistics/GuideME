package guideme.internal.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public class LanguageSpecificAnalyzerWrapper extends DelegatingAnalyzerWrapper {
    private final Analyzer defaultAnalyzer = new StandardAnalyzer();
    private final Analyzer englishAnalyzer = new EnglishAnalyzer();

    public LanguageSpecificAnalyzerWrapper() {
        super(PER_FIELD_REUSE_STRATEGY);
    }

    @Override
    protected Analyzer getWrappedAnalyzer(String fieldName) {
        if (fieldName == null) {
            return defaultAnalyzer;
        }
        if (fieldName.endsWith("_en")) {
            return englishAnalyzer;
        }
        return defaultAnalyzer;
    }
}
