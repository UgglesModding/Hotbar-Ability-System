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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HCA_AbilityApi {

    private static AbilityHotbarState state;
    public static final Random rng = new Random();

    private HCA_AbilityApi() {}

    public static void Init(AbilityHotbarState State) {
        state = State;
    }



    public static final class AbilitySlotInfo {
        public final int slotIndex0to8;

        public String key;
        public String rootInteraction;
        public String id;

        public boolean plugin;
        public boolean consume;

        public int maxUses;
        public int remainingUses;

        public float powerMultiplier;
        public int abilityValue;

        public String icon;

        public AbilitySlotInfo(int slotIndex0to8) {
            this.slotIndex0to8 = slotIndex0to8;
        }
    }


    public static AbilitySlotInfo GetSlotInformationByIndex(PlayerRef playerRef, int slotIndex0to8) {
        if (state == null) return null;
        if (playerRef == null) return null;
        if (slotIndex0to8 < 0 || slotIndex0to8 > 8) return null;

        var s = state.get(playerRef.getUsername());
        AbilitySlotInfo info = new AbilitySlotInfo(slotIndex0to8);

        info.key = s.hotbarItemIds[slotIndex0to8];
        info.rootInteraction = s.hotbarRootInteractions[slotIndex0to8];
        info.id = s.hotbarAbilityIds[slotIndex0to8];

        info.plugin = s.hotbarPluginFlags[slotIndex0to8];
        info.consume = s.hotbarConsumeFlags[slotIndex0to8];

        info.maxUses = s.hotbarMaxUses[slotIndex0to8];
        info.remainingUses = s.hotbarRemainingUses[slotIndex0to8];

        info.powerMultiplier = s.hotbarPowerMultipliers[slotIndex0to8];
        info.abilityValue = s.hotbarAbilityValues[slotIndex0to8];

        info.icon = s.hotbarIcons[slotIndex0to8];

        return info;
    }

    public static AbilitySlotInfo GetSlotInformationById(PlayerRef playerRef, String abilityId) {
        if (state == null) return null;
        if (playerRef == null) return null;
        if (abilityId == null || abilityId.isBlank()) return null;

        int idx = FindSlotIndexByID(playerRef, abilityId);
        if (idx < 0) return null;

        return GetSlotInformationByIndex(playerRef, idx);
    }

    public static AbilitySlotInfo[] GetAllAbilities(PlayerRef playerRef) {
        if (state == null) return new AbilitySlotInfo[0];
        if (playerRef == null) return new AbilitySlotInfo[0];

        AbilitySlotInfo[] arr = new AbilitySlotInfo[9];
        for (int i = 0; i < 9; i++) {
            arr[i] = GetSlotInformationByIndex(playerRef, i);
        }
        return arr;
    }

    public static boolean SetSlotInformation(PlayerRef playerRef, AbilitySlotInfo info) {
        if (state == null) return false;
        if (playerRef == null) return false;
        if (info == null) return false;

        int idx = info.slotIndex0to8;
        if (idx < 0 || idx > 8) return false;

        var s = state.get(playerRef.getUsername());

        s.hotbarItemIds[idx] = info.key;
        s.hotbarRootInteractions[idx] = info.rootInteraction;
        s.hotbarAbilityIds[idx] = info.id;

        s.hotbarPluginFlags[idx] = info.plugin;
        s.hotbarConsumeFlags[idx] = info.consume;

        s.hotbarMaxUses[idx] = info.maxUses;

        int max = info.maxUses;
        int rem = info.remainingUses;

        if (max > 0) {
            if (rem < 0) rem = 0;
            if (rem > max) rem = max;
        } else {
            if (rem < 0) rem = 0;
        }

        s.hotbarRemainingUses[idx] = rem;

        s.hotbarPowerMultipliers[idx] = (info.powerMultiplier > 0.0f) ? info.powerMultiplier : 1.0f;
        s.hotbarAbilityValues[idx] = info.abilityValue;

        s.hotbarIcons[idx] = info.icon;

        return true;
    }


    public static int FindSlotIndexByID(PlayerRef playerRef, String AbilityID) {
        if (state == null) return -1;
        if (playerRef == null) return -1;
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
        if (playerRef == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        int max = s.hotbarMaxUses[idx];
        if (max <= 0) return true;
        return s.hotbarRemainingUses[idx] > 0;
    }

    public static boolean SpendUse(PlayerRef playerRef, String AbilityID) {
        if (state == null) return false;
        if (playerRef == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        int max = s.hotbarMaxUses[idx];

        if (max <= 0) return true;

        int remaining = s.hotbarRemainingUses[idx];
        if (remaining <= 0) return false;

        s.hotbarRemainingUses[idx] = remaining - 1;
        return true;
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
        if (playerRef == null) return false;
        if (newValue <= 0.0f) newValue = 1.0f;

        var s = state.get(playerRef.getUsername());
        s.PlayerPowerMultiplier = newValue;
        return true;
    }

    public static boolean HasAbilityString(PlayerRef playerRef, String AbilityID, String value) {
        if (state == null || value == null) return false;
        if (playerRef == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        return s.hotbarStringFlags[idx].contains(value);
    }

    public static boolean AddAbilityString(PlayerRef playerRef, String AbilityID, String value) {
        if (state == null || value == null) return false;
        if (playerRef == null) return false;

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
        if (playerRef == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        var s = state.get(playerRef.getUsername());
        return s.hotbarStringFlags[idx].remove(value);
    }

    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef ref) { super(ref); }
        @Override protected void build(@Nonnull UICommandBuilder ui) {}
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

        if (state == null) return true;

        var s = state.get(context.PlayerRef.getUsername());
        if (s == null) return true;

        int slot1to9 = s.selectedAbilitySlot;
        if (slot1to9 < 1 || slot1to9 > 9) return true;

        int slot0to8 = slot1to9 - 1;

        if (!s.hotbarConsumeFlags[slot0to8]) return true;

        Inventory inv = context.Player.getInventory();
        ItemStack inHand = inv.getItemInHand();
        if (inHand == null || inHand.isEmpty()) return false;

        int qty = inHand.getQuantity();
        if (qty < amount) return false;

        int newQty = qty - amount;

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
