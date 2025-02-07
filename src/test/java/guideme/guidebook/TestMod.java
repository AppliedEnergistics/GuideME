package guideme.guidebook;

import cpw.mods.modlauncher.Launcher;
import guideme.internal.GuideME;
import guideme.internal.GuideMEClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.RuntimeDistCleaner;

@Mod(value = GuideME.MOD_ID)
public class TestMod {
    public TestMod(ModLoadingContext context, IEventBus modBus) {
        var cleaner = (RuntimeDistCleaner) Launcher.INSTANCE.environment().findLaunchPlugin("runtimedistcleaner").get();
        cleaner.getExtension().accept(Dist.CLIENT);

        new GuideMEClient(context, modBus);
    }
}
