package guideme.internal.web;

import guideme.siteexport.DefaultValue;
import org.jspecify.annotations.Nullable;

public record GameSceneAttributes(
        @Nullable String background,

        @DefaultValue("false") boolean interactive,

        // These are added during export
        String src,
        String placeholder,
        int width,
        int height,

        @DefaultValue("5") int padding,

        @DefaultValue("1.5") float zoom) {
}
