package guideme.guidebook.layout;

import guideme.guidebook.style.ResolvedTextStyle;

public interface FontMetrics {
    float getAdvance(int codePoint, ResolvedTextStyle style);

    int getLineHeight(ResolvedTextStyle style);
}
