package tech.gulp.lavavisual.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class LavaRenderLayers {
    private LavaRenderLayers() { }
    public static final RenderType LINES = RenderType.create("lavavisual_lines", RenderSetup.builder(
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("lavavisual", "pipeline/lines"))
                    .withDepthStencilState(Optional.empty())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false).build()))
            .createRenderSetup());
    public static final RenderType CHAMS = RenderType.create("lavavisual_chams", RenderSetup.builder(
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("lavavisual", "pipeline/chams"))
                    .withDepthStencilState(Optional.empty())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false).build()))
            .withTexture("Sampler0", Identifier.fromNamespaceAndPath("lavavisual", "textures/white.png"))
            .useOverlay().useLightmap().sortOnUpload().createRenderSetup());
    public static void initialize() { }
}
