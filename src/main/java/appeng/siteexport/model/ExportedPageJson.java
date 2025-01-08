package appeng.siteexport.model;

import java.util.HashMap;
import java.util.Map;

import guideme.libs.mdast.model.MdAstRoot;

public class ExportedPageJson {
    public String title;
    public MdAstRoot astRoot;

    public Map<String, Object> frontmatter = new HashMap<>();
}
