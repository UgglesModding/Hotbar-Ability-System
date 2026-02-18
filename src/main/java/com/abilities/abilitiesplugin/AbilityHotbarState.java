package com.abilities.abilitiesplugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;


public class AbilityHotbarState {

    public static final class State {
        public boolean enabled = false;


        public String abilityBarUiPath = null;

        public final String[] hotbarItemIds = new String[9];
        public final String[] hotbarRootInteractions = new String[9];

        public final String[] hotbarAbilityIds = new String[9];
        public final boolean[] hotbarPluginFlags = new boolean[9];
        public final boolean[] hotbarConsumeFlags = new boolean[9];

        public final int[] hotbarMaxUses = new int[9];
        public final float[] hotbarPowerMultipliers = new float[9];
        public final String[] hotbarIcons = new String[9];
        public final int[] hotbarRemainingUses = new int[9];
        public final int[] hotbarAbilityValues = new int[9];
        public final float[] hotbarCooldownTimes = new float[9];
        public final float[] hotbarRechargeTimes = new float[9];
        public final boolean[] hotbarStartWithCooldown = new boolean[9];
        public final long[] hotbarCooldownUntilMs = new long[9];
        public final double[] hotbarRechargeAccumulatorSec = new double[9];
        public final long[] hotbarLastUpdateMs = new long[9];
        @SuppressWarnings("unchecked")
        public final List<String>[] hotbarStringFlags = new ArrayList[9];

        public float PlayerPowerMultiplier = 1.0f;

        public int selectedAbilitySlot = 1;

        public int suppressNextSetActiveSlot = -1;
        public long suppressNextSetActiveSlotUntilMs = 0;
        public long nextHudRefreshAtMs = 0;

        public void fillAllEmpty() {
            abilityBarUiPath = null;

            for (int i = 0; i < 9; i++) {
                hotbarRootInteractions[i] = null;

                hotbarAbilityIds[i] = null;
                hotbarPluginFlags[i] = false;
                hotbarConsumeFlags[i] = false;

                hotbarMaxUses[i] = 0;
                hotbarPowerMultipliers[i] = 1.0f;
                hotbarIcons[i] = null;
                hotbarRemainingUses[i] = 0;
                hotbarCooldownTimes[i] = 0.3f;
                hotbarRechargeTimes[i] = 1.0f;
                hotbarStartWithCooldown[i] = true;
                hotbarCooldownUntilMs[i] = 0L;
                hotbarRechargeAccumulatorSec[i] = 0.0;
                hotbarLastUpdateMs[i] = 0L;
                hotbarStringFlags[i] = new ArrayList<>();

            }
            selectedAbilitySlot = 1;
            nextHudRefreshAtMs = 0L;
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
