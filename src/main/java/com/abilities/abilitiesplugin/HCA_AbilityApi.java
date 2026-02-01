package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;

public final class HCA_AbilityApi {

    private static AbilityHotbarState state; // set once from plugin setup
    private static final Random rng = new Random();

    private HCA_AbilityApi() {
    }

    public static void Init(AbilityHotbarState State) {
        state = State;
    }

    public static boolean SpendUse(PlayerRef playerRef, String AbilityID) {
        if (state == null) return false;

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

    public static boolean SetUses(PlayerRef playerRef, String AbilityID, int NewRemaining) {
        if (state == null) return false;
        if (AbilityID == null || AbilityID.isBlank()) return false;

        var s = state.get(playerRef.getUsername());

        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null) continue;
            if (!id.equalsIgnoreCase(AbilityID)) continue;

            int max = s.hotbarMaxUses[i];

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
            if (max <= 0) return true; // unlimited

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

    public static boolean HasUsesLeft(PlayerRef playerRef, String AbilityID) {
        if (state == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        int max = s.hotbarMaxUses[idx];

        if (max <= 0) return true; // unlimited

        return s.hotbarRemainingUses[idx] > 0;
    }

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
            if (max <= 0) continue;

            int remaining = s.hotbarRemainingUses[i];
            if (remaining >= max) continue;

            candidates[count++] = i;
        }

        if (count <= 0) return false;

        int pick = candidates[rng.nextInt(count)];
        s.hotbarRemainingUses[pick] = s.hotbarRemainingUses[pick] + 1;
        return true;
    }

    public static boolean SetUsesBySlot(PlayerRef playerRef, int slot1to9, int newRemaining) {
        if (state == null) {
            playerRef.sendMessage(Message.raw("[CAO] SetUsesBySlot failed: API not initialized"));
            return false;
        }

        if (slot1to9 < 1 || slot1to9 > 9) return false;

        var s = state.get(playerRef.getUsername());
        int idx = slot1to9 - 1;

        int max = s.hotbarMaxUses[idx];

        if (max > 0) {
            if (newRemaining < 0) newRemaining = 0;
            if (newRemaining > max) newRemaining = max;
            s.hotbarRemainingUses[idx] = newRemaining;
            return true;
        }

        if (newRemaining < 0) newRemaining = 0;
        s.hotbarRemainingUses[idx] = newRemaining;
        return true;
    }

    public static boolean RefillAllAbilitiesWithUses(PlayerRef playerRef, String excludeID) {
        if (state == null) return false;

        var s = state.get(playerRef.getUsername());
        boolean changed = false;

        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null || id.isBlank()) continue;
            if (excludeID != null && id.equalsIgnoreCase(excludeID)) continue;

            int max = s.hotbarMaxUses[i];
            if (max <= 0) continue;

            int remaining = s.hotbarRemainingUses[i];
            if (remaining >= max) continue;

