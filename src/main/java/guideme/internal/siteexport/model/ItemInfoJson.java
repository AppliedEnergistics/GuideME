package guideme.internal.siteexport.model;

import guideme.siteexport.web.ExportedItemInfo;

public class ItemInfoJson implements ExportedItemInfo {
    public String id;
    public String displayName;
    public String rarity;
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
