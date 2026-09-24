package tech.gulp.lavavisual.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.OptionalDouble;

/** Dedicated layers own and restore GPU state; never mutate vanilla's shared layers. */
public abstract class LavaRenderLayers extends RenderLayer {
    private LavaRenderLayers(String name, VertexFormat format, VertexFormat.DrawMode mode,
                             int size, boolean crumbling, boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }

    public static final RenderLayer LINES = of("lavavisual_lines", VertexFormats.LINES,
            VertexFormat.DrawMode.LINES, 4096, false, false,
            MultiPhaseParameters.builder()
                    .program(LINES_PROGRAM)
                    .lineWidth(new LineWidth(OptionalDouble.of(2.0)))
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .depthTest(ALWAYS_DEPTH_TEST)
                    .writeMaskState(COLOR_MASK)
                    .cull(DISABLE_CULLING)
                    .build(false));

    public static final RenderLayer CHAMS = of("lavavisual_chams", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 65536, false, true,
            MultiPhaseParameters.builder()
                    .program(ENTITY_TRANSLUCENT_PROGRAM)
                    .texture(new Texture(Identifier.of("lavavisual", "textures/white.png"), false, false))
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .depthTest(ALWAYS_DEPTH_TEST)
                    .writeMaskState(COLOR_MASK)
                    .cull(DISABLE_CULLING)
                    .lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .build(false));
}
