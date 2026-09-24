package net.fabricmc.loader.impl.discovery;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual Fabric SAT resolver, not a hand-written version-range approximation. */
class BundleResolutionTest {
    private final VersionOverrides versions = new VersionOverrides();
    private final DependencyOverrides dependencies = new DependencyOverrides(Path.of("build/nonexistent-config"));

    private LoaderModMetadata metadata(InputStream stream, String name) throws Exception {
        return ModMetadataParser.parseMetadata(stream, name, List.of(), versions, dependencies, false);
    }

    private ModCandidateImpl builtin(String name, String version) throws Exception {
        String json = "{\"schemaVersion\":1,\"id\":\"" + name + "\",\"version\":\"" + version + "\"}";
        var meta = metadata(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), name);
        return ModCandidateImpl.createPlain(List.of(Path.of(name)), meta, false, List.of());
    }

    private List<ModCandidateImpl> resolve(String minecraft, String java, String api) throws Exception {
        Path bundle = Path.of(System.getProperty("bundle.file"));
        List<ModCandidateImpl> nested = new ArrayList<>();
        List<ModCandidateImpl> candidates = new ArrayList<>();
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            var rootMeta = metadata(zip.getInputStream(zip.getEntry("fabric.mod.json")), bundle.toString());
            for (var entry : zip.stream().filter(e -> e.getName().endsWith(".jar")).toList()) {
                try (ZipInputStream child = new ZipInputStream(zip.getInputStream(entry))) {
                    java.util.zip.ZipEntry resource;
                    while ((resource = child.getNextEntry()) != null) {
                        if (!resource.getName().equals("fabric.mod.json")) continue;
                        var meta = metadata(new ByteArrayInputStream(child.readAllBytes()), entry.getName());
                        nested.add(ModCandidateImpl.createNested(entry.getName(), 0, meta, false, List.of()));
                        break;
                    }
                }
            }
            var root = ModCandidateImpl.createPlain(List.of(bundle), rootMeta, false, nested);
            for (var child : nested) child.addParent(root);
            candidates.add(root);
            candidates.addAll(nested);
        }
        assertEquals(3, nested.size());
        candidates.add(builtin("minecraft", minecraft));
        candidates.add(builtin("java", java));
        candidates.add(builtin("fabricloader", "0.19.5"));
        candidates.add(builtin("fabric-api", api));
        return ModResolver.resolve(candidates, EnvType.CLIENT, new HashMap<>());
    }

    private void assertAdapter(String mc, String java, String api) throws Exception {
        var mods = resolve(mc, java, api);
        var selected = mods.stream().filter(m -> m.getId().equals("lavavisual")).toList();
        assertEquals(1, selected.size(), "Must select exactly one adapter");
        assertTrue(selected.getFirst().getVersion().getFriendlyString().endsWith("mc" + mc));
    }

    @Test void selects1204OnJava17() throws Exception { assertAdapter("1.20.4", "17", "0.97.2+1.20.4"); }
    @Test void selects1214OnJava21IgnoringJava25Adapter() throws Exception { assertAdapter("1.21.4", "21", "0.119.4+1.21.4"); }
    @Test void selects262OnJava25() throws Exception { assertAdapter("26.2", "25", "0.161.0+26.2"); }
    @Test void rejectsUnsupportedIntermediateVersion() { assertThrows(ModResolutionException.class, () -> resolve("1.21.5", "21", "0.119.4")); }
    @Test void rejectsUnverifiedFutureVersion() { assertThrows(ModResolutionException.class, () -> resolve("26.3", "25", "0.161.0")); }
    @Test void rejectsOldJavaFor262() { assertThrows(ModResolutionException.class, () -> resolve("26.2", "21", "0.161.0+26.2")); }
}
