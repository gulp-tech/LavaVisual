package tech.gulp.lavavisual.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a Bedrock skin pack (.mcpack) with the LavaVisual geometry, with no Android classes so it can be unit tested. */
public final class PackWriter {
    private PackWriter() { }

    /** A stable uuid, so importing the pack again replaces the old one instead of piling up copies. */
    public static String uuid(String seed) {
        return UUID.nameUUIDFromBytes(("lavavisual:" + seed).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String manifest(String packName, String seed) {
        return "{\n  \"format_version\": 1,\n  \"header\": {\n    \"name\": \"" + packName + "\",\n"
                + "    \"uuid\": \"" + uuid(seed + ":header") + "\",\n    \"version\": [1, 0, 0]\n  },\n"
                + "  \"modules\": [\n    {\n      \"type\": \"skin_pack\",\n"
                + "      \"uuid\": \"" + uuid(seed + ":module") + "\",\n      \"version\": [1, 0, 0]\n    }\n  ]\n}\n";
    }

    public static String skins(String geometryId, boolean slim) {
        return "{\n  \"serialize_name\": \"LavaVisual\",\n  \"localization_name\": \"LavaVisual\",\n  \"skins\": [\n    {\n"
                + "      \"localization_name\": \"lv\",\n      \"geometry\": \"" + geometryId + "\",\n"
                + "      \"texture\": \"skin.png\",\n      \"type\": \"free\"\n    }\n  ]\n}\n"
                + (slim ? "" : "");
    }

    public static String lang(String skinName) {
        return "skinpack.LavaVisual=LavaVisual\nskin.LavaVisual.lv=" + skinName + "\n";
    }

    /** The whole .mcpack as bytes. */
    public static byte[] build(String packName, String skinName, byte[] skinPng, String geometryJson, String geometryId, boolean slim, String seed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "manifest.json", manifest(packName, seed).getBytes(StandardCharsets.UTF_8));
            put(zip, "skins.json", skins(geometryId, slim).getBytes(StandardCharsets.UTF_8));
            put(zip, "geometry.json", geometryJson.getBytes(StandardCharsets.UTF_8));
            put(zip, "skin.png", skinPng);
            byte[] text = lang(skinName).getBytes(StandardCharsets.UTF_8);
            put(zip, "texts/en_US.lang", text);
            put(zip, "texts/ru_RU.lang", text);
            put(zip, "texts/languages.json", "[\"en_US\", \"ru_RU\"]".getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }
}
