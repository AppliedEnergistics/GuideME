package guideme.guidebook;

import cpw.mods.modlauncher.Launcher;
import guideme.GuideME;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;

@Mod(value = GuideME.MOD_ID)
public class TestMod {
    public TestMod(ModContainer modContainer, IEventBus modBus) {
        var cleaner = (RuntimeDistCleaner) Launcher.INSTANCE.environment().findLaunchPlugin("runtimedistcleaner").get();
        cleaner.setDistribution(Dist.CLIENT);

        new GuideME(modContainer, modBus);
    }
}
