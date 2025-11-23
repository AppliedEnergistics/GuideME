package guideme;

import guideme.internal.GuideME;
import guideme.internal.GuideRegistry;
import java.util.Collection;
import net.minecraft.resources.Identifier;
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
    public static Guide getById(Identifier id) {
        return GuideRegistry.getById(id);
    }

    /**
     * Create a generic guide item that will open the given guide.
     */
    public static ItemStack createGuideItem(Identifier guideId) {
        var stack = new ItemStack(GuideME.GUIDE_ITEM.get());
        stack.set(GuideME.GUIDE_ID_COMPONENT, guideId);
        return stack;
    }
}
