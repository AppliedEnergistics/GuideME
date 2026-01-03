package guideme.internal.siteexport.model;

import org.jspecify.annotations.Nullable;

public record IndexModel(
        int format,
        long generated,
        @Nullable String gameMajorVersion,
        String gameVersion,
        String gameVersionName,
        @Nullable Boolean gameVersionStable,
        String modVersion,
        String guideMeVersion,
        String guideDataPath) {
}
