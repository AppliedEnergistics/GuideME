package guideme.scene.export;

/**
 * A buffer source we pass into the standard renderer to capture all rendered 3D data in buffers suitable for export.
 *
 * <p>
 * !!! Not ported - THIS CLASS DOES NOT COMPILE AGAINST 26.3 AND WAS INTENTIONALLY NOT PORTED !!!
 *
 * <p>
 * There is no longer any equivalent interception point in Minecraft 26.3:
 * <ul>
 * <li>{@code MultiBufferSource} and {@code MultiBufferSource.BufferSource} were deleted outright. Nothing
 * hands out a {@code VertexConsumer} per {@code RenderType} any more, so the whole
 * "extend BufferSource and override endBatch(RenderType)" strategy is gone, as is the
 * {@code startedBuilders} access transformer it relied on.</li>
 * <li>{@code FeatureRenderDispatcher} no longer takes any buffer source at all. Its constructor is
 * {@code FeatureRenderDispatcher(RenderBuffers, ModelManager, AtlasManager, Font, GameRenderState)} and all
 * vertex data is written into {@code RenderBuffers#stagedVertexBuffer()}
 * ({@code net.minecraft.client.renderer.StagedVertexBuffer}), which uploads straight to GPU buffers during
 * {@code FeatureRenderDispatcher#prepareFrame} and frees the CPU-side staging slices afterwards
 * ({@code StagedVertexBuffer.Draw#freeVertexData}).</li>
 * <li>The mapping from a {@code StagedVertexBuffer.Draw} back to its {@code RenderType} only exists inside
 * the private {@code RenderTypeFeatureRenderer.Group} ({@code draws} / {@code drawRenderTypes}), so even
 * reading the staging buffer would lose the material information that {@link Mesh} needs.</li>
 * </ul>
 *
 * <p>
 * Viable redesigns, in increasing order of fidelity (all of them also require changes to
 * {@code SceneExporter}, which no longer compiles either):
 * <ol>
 * <li><b>Capture only custom geometry.</b> Implement {@code SubmitNodeCollector} directly and capture
 * {@code submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)} by invoking the callback
 * against a local {@code BufferBuilder} per {@code RenderType}. After the port of
 * {@code GuidebookLevelRenderer}, all block and annotation geometry goes through exactly this call, so this
 * captures the bulk of a scene. Block entities and entities (which use {@code submitModel} /
 * {@code submitItem} / ...) would be lost, because only vanilla's {@code FeatureRenderer}s can turn those
 * submit nodes into vertices.</li>
 * <li><b>Record the render pass.</b> Drive a real {@code SubmitNodeStorage} + {@code FeatureRenderDispatcher}
 * with a private {@code RenderBuffers}, then call
 * {@code FeatureRenderDispatcher.renderAllFeatures(renderPass, frame)} with a <em>recording</em>
 * implementation of {@code com.mojang.renderpearl.api.commands.RenderPass} that captures
 * {@code setPipeline} / {@code setVertexBuffer} / {@code setIndexBuffer} / {@code drawIndexed} instead of
 * executing them, and read the referenced GPU buffers back with
 * {@code CommandEncoder#readBuffer}/{@code GpuFence}. This captures everything, but only identifies the
 * material by the pipeline plus the {@code "Render Type <name>"} debug-group label, so a name to
 * {@code RenderType} lookup would have to be maintained.</li>
 * <li><b>Access-transformer the dispatcher internals</b> ({@code FeatureRenderDispatcher.featureRenderers},
 * {@code RenderTypeFeatureRenderer.groups}, {@code RenderTypeFeatureRenderer$Group.draws} and
 * {@code drawRenderTypes}) and walk them after {@code prepareFrame} to pair each
 * {@code StagedVertexBuffer.Draw} (via the public {@code StagedVertexBuffer#getExecuteInfo}) with its
 * {@code PreparedRenderType}, then read the GPU buffers back. Highest fidelity, most brittle.</li>
 * </ol>
 */
final class MeshBuildingBufferSource {
    private MeshBuildingBufferSource() {
    }
}
