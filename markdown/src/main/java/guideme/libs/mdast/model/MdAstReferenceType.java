package guideme.libs.mdast.model;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum MdAstReferenceType {
    /**
     * The reference is implicit, its identifier inferred from its content.
     */
    SHORTCUT("shortcut"),
    /**
     * The reference is explicit, its identifier inferred from its content.
     */
    COLLAPSED("collapsed"),
    /**
     * The reference is explicit, its identifier explicitly set.
     */
    FULL("full");

    private final String serializedName;

    private static final Map<String, MdAstReferenceType> REVERSE_MAPPING = Stream.of(values())
            .collect(Collectors.toMap(e -> e.serializedName, e -> e));

    MdAstReferenceType(String serializedName) {
        this.serializedName = serializedName;
    }

    public static MdAstReferenceType fromSerializedName(String referenceType) {
        var result = REVERSE_MAPPING.get(referenceType);
        if (result == null) {
            throw new IllegalArgumentException("Invalid reference type: " + referenceType);
        }
        return result;
    }

    public String getSerializedName() {
        return serializedName;
    }
}
