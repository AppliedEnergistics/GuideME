package guideme.internal.web;

import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.siteexport.DefaultValue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Maps from attributes on a JSX tag to a Java model class.
 */
final class JsxAttributeMapper {
    public static <T extends Record> T map(MdxJsxElementFields fields, Class<T> modelClass) {
        var components = modelClass.getRecordComponents();
        Constructor<T> recordConstructor;
        try {
            recordConstructor = modelClass.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
            recordConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find record constructor matching record components.", e);
        }

        boolean[] attributePresent = new boolean[components.length];
        Object[] ctorParameters = new Object[components.length];
        attributes: for (var attribute : fields.attributes()) {
            if (attribute instanceof MdxJsxAttribute attributeNode) {
                // Find matching record component
                for (int i = 0; i < components.length; i++) {
                    if (components[i].getName().equals(attributeNode.name)) {
                        String value = attributeNode.hasStringValue() ? attributeNode.getStringValue()
                                : attributeNode.getExpressionValue();
                        ctorParameters[i] = convertType(components[i], value, attributeNode.hasExpressionValue());
                        attributePresent[i] = true;
                        continue attributes;
                    }
                }

                throw new RuntimeException("Unknown attribute " + attributeNode.name);
            } else {
                throw new RuntimeException("Unsupported attribute " + attribute);
            }
        }

        // Validate missing required values
        for (int i = 0; i < components.length; i++) {
            var value = ctorParameters[i];
            var component = components[i];
            var defaultValue = component.getAnnotation(DefaultValue.class);
            if (value == null && (component.getType().isPrimitive()
                    || !component.getAnnotatedType().isAnnotationPresent(Nullable.class))) {
                // Default value present?
                if (defaultValue != null) {
                    ctorParameters[i] = convertType(components[i], defaultValue.value(), false);
                } else {
                    throw new RuntimeException("Missing required attribute " + components[i].getName());
                }
            }
        }

        try {
            return recordConstructor.newInstance(ctorParameters);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Couldn't invoke record constructor.", e);
        }
    }

    private static Object convertType(RecordComponent component, String attributeValue, boolean expression) {
        var componentType = component.getType();

        // Only a subset of primitives are supported
        if (int.class.equals(componentType)) {
            try {
                return Integer.parseInt(attributeValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected integer value for attribute " + component.getName());
            }

        } else if (float.class.equals(componentType)) {
            try {
                return Float.parseFloat(attributeValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected float value for attribute " + component.getName());
            }

        } else if (boolean.class.equals(componentType)) {
            return switch (attributeValue) {
                case "true" -> true;
                case "false" -> false;
                default ->
                    throw new IllegalArgumentException("Expected float value for attribute " + component.getName());
            };

        }

        // Any of the following types can only be converted from strings.
        if (expression) {
            throw new RuntimeException("Cannot use an expression value for attribute " + component.getName());
        }

        if (String.class.isAssignableFrom(componentType)) {
            return attributeValue;
        } else if (Vector3f.class.isAssignableFrom(componentType)) {

            var parts = attributeValue.trim().split("\\s+", 3);
            var result = new Vector3f();
            try {
                for (int i = 0; i < parts.length; i++) {
                    float v = Float.parseFloat(parts[i]);
                    result.setComponent(i, v);
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("Malformed 3D vector: '" + attributeValue + "'");
            }

            return result;
        } else {
            throw new IllegalStateException("Unsupported component type: " + componentType);
        }
    }
}
