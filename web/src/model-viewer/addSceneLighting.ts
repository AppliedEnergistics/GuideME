import { AmbientLight, DirectionalLight, Object3D, Vector3 } from "three";

// See Minecraft "Lighting" class
const DIFFUSE_LIGHT_0 = new Vector3(0.2, 1.0, -0.7).normalize();
const DIFFUSE_LIGHT_1 = new Vector3(-0.2, 1.0, 0.7).normalize();
const MINECRAFT_LIGHT_POWER = 0.6;
const MINECRAFT_AMBIENT_LIGHT = 0.4;

/**
 * Note that this lighting is only used for geometry that would otherwise not be pre-baked into
 * the chunk, since that geometry has diffuse directional lighting pre-baked into the vertex colors.
 */
export default function addLevelLighting(parent: Object3D) {
  // Minecraft uses some odd lighting for dynamically lit objects (such as entities, block entity renderers, etc.)
  // For details see the individual render type shaders and minecraft_mix_light

  const dirLight1 = new DirectionalLight(0xffffff, MINECRAFT_LIGHT_POWER);
  dirLight1.position.copy(DIFFUSE_LIGHT_0);
  parent.add(dirLight1);
  const dirLight2 = new DirectionalLight(0xffffff, MINECRAFT_LIGHT_POWER);
  dirLight2.position.copy(DIFFUSE_LIGHT_1);
  parent.add(dirLight2);

  // For debugging the light directions
  // parent.add(new DirectionalLightHelper(dirLight1, 1, "blue"));
  // parent.add(new DirectionalLightHelper(dirLight2, 1, "red"));

  const ambientLight = new AmbientLight(0xffffff, MINECRAFT_AMBIENT_LIGHT);
  parent.add(ambientLight);
}
