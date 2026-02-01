package com.abilities.abilitiesplugin;

import com.google.gson.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AbilityReceiver {

    private static final Gson gson = new GsonBuilder().create();
    private static WeaponRegistry weaponRegistry;

    private AbilityReceiver() {}

    public static void init(WeaponRegistry registry) {
        weaponRegistry = registry;
    }

    public static boolean registerContributionPack(
            ClassLoader contributorLoader,
            InputStream packJsonStream,
            String sourceTag
    ) {
        if (weaponRegistry == null) {
            System.out.println("[AbilityReceiver] Not initialized; cannot accept pack from " + sourceTag);
            return false;
        }
        if (contributorLoader == null || packJsonStream == null) return false;

        try (InputStreamReader r = new InputStreamReader(packJsonStream, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return false;

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
                    boolean ok = weaponRegistry.registerIndexFromContributor(contributorLoader, idxPath, visited, sourceTag);
                    if (ok) countIndexes++;
                }

                System.out.println("[AbilityReceiver] Indexes processed=" + countIndexes + " from " + sourceTag);
            }

            // ---- OverrideList (THIS WAS MISSING) ----
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

            return true;

        } catch (Throwable t) {
            System.out.println("[AbilityReceiver] Failed pack from " + sourceTag + " : " + t.getMessage());
            return false;
        }
    }

    private static String normalizePath(String p) {
        String s = p.replace("\\", "/").trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }
}
