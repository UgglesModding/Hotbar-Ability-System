package com.abilities.abilitiesplugin;

import com.google.gson.*;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Single-source pack loader:
 * - applies Hotbar's own pack first
 * - loads packs from every plugin's dataDir/hca/*.json
 * - loads packs from every plugin jar resources (HCA/*.json)
 * - optionally loads packs from mods folder jar files (HCA/*.json)
 *
 * Packs are applied directly to WeaponRegistry (no queue).
 */
public final class ModPackScanner {

    private static final Gson GSON = new GsonBuilder().create();

    private ModPackScanner() {}

    // -----------------------------
    // Resource access abstraction
    // -----------------------------
    public interface ResourceAccess {
        InputStream open(String resourcePath) throws IOException;
    }

    /** Reads resources from a ClassLoader (for Hotbar's built-in pack & jar-based scanning). */
    public static final class ClassLoaderAccess implements ResourceAccess {
        private final ClassLoader loader;
        public ClassLoaderAccess(ClassLoader loader) { this.loader = loader; }

        @Override
        public InputStream open(String resourcePath) {
            if (loader == null) return null;
            return loader.getResourceAsStream(normalizePath(resourcePath));
        }
    }

    /** Reads files from disk relative to a root folder. */
    public static final class FileSystemAccess implements ResourceAccess {
        private final Path root;
        public FileSystemAccess(Path root) { this.root = root; }

        @Override
        public InputStream open(String resourcePath) throws IOException {
            if (root == null) return null;
            String rel = normalizePath(resourcePath);
            Path p = root.resolve(rel).normalize();
            if (!Files.exists(p) || Files.isDirectory(p)) return null;
            return Files.newInputStream(p);
        }
    }

    /** Reads entries from a jar/zip. */
    public static final class ZipResourceAccess implements ResourceAccess {
        private final Path jarPath;
        public ZipResourceAccess(Path jarPath) { this.jarPath = jarPath; }

        @Override
        public InputStream open(String resourcePath) throws IOException {
            String p = normalizePath(resourcePath);
            ZipFile zip = new ZipFile(jarPath.toFile());
            ZipEntry e = zip.getEntry(p);
            if (e == null) {
                zip.close();
                return null;
            }

            InputStream raw = zip.getInputStream(e);
            return new FilterInputStream(raw) {
                @Override public void close() throws IOException {
                    super.close();
                    zip.close();
                }
            };
        }
    }

    // -----------------------------
    // Public entry point
    // -----------------------------
    public static void loadAllPacks(
            WeaponRegistry weaponRegistry,
            ClassLoader hotbarLoader
    ) {
        if (weaponRegistry == null) return;
        if (hotbarLoader == null) return;

        int applied = 0;

        // ✅ REQUIRED: base HCA pack must be applied first
        applied += applyHotbarPack(weaponRegistry, hotbarLoader);

        applied += applyPluginDataDirPacks(weaponRegistry);

        applied += applyPluginJarResourcePacks(weaponRegistry);

        applied += applyModsFolderJarPacks(weaponRegistry);

        System.out.println("[HCA] ModPackScanner: total packs applied=" + applied);
    }

    // -----------------------------
    // Step 1: Hotbar built-in pack
    // -----------------------------
    private static int applyHotbarPack(WeaponRegistry weaponRegistry, ClassLoader hotbarLoader) {
        String path = "HCA/HCA_pack.json";

        // Normal: load from resources inside jar
        try (InputStream in = hotbarLoader.getResourceAsStream(path)) {

            if (in == null) {
                // ✅ Dev fallback: allow folder-based testing if resources aren't packed
                Path p1 = Paths.get("src/main/resources/HCA/HCA_pack.json");
                Path p2 = Paths.get("main/resources/HCA/HCA_pack.json");
                Path pick = Files.exists(p1) ? p1 : (Files.exists(p2) ? p2 : null);

                if (pick == null) {
                    System.out.println("[HCA] Missing Hotbar pack resource: " + path);
                    return 0;
                }

                try {
                    byte[] bytes = Files.readAllBytes(pick);

                    // root should be folder containing "HCA/"
                    // pick = .../resources/HCA/HCA_pack.json
                    // root = .../resources
                    Path root = pick.getParent().getParent();

                    boolean ok = applyPackBytes(
                            weaponRegistry,
                            new FileSystemAccess(root),
                            bytes,
                            "HotbarAbilities:devFolder"
                    );

                    System.out.println("[HCA] Hotbar pack (dev folder) applied=" + ok + " from=" + pick);
                    return ok ? 1 : 0;

                } catch (Throwable t2) {
                    System.out.println("[HCA] Failed reading Hotbar pack from dev folder: " + t2.getMessage());
                    return 0;
                }
            }

            byte[] bytes = in.readAllBytes();
            boolean ok = applyPackBytes(weaponRegistry, new ClassLoaderAccess(hotbarLoader), bytes, "HotbarAbilities");
            System.out.println("[HCA] Hotbar pack applied=" + ok);
            return ok ? 1 : 0;

        } catch (Throwable t) {
            System.out.println("[HCA] Failed reading Hotbar pack: " + t.getMessage());
            return 0;
        }
    }

    // -----------------------------
    // Step 2: plugin data dirs
    // -----------------------------
    private static int applyPluginDataDirPacks(WeaponRegistry weaponRegistry) {
        int applied = 0;

        for (var plugin : PluginManager.get().getPlugins()) {
            try {
                Path dataDir = plugin.getDataDirectory();
                if (dataDir == null) continue;

                Path hcaDir = dataDir.resolve("hca");
                if (!Files.isDirectory(hcaDir)) continue;

                List<Path> jsonFiles = new ArrayList<>();
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(hcaDir, "*.json")) {
                    for (Path p : ds) jsonFiles.add(p);
                }

                jsonFiles.sort(Comparator.comparing(
                        p -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                ));

                for (Path packPath : jsonFiles) {
                    byte[] bytes = Files.readAllBytes(packPath);

                    // Root = the plugin's HCA dir
                    ResourceAccess access = new FileSystemAccess(hcaDir);

                    String tag = plugin.getName() + ":dataDir:" + packPath.getFileName();
                    boolean ok = applyPackBytes(weaponRegistry, access, bytes, tag);

                    if (ok) applied++;

                    System.out.println("[HCA] dataDir pack applied=" + ok + " tag=" + tag);
                }
            } catch (Throwable t) {
                System.out.println("[HCA] dataDir scan error for " + plugin + ": " + t.getMessage());
            }
        }

        return applied;
    }

    // -----------------------------
    // Step 2.5: plugin jar resource scan
    // -----------------------------
    private static int applyPluginJarResourcePacks(WeaponRegistry weaponRegistry) {
        int applied = 0;

        for (var plugin : PluginManager.get().getPlugins()) {
            if (plugin == null) continue;

            // Skip Hypixel internals
            String cn = plugin.getClass().getName();
            if (cn.startsWith("com.hypixel.")) continue;

            // Skip HCA itself (Hotbar pack is applied in step 1)
            if (cn.startsWith("com.abilities.abilitiesplugin.")) continue;

            Path jarPath = null;
            try {
                var cs = plugin.getClass().getProtectionDomain().getCodeSource();
                if (cs == null || cs.getLocation() == null) continue;
                jarPath = Paths.get(cs.getLocation().toURI());
            } catch (Throwable ignored) {}

            if (jarPath == null) continue;

            // Only scan real jars here
            String jarLower = jarPath.toString().toLowerCase(Locale.ROOT);
            if (!jarLower.endsWith(".jar")) continue;
            if (!Files.exists(jarPath)) continue;

            String jarName = jarPath.getFileName().toString();

            try (ZipFile zip = new ZipFile(jarPath.toFile())) {
                List<String> packEntries = new ArrayList<>();

                // Standard pack name
                if (zip.getEntry("HCA/HCA_pack.json") != null) {
                    packEntries.add("HCA/HCA_pack.json");
                }

                // Alternate names: HCA/*hca_pack.json (your existing behavior)
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (name == null) continue;

                    String n = name.replace("\\", "/");
                    if (!n.startsWith("HCA/")) continue;
                    if (!n.toLowerCase(Locale.ROOT).endsWith("hca_pack.json")) continue;

                    packEntries.add(n);
                }

                packEntries.sort(String.CASE_INSENSITIVE_ORDER);
                if (packEntries.isEmpty()) continue;

                ResourceAccess access = new ZipResourceAccess(jarPath);

                for (String entryName : packEntries) {
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null) continue;

                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }

                    String tag = "PLUGINJAR::" + jarName + "::" + entryName;
                    boolean ok = applyPackBytes(weaponRegistry, access, bytes, tag);
                    if (ok) applied++;

                    System.out.println("[HCA] plugin-jar pack applied=" + ok + " tag=" + tag);
                }
            } catch (Throwable t) {
                System.out.println("[HCA] failed scanning plugin jar " + jarName + ": " + t.getMessage());
            }
        }

        return applied;
    }

    // -----------------------------
    // Step 3: mods folder jar scan
    // -----------------------------
    private static int applyModsFolderJarPacks(WeaponRegistry weaponRegistry) {
        Path modsDir = findModsDir();
        if (modsDir == null) {
            System.out.println("[HCA] mods folder not found; skipping jar scan");
            return 0;
        }

        int applied = 0;
        List<Path> jars = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path p : ds) jars.add(p);
        } catch (Throwable t) {
            System.out.println("[HCA] failed listing mods folder: " + t.getMessage());
            return 0;
        }

        jars.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path jarPath : jars) {
            String jarName = jarPath.getFileName().toString();
            try (ZipFile zip = new ZipFile(jarPath.toFile())) {

                List<String> packEntries = new ArrayList<>();

                // Standard pack name
                if (zip.getEntry("HCA/HCA_pack.json") != null) {
                    packEntries.add("HCA/HCA_pack.json");
                }

                // Alternate names: HCA/*_hca_pack.json
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (name == null) continue;

                    String n = name.replace("\\", "/");
                    if (!n.startsWith("HCA/")) continue;
                    if (!n.toLowerCase(Locale.ROOT).endsWith("_hca_pack.json")) continue;

                    packEntries.add(n);
                }

                packEntries.sort(String.CASE_INSENSITIVE_ORDER);

                if (packEntries.isEmpty()) continue;

                ResourceAccess access = new ZipResourceAccess(jarPath);

                for (String entryName : packEntries) {
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null) continue;

                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }

                    String tag = jarName + "::" + entryName;
                    boolean ok = applyPackBytes(weaponRegistry, access, bytes, tag);
                    if (ok) applied++;

                    System.out.println("[HCA] jar pack applied=" + ok + " tag=" + tag);
                }

            } catch (Throwable t) {
                System.out.println("[HCA] failed scanning jar " + jarName + ": " + t.getMessage());
            }
        }

        return applied;
    }

    // -----------------------------
    // Apply one pack to WeaponRegistry
    // -----------------------------
    private static boolean applyPackBytes(
            WeaponRegistry weaponRegistry,
            ResourceAccess access,
            byte[] packBytes,
            String sourceTag
    ) {
        if (weaponRegistry == null) return false;
        if (access == null) return false;
        if (packBytes == null || packBytes.length == 0) return false;

        JsonObject root;
        try (InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(packBytes), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el == null || !el.isJsonObject()) return false;
            root = el.getAsJsonObject();
        } catch (Throwable t) {
            System.out.println("[HCA] invalid pack JSON from " + sourceTag + " : " + t.getMessage());
            return false;
        }

        // ---- Overrides ----
        JsonObject overridesObj = (root.get("Overrides") != null && root.get("Overrides").isJsonObject())
                ? root.getAsJsonObject("Overrides")
                : null;

        if (overridesObj != null) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : overridesObj.entrySet()) {
                String itemId = e.getKey();
                String useDef = null;
                try { useDef = e.getValue().getAsString(); } catch (Throwable ignored) {}

                if (itemId == null || itemId.isBlank()) continue;
                if (useDef == null || useDef.isBlank()) continue;
                map.put(itemId, useDef);
            }
            if (!map.isEmpty()) weaponRegistry.registerOverrideMap(map, sourceTag);
        }

        // ---- OverrideList ----
        JsonArray overrideListArr = (root.get("OverrideList") != null && root.get("OverrideList").isJsonArray())
                ? root.getAsJsonArray("OverrideList")
                : null;

        if (overrideListArr != null && overrideListArr.size() > 0) {
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement e : overrideListArr) {
                if (e != null && e.isJsonObject()) list.add(e.getAsJsonObject());
            }
            if (!list.isEmpty()) weaponRegistry.registerOverrideList(list, sourceTag);
        }

        // ---- Indexes ----
        JsonArray indexesArr = (root.get("Indexes") != null && root.get("Indexes").isJsonArray())
                ? root.getAsJsonArray("Indexes")
                : null;

        if (indexesArr != null && indexesArr.size() > 0) {
            Set<String> visited = new HashSet<>();
            for (JsonElement idxEl : indexesArr) {
                if (idxEl == null || idxEl.isJsonNull()) continue;
                String idxPath = null;
                try { idxPath = idxEl.getAsString(); } catch (Throwable ignored) {}
                if (idxPath == null || idxPath.isBlank()) continue;

                weaponRegistry.registerIndexFromAccess(access, normalizePath(idxPath), visited, sourceTag);
            }
        }

        return true;
    }

    // -----------------------------
    // Mods dir finder
    // -----------------------------
    private static Path findModsDir() {
        try {
            Path p = PluginManager.MODS_PATH;
            if (p != null && Files.isDirectory(p)) return p;
        } catch (Throwable ignored) {}

        Path p1 = Paths.get("mods");
        if (Files.isDirectory(p1)) return p1;

        Path p2 = Paths.get("Server", "mods");
        if (Files.isDirectory(p2)) return p2;

        try {
            Path cwd = Paths.get("").toAbsolutePath();
            Path p3 = cwd.resolve("mods");
            if (Files.isDirectory(p3)) return p3;
        } catch (Throwable ignored) {}

        return null;
    }

    private static String normalizePath(String p) {
        String s = (p == null) ? "" : p.replace("\\", "/").trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }
}
