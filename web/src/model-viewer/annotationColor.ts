import { Color } from "three";

/**
 * Annotation colors use the format of guide markup, which is #AARRGGBB like in-game. three.js only understands
 * #RRGGBB, so the alpha is dropped. Annotations don't use the alpha of their color.
 */
export function parseAnnotationColor(color: string): Color {
  if (/^#[0-9a-fA-F]{8}$/.test(color)) {
    return new Color("#" + color.substring(3));
  }
  return new Color(color);
}
