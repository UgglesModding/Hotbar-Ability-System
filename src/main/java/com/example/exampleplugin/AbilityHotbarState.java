package com.example.exampleplugin;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityHotbarState {

    public static class State {
        public volatile boolean enabled = false;

        public volatile String currentAbilityBarId = null;

        // 1..9 selection (confirm on 0 uses this)
        public volatile int selectedAbilitySlot = 1;

        // Stores IN-GAME ITEM IDS (e.g. "Ability_DaggerLeap")
        public final String[] hotbarItemIds = new String[] {
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID,
                AbilityRegistry.EMPTY_ITEM_ID
        };

        public void fillAllEmpty() {
            Arrays.fill(hotbarItemIds, AbilityRegistry.EMPTY_ITEM_ID);
        }
    }

    private final ConcurrentHashMap<String, State> byUsername = new ConcurrentHashMap<>();

    public State get(String username) {
        return byUsername.computeIfAbsent(username, k -> new State());
    }
}
