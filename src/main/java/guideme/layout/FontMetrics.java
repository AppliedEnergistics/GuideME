package guideme.layout;

import guideme.style.ResolvedTextStyle;

public interface FontMetrics {
    float getAdvance(int codePoint, ResolvedTextStyle style);

    int getLineHeight(ResolvedTextStyle style);
}
