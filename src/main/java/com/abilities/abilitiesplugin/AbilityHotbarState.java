package com.abilities.abilitiesplugin;

import java.util.HashMap;
import java.util.Map;

public class AbilityHotbarState {

    public static final class State {
        public boolean enabled = false;

        // Which .ui file to append when this player's hotbar is opened.
        // Comes from the held weapon JSON field: "AbilityBar".
        public String abilityBarUiPath = null;

        public final String[] hotbarItemIds = new String[9];
        public final String[] hotbarRootInteractions = new String[9];

        public final String[] hotbarAbilityIds = new String[9];
        public final boolean[] hotbarPluginFlags = new boolean[9];

        public final int[] hotbarMaxUses = new int[9];
        public final float[] hotbarPowerMultipliers = new float[9];
        public final String[] hotbarIcons = new String[9];
        public final int[] hotbarRemainingUses = new int[9];

        public int selectedAbilitySlot = 1;

        public void fillAllEmpty() {
            abilityBarUiPath = null;

            for (int i = 0; i < 9; i++) {
                hotbarRootInteractions[i] = null;

                hotbarAbilityIds[i] = null;
                hotbarPluginFlags[i] = false;

                hotbarMaxUses[i] = 0;
                hotbarPowerMultipliers[i] = 1.0f;
                hotbarIcons[i] = null;
                hotbarRemainingUses[i] = 0;
            }
            selectedAbilitySlot = 1;
        }
    }

    private final Map<String, State> byUser = new HashMap<>();

    public State get(String username) {
        return byUser.computeIfAbsent(username, k -> {
            State s = new State();
            s.fillAllEmpty();
            return s;
        });
    }
}
