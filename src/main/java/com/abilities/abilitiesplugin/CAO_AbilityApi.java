package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Random;

public final class CAO_AbilityApi {

    private static AbilityHotbarState state; // set once from plugin setup
    private static final Random rng = new Random();

    private CAO_AbilityApi() {
    }

    public static void Init(AbilityHotbarState State) {
        state = State;
    }

    public static boolean SpendUse(PlayerRef playerRef, String AbilityID) {
        if (state == null) {
            //playerRef.sendMessage(Message.raw("[CAO] SpendUse failed: API not initialized"));
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

    //Set remaining uses for the first hotbar slot matching AbilityID.
    public static boolean SetUses(PlayerRef playerRef, String AbilityID, int NewRemaining) {
        if (state == null) {
            //playerRef.sendMessage(Message.raw("[CAO] SetUses failed: API not initialized"));
            return false;
        }
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
                // unlimited: setting doesn't really matter, but keep it non-negative
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

    //True if ability is usable right now (unlimited OR RemainingUses > 0).

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

    // =========================================================
    // NEW: Slot-based setter + refill abilities
    // =========================================================


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

        // max <= 0 => unlimited; nothing to "refill", but allow non-negative assignment anyway
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
            if (max <= 0) continue; // unlimited => ignore

            int remaining = s.hotbarRemainingUses[i];
            if (remaining >= max) continue; // already full

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

    //Placeholder: keep the door open for real RootInteraction execution later.

    public static boolean DoRootInteraction(
            PlayerRef playerRef,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            World world,
            String RootInteraction
    ) {
        if (RootInteraction == null || RootInteraction.isBlank()) return false;

        //playerRef.sendMessage(Message.raw("[CAO] DoRootInteraction placeholder: " + RootInteraction));
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

    public final class ChargeConsumption {

        private ChargeConsumption() {}

        public static boolean consumeChargeInHand(Player player, String itemId, int amount) {
            if (player == null || itemId == null || itemId.isBlank() || amount <= 0) return false;

            Inventory inv = player.getInventory();
            ItemStack inHand = inv.getItemInHand();
            if (inHand == null || inHand.isEmpty()) return false;

            if (!itemId.equals(inHand.getItemId())) return false;

            int qty = inHand.getQuantity();
            if (qty < amount) return false;

            int newQty = qty - amount;

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

}
