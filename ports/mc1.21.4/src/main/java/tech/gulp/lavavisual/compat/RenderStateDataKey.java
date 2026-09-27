package tech.gulp.lavavisual.compat;

import java.util.function.Supplier;

/** Key for one piece of per-frame data (the same shape the newer versions offer on the frame state). */
public final class RenderStateDataKey<T> {
    private final Supplier<String> name;
    private RenderStateDataKey(Supplier<String> name) { this.name = name; }
    public static <T> RenderStateDataKey<T> create(Supplier<String> name) { return new RenderStateDataKey<>(name); }
    @Override public String toString() { return name.get(); }
}