            s.hotbarRemainingUses[i] = max;
            changed = true;
        }

        return changed;
    }

    public static boolean RefillRandomAbilityWithUses(PlayerRef playerRef, String excludeID) {
        if (state == null) return false;

        var s = state.get(playerRef.getUsername());

        int[] candidates = new int[9];
        int count = 0;

        for (int i = 0; i < 9; i++) {
            String id = s.hotbarAbilityIds[i];
            if (id == null || id.isBlank()) continue;
            if (excludeID != null && id.equalsIgnoreCase(excludeID)) continue;

            int max = s.hotbarMaxUses[i];
            if (max <= 0) continue;

            int remaining = s.hotbarRemainingUses[i];
            if (remaining >= max) continue;

            candidates[count++] = i;
        }

        if (count <= 0) return false;

        int pick = candidates[rng.nextInt(count)];
        s.hotbarRemainingUses[pick] = s.hotbarMaxUses[pick];
        return true;
    }

    public static boolean DoRootInteraction(
            PlayerRef playerRef,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            World world,
            String RootInteraction
    ) {
        if (RootInteraction == null || RootInteraction.isBlank()) return false;
        return false;
    }

    public static void UpdateHud(AbilityContext Context) {
        if (Context == null) return;
        if (Context.Player == null) return;
        if (state == null) return;

        Context.Player.getHudManager().setCustomHud(
                Context.PlayerRef,
                new AbilityHotbarHud(Context.PlayerRef, state)
        );
    }

    public static boolean SetPlayerPowerMultiplier(PlayerRef playerRef, float newValue) {
        if (state == null) return false;
        if (newValue <= 0.0f) newValue = 1.0f;

        var s = state.get(playerRef.getUsername());
        s.PlayerPowerMultiplier = newValue;
        return true;
    }

    public static float GetPlayerPowerMultiplier(PlayerRef playerRef) {
        if (state == null) return 1.0f;

        var s = state.get(playerRef.getUsername());
        float v = s.PlayerPowerMultiplier;
        return (v <= 0.0f) ? 1.0f : v;
    }

    public static boolean HasAbilityString(PlayerRef playerRef, String AbilityID, String value) {
        if (state == null || value == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        return s.hotbarStringFlags[idx].contains(value);
    }

    public static boolean AddAbilityString(PlayerRef playerRef, String AbilityID, String value) {
        if (state == null || value == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        List<String> list = s.hotbarStringFlags[idx];

        if (list.contains(value)) return false;

        list.add(value);
        return true;
    }

    public static boolean RemoveAbilityString(PlayerRef playerRef, String AbilityID, String value) {
        if (state == null || value == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        return s.hotbarStringFlags[idx].remove(value);
    }

    // =========================================================
    // Ability bar OFF helper (no extra files)
    // =========================================================

    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef ref) {
            super(ref);
        }

        @Override
        protected void build(@Nonnull UICommandBuilder ui) {
        }
    }

    public static void TurnOffAbilityBar(Player player, PlayerRef playerRef) {
        if (player == null || playerRef == null) return;

        if (state != null) {
            var s = state.get(playerRef.getUsername());
            if (s != null) s.enabled = false;
        }

        player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
    }

    public static boolean ConsumeChargeInHand(AbilityContext context, int amount) {
        if (context == null) return false;
        if (context.Player == null || context.PlayerRef == null) return false;

        // If API isn't initialized, do nothing (don't break abilities)
        if (state == null) return true;

        var s = state.get(context.PlayerRef.getUsername());
        if (s == null) return true;

        int slot1to9 = s.selectedAbilitySlot;
        if (slot1to9 < 1 || slot1to9 > 9) {
            // If we don't know the slot, don't consume.
            return true;
        }

        int slot0to8 = slot1to9 - 1;

        // Only consume if this slot has Consume=true
        if (!s.hotbarConsumeFlags[slot0to8]) {
            return true;
        }

        Inventory inv = context.Player.getInventory();
        ItemStack inHand = inv.getItemInHand();
        if (inHand == null || inHand.isEmpty()) return false;

        int qty = inHand.getQuantity();
        if (qty < amount) return false;

        int newQty = qty - amount;

        // If we're about to hit 0, and the bar is open, turn it off first
        if (newQty <= 0 && s.enabled) {
            TurnOffAbilityBar(context.Player, context.PlayerRef);
        }

        if (inv.usingToolsItem()) {
            ItemContainer tools = inv.getTools();
            short slot = (short) inv.getActiveToolsSlot();

            ItemStack replacement = (newQty <= 0) ? ItemStack.EMPTY : inHand.withQuantity(newQty);
            if (replacement == null) replacement = ItemStack.EMPTY;

            ItemStackSlotTransaction tx = tools.setItemStackForSlot(slot, replacement);
            return tx != null && tx.succeeded();
        } else {
            ItemContainer hotbar = inv.getHotbar();
            short slot = (short) inv.getActiveHotbarSlot();

            ItemStack replacement = (newQty <= 0) ? ItemStack.EMPTY : inHand.withQuantity(newQty);
            if (replacement == null) replacement = ItemStack.EMPTY;

            ItemStackSlotTransaction tx = hotbar.setItemStackForSlot(slot, replacement);
            return tx != null && tx.succeeded();
        }
    }
}
