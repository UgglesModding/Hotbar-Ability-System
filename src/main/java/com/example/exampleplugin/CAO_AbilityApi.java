package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Random;

public final class CAO_AbilityApi {

    private static AbilityHotbarState state; // set once from plugin setup
    private static final Random rng = new Random();

    private CAO_AbilityApi() {}

    /** Call this once in ExamplePlugin.setup() after you create your AbilityHotbarState. */
    public static void Init(AbilityHotbarState State) {
        state = State;
    }

    public static boolean SpendUse(PlayerRef playerRef, String AbilityID) {
        if (state == null) {
            playerRef.sendMessage(Message.raw("[CAO] SpendUse failed: API not initialized"));
            return false;
        }

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        int max = s.hotbarMaxUses[idx];

        if (max <= 0) return true; // unlimited

        int remaining = s.hotbarRemainingUses[idx];
        if (remaining <= 0) return false;

        s.hotbarRemainingUses[idx] = remaining - 1;
        return true;
    }


    /** Set remaining uses for the first hotbar slot matching AbilityID. */
    public static boolean SetUses(PlayerRef playerRef, String AbilityID, int NewRemaining) {
        if (state == null) {
            playerRef.sendMessage(Message.raw("[CAO] SetUses failed: API not initialized"));
            return false;
        }
        if (AbilityID == null || AbilityID.isBlank()) return false;

        var s = state.get(playerRef.getUsername());

        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null) continue;
            if (!id.equalsIgnoreCase(AbilityID)) continue;

            int max = s.hotbarMaxUses[i];

            // If MaxUses <= 0, treat as unlimited; setting remaining doesn't matter, but we'll allow it.
            if (max > 0) {
                if (NewRemaining < 0) NewRemaining = 0;
                if (NewRemaining > max) NewRemaining = max;
            } else {
                if (NewRemaining < 0) NewRemaining = 0;
            }

            s.hotbarRemainingUses[i] = NewRemaining;
            return true;
        }
        return false;
    }

    public static boolean AddUses(PlayerRef playerRef, String AbilityID, int Delta) {
        if (Delta == 0) return true;
        if (state == null) return false;

        var s = state.get(playerRef.getUsername());

        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null) continue;
            if (!id.equalsIgnoreCase(AbilityID)) continue;

            int max = s.hotbarMaxUses[i];

            // If unlimited, adding uses is irrelevant; return true anyway.
            if (max <= 0) return true;

            int cur = s.hotbarRemainingUses[i];
            int next = cur + Delta;

            if (next < 0) next = 0;
            if (next > max) next = max;

            s.hotbarRemainingUses[i] = next;
            return true;
        }
        return false;
    }

    public static int FindSlotIndexByID(PlayerRef playerRef, String AbilityID) {
        if (state == null) return -1;
        if (AbilityID == null || AbilityID.isBlank()) return -1;

        var s = state.get(playerRef.getUsername());
        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null) continue;
            if (id.equalsIgnoreCase(AbilityID)) return i;
        }
        return -1;
    }

    /** True if ability is usable right now (unlimited OR RemainingUses > 0). */
    public static boolean HasUsesLeft(PlayerRef playerRef, String AbilityID) {
        if (state == null) return false;
        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        int max = s.hotbarMaxUses[idx];

        // MaxUses <= 0 => unlimited
        if (max <= 0) return true;

        return s.hotbarRemainingUses[idx] > 0;
    }

    /** Adds +1 use to a random ability in hotbar, excluding ExcludeID (and excluding empty IDs). */
    public static boolean AddUseToRandomAbility(PlayerRef playerRef, String ExcludeID) {
        if (state == null) return false;

        var s = state.get(playerRef.getUsername());

        int[] candidates = new int[9];
        int count = 0;

        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null || id.isBlank()) continue;
            if (ExcludeID != null && id.equalsIgnoreCase(ExcludeID)) continue;

            int max = s.hotbarMaxUses[i];
            if (max <= 0) continue; // unlimited => doesn't need charges

            int remaining = s.hotbarRemainingUses[i];
            if (remaining >= max) continue; // already full

            candidates[count++] = i;
        }

        if (count <= 0) return false;

        int pick = candidates[rng.nextInt(count)];
        s.hotbarRemainingUses[pick] = s.hotbarRemainingUses[pick] + 1;
        return true;
    }

    /** Placeholder: keep the door open for real RootInteraction execution later. */
    public static boolean DoRootInteraction( //placeholder for logic that uses rootinteractions. No known logic yet to run rootinteractions
            PlayerRef playerRef,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            World world,
            String RootInteraction
    ) {
        if (RootInteraction == null || RootInteraction.isBlank()) return false;

        // We don't have engine API yet; for now just log.
        playerRef.sendMessage(Message.raw("[CAO] DoRootInteraction placeholder: " + RootInteraction));
        return false;
    }

    /** Placeholder timer hook. Replace later when we find the official scheduler API. */
    public static void RunLater(PlayerRef playerRef, int TicksDelay, Runnable Action) {
        //placeholder logic for waiting and delays. do not use

        playerRef.sendMessage(Message.raw("[CAO] RunLater placeholder (ticks=" + TicksDelay + ") running immediately"));
        try {
            Action.run();
        } catch (Throwable t) {
            playerRef.sendMessage(Message.raw("[CAO] RunLater action failed: " + t.getClass().getSimpleName()));
        }
    }
}
