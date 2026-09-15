package guideme.internal.util;

import com.mojang.serialization.JavaOps;
import guideme.compiler.ParsedGuidePage;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NavigationUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NavigationUtil.class);

    private NavigationUtil() {
    }

    public record NavIcon(ItemStackTemplate icon, Supplier<ItemStack> iconFactory) {
    }

    @Nullable
    public static NavIcon createNavigationIcon(ParsedGuidePage page) {
        var navigation = page.getFrontmatter().navigationEntry();

        if (navigation != null && navigation.iconItemId() != null) {
            var iconItem = BuiltInRegistries.ITEM.get(navigation.iconItemId()).orElse(null);
            if (iconItem != null) {
                if (navigation.iconComponents() != null) {
                    RegistryAccess registryAccess;
                    try {
                        registryAccess = Platform.getClientRegistryAccess();
                    } catch (NullPointerException ignored) {
                        registryAccess = null;
                    }

                    if (registryAccess != null) {
                        var patch = DataComponentPatch.CODEC
                                .parse(RegistryOps.create(JavaOps.INSTANCE, registryAccess),
                                        navigation.iconComponents())
                                .resultOrPartial(
                                        err -> LOG.error("Failed to deserialize component patch {} for icon {}: {}",
                                                navigation.iconComponents(), navigation.iconItemId(), err));
                        Supplier<ItemStack> iconFactory = () -> new ItemStack(iconItem, 1,
                                patch.orElse(DataComponentPatch.EMPTY));
                        return new NavIcon(new ItemStackTemplate(iconItem, patch.orElse(DataComponentPatch.EMPTY)),
                                iconFactory);
                    } else {
                        // Try to deserialize the icon component without registry access
                        Supplier<ItemStack> iconFactory = () -> {
                            var patch = DataComponentPatch.CODEC
                                    .parse(RegistryOps.create(JavaOps.INSTANCE, Platform.getClientRegistryAccess()),
                                            navigation.iconComponents())
                                    .resultOrPartial(
                                            err -> LOG.error("Failed to deserialize component patch {} for icon {}: {}",
                                                    navigation.iconComponents(), navigation.iconItemId(), err));
                            return new ItemStack(iconItem, 1, patch.orElse(DataComponentPatch.EMPTY));
                        };
                        var patch = DataComponentPatch.CODEC.parse(JavaOps.INSTANCE, navigation.iconComponents())
                                .resultOrPartial();
                        var icon = new ItemStackTemplate(iconItem, 1, patch.orElse(DataComponentPatch.EMPTY));
                        return new NavIcon(icon, iconFactory);
                    }
                } else {
                    return new NavIcon(new ItemStackTemplate(iconItem), () -> new ItemStack(iconItem));
                }
            } else {
                LOG.error("Couldn't find icon {} for icon of page {}", navigation.iconItemId(), page);
            }
        }

        return null;
    }
}
