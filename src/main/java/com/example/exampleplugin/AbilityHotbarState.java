package com.example.exampleplugin;

import java.util.HashMap;
import java.util.Map;

public class AbilityHotbarState {

    public static final class State {
        public boolean enabled = false;

        public final String[] hotbarItemIds = new String[9];
        public final String[] hotbarRootInteractions = new String[9];

        // NEW
        public final String[] hotbarAbilityIds = new String[9];     // weapon slot "ID"
        public final boolean[] hotbarPluginFlags = new boolean[9];  // weapon slot "Plugin"

        public int selectedAbilitySlot = 1;

        public void fillAllEmpty() {
            for (int i = 0; i < 9; i++) {
                hotbarItemIds[i] = AbilityRegistry.EMPTY_ITEM_ID;
                hotbarRootInteractions[i] = null;

                hotbarAbilityIds[i] = null;
                hotbarPluginFlags[i] = false;
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
