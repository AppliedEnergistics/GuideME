package guideme.internal.siteexport.model;

import guideme.siteexport.ExportedFluidInfo;

public class FluidInfoJson implements ExportedFluidInfo {
    public String id;
    public String displayName;
    public String icon;

    @Override
    public String icon() {
        return icon;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
