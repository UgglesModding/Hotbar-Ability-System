package com.abilities.abilitiesplugin;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Queue-first contribution receiver.
 *
 * - registerContributionPack(): ALWAYS queues if Hotbar isn't active yet.
 * - activate(): Hotbar calls this during its own setup to load:
 *      1) its own pack first
 *      2) then every queued pack in receive order
 * - After activation, new packs apply immediately (but still in arrival order).
 */
public final class AbilityReceiver {

    private static final Gson gson = new GsonBuilder().create();

    private static WeaponRegistry weaponRegistry;
    private static boolean active = false;

    private static final List<PendingPack> pending = new ArrayList<>();
    private static long seq = 0;

    private static final class PendingPack {
        final long order;
        final ClassLoader loader;
        final byte[] jsonBytes;
        final String sourceTag;

        PendingPack(long order, ClassLoader loader, byte[] jsonBytes, String sourceTag) {
            this.order = order;
            this.loader = loader;
            this.jsonBytes = jsonBytes;
            this.sourceTag = sourceTag;
        }
    }

    private AbilityReceiver() {}

    /**
     * Hotbar calls this in its setup().
     * Loads Hotbar's own pack FIRST, then flushes all queued packs in order.
     */
    public static synchronized boolean activate(
            WeaponRegistry registry,
            ClassLoader hotbarLoader,
            InputStream hotbarPackStream,
            String hotbarSourceTag
    ) {
        if (registry == null) {
            System.out.println("[AbilityReceiver] activate() failed: registry is null");
            return false;
        }

        weaponRegistry = registry;

        if (active) {
            // Already active: just apply the hotbar pack if provided
            if (hotbarPackStream != null) {
                try {
                    byte[] bytes = readAllBytes(hotbarPackStream);
                    applyPackBytes(hotbarLoader, bytes, hotbarSourceTag);
                    System.out.println("[AbilityReceiver] activate(): applied hotbar pack again (already active)");
                } catch (Throwable t) {
                    System.out.println("[AbilityReceiver] activate(): failed applying hotbar pack: " + t.getMessage());
                    return false;
                }
            }
            return true;
        }

        // 1) Apply Hotbar's own pack first
        if (hotbarPackStream != null) {
            try {
                byte[] bytes = readAllBytes(hotbarPackStream);
                applyPackBytes(hotbarLoader, bytes, hotbarSourceTag);
                System.out.println("[AbilityReceiver] activate(): applied hotbar pack");
            } catch (Throwable t) {
                System.out.println("[AbilityReceiver] activate(): failed applying hotbar pack: " + t.getMessage());
                return false;
            }
        } else {
            System.out.println("[AbilityReceiver] activate(): hotbar pack stream was null");
        }

        // 2) Mark active
        active = true;

        // 3) Flush pending packs in the order they arrived
        if (!pending.isEmpty()) {
            pending.sort(Comparator.comparingLong(p -> p.order));
            System.out.println("[AbilityReceiver] activate(): flushing queued packs=" + pending.size());

            for (PendingPack p : pending) {
                try {
                    applyPackBytes(p.loader, p.jsonBytes, p.sourceTag);
                } catch (Throwable t) {
                    System.out.println("[AbilityReceiver] Failed flushing pack " + p.sourceTag + ": " + t.getMessage());
                }
            }

            pending.clear();
        }

        return true;
    }

    /**
     * Mods call this at any time.
     * If Hotbar isn't active yet -> queue.
     * If Hotbar is active -> apply immediately.
     */
    public static synchronized boolean registerContributionPack(
            ClassLoader contributorLoader,
            InputStream packJsonStream,
            String sourceTag
    ) {
        if (contributorLoader == null || packJsonStream == null) return false;
        if (sourceTag == null || sourceTag.isBlank()) sourceTag = "Unknown";

        try {
            byte[] bytes = readAllBytes(packJsonStream);

            if (!active || weaponRegistry == null) {
                pending.add(new PendingPack(++seq, contributorLoader, bytes, sourceTag));
                System.out.println("[AbilityReceiver] queued pack from " + sourceTag + " (active=" + active + ")");
                return true;
            }

            // Active -> apply now
            applyPackBytes(contributorLoader, bytes, sourceTag);
            System.out.println("[AbilityReceiver] applied pack immediately from " + sourceTag);
            return true;

        } catch (Throwable t) {
            System.out.println("[AbilityReceiver] Failed pack from " + sourceTag + " : " + t.getMessage());
            return false;
        }
    }

    // ============================
    // Actual pack application
    // ============================

    private static void applyPackBytes(ClassLoader loader, byte[] jsonBytes, String sourceTag) throws Exception {
        try (InputStream is = new ByteArrayInputStream(jsonBytes);
             InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return;

            JsonObject root = el.getAsJsonObject();

            // ---- Overrides ----
            JsonObject overridesObj = root.getAsJsonObject("Overrides");
            if (overridesObj != null) {
                Map<String, String> map = new HashMap<>();
                for (String key : overridesObj.keySet()) {
                    JsonElement v = overridesObj.get(key);
                    if (v == null || v.isJsonNull()) continue;

                    String useDef;
                    try { useDef = v.getAsString(); }
                    catch (Throwable ignored) { continue; }

                    if (key == null || key.isBlank()) continue;
                    if (useDef == null || useDef.isBlank()) continue;

                    map.put(key, useDef);
                }

                if (!map.isEmpty()) {
                    weaponRegistry.registerOverrideMap(map, sourceTag);
                    System.out.println("[AbilityReceiver] Overrides merged=" + map.size() + " from " + sourceTag);
                }
            }

            // ---- Indexes ----
            JsonArray indexesArr = root.getAsJsonArray("Indexes");
            if (indexesArr != null && indexesArr.size() > 0) {
                Set<String> visited = new HashSet<>();
                int countIndexes = 0;

                for (JsonElement idxEl : indexesArr) {
                    if (idxEl == null || idxEl.isJsonNull()) continue;

                    String idxPath;
                    try { idxPath = idxEl.getAsString(); }
                    catch (Throwable ignored) { continue; }

                    if (idxPath == null || idxPath.isBlank()) continue;

                    idxPath = normalizePath(idxPath);
                    boolean ok = weaponRegistry.registerIndexFromContributor(loader, idxPath, visited, sourceTag);
                    if (ok) countIndexes++;
                }

                System.out.println("[AbilityReceiver] Indexes processed=" + countIndexes + " from " + sourceTag);
            }

            // ---- OverrideList ----
            JsonArray overrideListArr = root.getAsJsonArray("OverrideList");
            if (overrideListArr != null && overrideListArr.size() > 0) {
                List<JsonObject> list = new ArrayList<>();
                for (JsonElement oEl : overrideListArr) {
                    if (oEl == null || !oEl.isJsonObject()) continue;
                    list.add(oEl.getAsJsonObject());
                }

                if (!list.isEmpty()) {
                    weaponRegistry.registerOverrideList(list, sourceTag);
                    System.out.println("[AbilityReceiver] OverrideList merged=" + list.size() + " from " + sourceTag);
                }
            }
        }
    }

    private static String normalizePath(String p) {
        String s = p.replace("\\", "/").trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toByteArray();
    }
}
