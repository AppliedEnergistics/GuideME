package guideme;

import guideme.internal.GuideRegistry;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Global registry of GuideME guides.
 */
public final class Guides {
    private Guides() {
    }

    public static Collection<? extends Guide> getAll() {
        return GuideRegistry.getAll();
    }

    @Nullable
    public static Guide getById(ResourceLocation id) {
        return GuideRegistry.getById(id);
    }
}
