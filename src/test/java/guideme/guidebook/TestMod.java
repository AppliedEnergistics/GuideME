package guideme.guidebook;

import guideme.internal.GuideME;
import guideme.internal.GuideMEClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = GuideME.MOD_ID)
public class TestMod {
    public TestMod(ModContainer modContainer, IEventBus modBus) {
        new GuideMEClient(modContainer, modBus);
    }
}
