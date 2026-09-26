package tech.gulp.lavavisual.audio;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import tech.gulp.lavavisual.LavaVisual;

/**
 * Album cover of the current track: the picture embedded in the file, or an image next to it (same name .png / .jpg,
 * cover.png / cover.jpg / folder.jpg). Decoded with STB (PNG and JPEG), scaled to 64x64 once per track. No cover ->
 * null, and the HUD draws a spinning disc instead.
 */
public final class Covers {
    public static final int SIZE = 64;
    private static final Identifier ID = Identifier.fromNamespaceAndPath("lavavisual", "music_cover");
    private static Path loaded;
    private static boolean present;
    private static DynamicTexture texture;
    // Result of the background decode, handed over to the game thread (which uploads it).
    private static NativeImage ready;
    private static Path readyFor;
    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "LavaVisual covers");
        thread.setDaemon(true);
        return thread;
    });
    private Covers() { }

    /** Cover of the track, or null while there is none (yet): the file is read and decoded on a background thread,
     *  only the small 64 x 64 upload happens here. */
    public static Identifier get(Minecraft mc, MusicPlayer.Track track) {
        if (track == null) return null;
        if (!track.file().equals(loaded)) {
            loaded = track.file();
            present = false;
            Path file = loaded;
            WORKER.execute(() -> {
                NativeImage image = null;
                try {
                    byte[] bytes = find(file);
                    image = bytes == null ? null : decode(bytes);
                } catch (RuntimeException | LinkageError error) {
                    LavaVisual.LOGGER.warn("LavaVisual: cannot read the cover of {}", file.getFileName(), error);
                }
                synchronized (Covers.class) {
                    if (ready != null) ready.close(); // an older result nobody picked up
                    ready = image;
                    readyFor = file;
                }
            });
        }
        NativeImage image;
        Path file;
        synchronized (Covers.class) {
            image = ready; file = readyFor;
            ready = null; readyFor = null;
        }
        if (file != null) {
            if (image != null && file.equals(loaded)) {
                try {
                    // Registering under the same id replaces (and closes) the previous cover.
                    texture = new DynamicTexture(() -> "lavavisual music cover", image);
                    mc.getTextureManager().register(ID, texture);
                    texture.upload();
                    present = true;
                } catch (RuntimeException | LinkageError error) {
                    LavaVisual.LOGGER.warn("LavaVisual: cannot show the cover of {}", file.getFileName(), error);
                }
            } else if (image != null) image.close();
        }
        return present ? ID : null;
    }
    private static byte[] find(Path file) {
        AudioInfo info = AudioInfo.read(file, 8 << 20, true);
        if (info.cover != null) return info.cover;
        String name = file.getFileName().toString();
        String base = name.substring(0, name.length() - 4);
        Path dir = file.getParent();
        for (String candidate : new String[]{base + ".png", base + ".jpg", base + ".jpeg", "cover.png", "cover.jpg", "folder.jpg", "folder.png"}) {
            Path p = dir.resolve(candidate);
            try { if (Files.isRegularFile(p) && Files.size(p) < (16 << 20)) return Files.readAllBytes(p); } catch (java.io.IOException ignored) { }
        }
        return null;
    }
    /** Any STB-readable image -> centre-cropped, box-filtered 64x64 ARGB. */
    private static NativeImage decode(byte[] bytes) {
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), comp = stack.mallocInt(1);
            ByteBuffer rgba = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
            if (rgba == null) return null;
            try {
                int width = w.get(0), height = h.get(0), side = Math.min(width, height);
                if (side <= 0) return null;
                int ox = (width - side) / 2, oy = (height - side) / 2;
                NativeImage out = new NativeImage(SIZE, SIZE, false);
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                    int x0 = ox + x * side / SIZE, x1 = Math.max(x0 + 1, ox + (x + 1) * side / SIZE);
                    int y0 = oy + y * side / SIZE, y1 = Math.max(y0 + 1, oy + (y + 1) * side / SIZE);
                    long r = 0, g = 0, b = 0, a = 0, n = 0;
                    for (int yy = y0; yy < y1; yy += Math.max(1, (y1 - y0) / 4)) for (int xx = x0; xx < x1; xx += Math.max(1, (x1 - x0) / 4)) {
                        int i = (yy * width + xx) * 4;
                        r += rgba.get(i) & 255; g += rgba.get(i + 1) & 255; b += rgba.get(i + 2) & 255; a += rgba.get(i + 3) & 255; n++;
                    }
                    out.setPixel(x, y, (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
                }
                return out;
            } finally {
                STBImage.stbi_image_free(rgba);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
