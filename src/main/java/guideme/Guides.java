package guideme;

import guideme.internal.GuideMECommon;
import guideme.internal.GuideRegistry;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Create a generic guide item that will open the given guide.
     */
    public static ItemStack createGuideItem(ResourceLocation guideId) {
        var stack = new ItemStack(GuideMECommon.GUIDE_ITEM.get());
        stack.set(GuideMECommon.GUIDE_ID_COMPONENT, guideId);
        return stack;
    }
}
