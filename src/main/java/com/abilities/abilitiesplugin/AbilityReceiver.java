package com.abilities.abilitiesplugin;

import com.google.gson.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Central receiver/queue for HCA packs.
 *
 * Anyone can queue packs at any time.
 * Hotbar calls activate(...) once during its setup to apply:
 *   1) its own pack
 *   2) queued packs in the order they arrived
 */
public final class AbilityReceiver {

    // =========================
    // Resource Access (pluggable)
    // =========================
    public interface ResourceAccess {
        InputStream open(String resourcePath);
    }

    public static final class ClassLoaderAccess implements ResourceAccess {
        private final ClassLoader loader;
        public ClassLoaderAccess(ClassLoader loader) { this.loader = loader; }

        @Override
        public InputStream open(String resourcePath) {
            if (loader == null) return null;
            return loader.getResourceAsStream(normalizePath(resourcePath));
        }
    }

    // =========================
    // Internal queue
    // =========================
    private static final class QueuedPack {
        final ResourceAccess access;
        final byte[] packBytes;
        final String sourceTag;

        QueuedPack(ResourceAccess access, byte[] packBytes, String sourceTag) {
            this.access = access;
            this.packBytes = packBytes;
            this.sourceTag = sourceTag;
        }
    }

    private static final List<QueuedPack> queue = new ArrayList<>();
    private static WeaponRegistry weaponRegistry;

    private AbilityReceiver() {}

    // =========================
    // Queue API
    // =========================
    public static synchronized boolean queuePack(ResourceAccess access, byte[] packJsonBytes, String sourceTag) {
        if (access == null) return false;
        if (packJsonBytes == null || packJsonBytes.length == 0) return false;
        if (sourceTag == null || sourceTag.isBlank()) sourceTag = "UnknownSource";

        queue.add(new QueuedPack(access, packJsonBytes, sourceTag));
        System.out.println("[AbilityReceiver] Queued pack from " + sourceTag + " bytes=" + packJsonBytes.length);
        return true;
    }

    public static synchronized boolean queuePack(ClassLoader contributorLoader, InputStream packJsonStream, String sourceTag) {
        if (contributorLoader == null || packJsonStream == null) return false;
        try {
            byte[] bytes = packJsonStream.readAllBytes();
            return queuePack(new ClassLoaderAccess(contributorLoader), bytes, sourceTag);
        } catch (Throwable t) {
            System.out.println("[AbilityReceiver] queuePack(stream) failed from " + sourceTag + " : " + t.getMessage());
            return false;
        }
    }

    // =========================
    // Activation (Hotbar calls once)
    // =========================
    public static synchronized boolean activate(
            WeaponRegistry registry,
            ClassLoader hotbarLoader,
            InputStream hotbarPackStream,
            String hotbarSourceTag
    ) {
        if (registry == null) return false;
        if (hotbarLoader == null) return false;
        if (hotbarPackStream == null) return false;

        weaponRegistry = registry;

        ResourceAccess hotbarAccess = new ClassLoaderAccess(hotbarLoader);

        byte[] hotbarPackBytes;
        try {
            hotbarPackBytes = hotbarPackStream.readAllBytes();
        } catch (Throwable t) {
            System.out.println("[AbilityReceiver] Failed reading hotbar pack bytes: " + t.getMessage());
            return false;
        }

        // 1) Apply Hotbar pack first
        String tag = (hotbarSourceTag == null || hotbarSourceTag.isBlank()) ? "HotbarAbilities" : hotbarSourceTag;
        boolean okHotbar = applyPackBytes(hotbarAccess, hotbarPackBytes, tag);

        // 2) Apply queued packs in arrival order
        int applied = 0;
        for (QueuedPack p : queue) {
            if (p == null) continue;
            boolean ok = applyPackBytes(p.access, p.packBytes, p.sourceTag);
            if (ok) applied++;
        }

        System.out.println("[AbilityReceiver] Activated. hotbarOk=" + okHotbar
                + " queuedApplied=" + applied + " queuedTotal=" + queue.size());

        // Optional: clear so re-activate doesn't double-apply
        queue.clear();

        return okHotbar;
    }

    // =========================
    // Pack parsing & apply
    // =========================
    private static boolean applyPackBytes(ResourceAccess access, byte[] packBytes, String sourceTag) {
        if (weaponRegistry == null) return false;
        if (access == null) return false;
        if (packBytes == null || packBytes.length == 0) return false;

        JsonObject root;
        try (InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(packBytes), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el == null || !el.isJsonObject()) return false;
            root = el.getAsJsonObject();
        } catch (Throwable t) {
            System.out.println("[AbilityReceiver] Invalid pack JSON from " + sourceTag + " : " + t.getMessage());
            return false;
        }

        // ---- Overrides (shorthand) ----
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

        // ---- OverrideList (patches) ----
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

        // ---- Indexes (weapon defs) ----
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

    private static String normalizePath(String p) {
        String s = p.replace("\\", "/").trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }
}
