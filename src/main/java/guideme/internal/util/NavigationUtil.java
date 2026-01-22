package guideme.internal.util;

import com.mojang.serialization.JavaOps;
import guideme.compiler.ParsedGuidePage;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NavigationUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NavigationUtil.class);

    private NavigationUtil() {
    }

    public static @Nullable ItemStackTemplate createNavigationIcon(ParsedGuidePage page) {
        var navigation = page.getFrontmatter().navigationEntry();

        ItemStackTemplate icon = null;
        if (navigation != null && navigation.iconItemId() != null) {
            var iconItem = BuiltInRegistries.ITEM.get(navigation.iconItemId()).orElse(null);
            if (iconItem != null) {
                if (navigation.iconComponents() != null) {
                    var patch = DataComponentPatch.CODEC.parse(JavaOps.INSTANCE, navigation.iconComponents())
                            .resultOrPartial(
                                    err -> LOG.error("Failed to deserialize component patch {} for icon {}: {}",
                                            navigation.iconComponents(), navigation.iconItemId(), err));
                    icon = new ItemStackTemplate(iconItem, 1, patch.orElse(DataComponentPatch.EMPTY));
                } else {
                    icon = new ItemStackTemplate(iconItem);
                }
            }

            if (icon == null) {
                LOG.error("Couldn't find icon {} for icon of page {}", navigation.iconItemId(), page);
            }
        }

        return icon;
    }
}
