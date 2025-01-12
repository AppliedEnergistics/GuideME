package guideme.guidebook.layout;

import guideme.api.style.ResolvedTextStyle;

public interface FontMetrics {
    float getAdvance(int codePoint, ResolvedTextStyle style);

    int getLineHeight(ResolvedTextStyle style);
}
