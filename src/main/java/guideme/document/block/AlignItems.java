package guideme.document.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum AlignItems implements StringRepresentable {
    CENTER,
    START,
    END;

    private final String serializedName;

    AlignItems() {
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
