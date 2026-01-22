package com.example.exampleplugin;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityHotbarState {

    public static class State {
        public volatile boolean enabled = false;
        public volatile int selectedAbilitySlot = 1;

        // IN-GAME ITEM IDS (Ability_DaggerLeap)
        public final String[] hotbarItemIds = new String[9];

        // ROOT INTERACTION IDS (Root_Ability_DaggerLeap)
        public final String[] hotbarRootInteractions = new String[9];

        public void fillAllEmpty() {
            Arrays.fill(hotbarItemIds, AbilityRegistry.EMPTY_ITEM_ID);
            Arrays.fill(hotbarRootInteractions, null);
        }
    }

    private final ConcurrentHashMap<String, State> byUsername = new ConcurrentHashMap<>();

    public State get(String username) {
        return byUsername.computeIfAbsent(username, k -> {
            State s = new State();
            s.fillAllEmpty();
            return s;
        });
    }
}
