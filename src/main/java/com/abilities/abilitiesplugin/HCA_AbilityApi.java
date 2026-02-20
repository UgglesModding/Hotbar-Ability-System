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
        public float cooldownTime = 0.3f;
        public float rechargeTime = 1.0f;
        public boolean startWithCooldown = true;
        public long cooldownUntilMs;

        public AbilitySlotInfo(int slotIndex0to8) {
            this.slotIndex0to8 = slotIndex0to8;
        }
    }


    public static AbilitySlotInfo GetSlotInformationByIndex(PlayerRef playerRef, int slotIndex0to8) {
        if (state == null) return null;
        if (playerRef == null) return null;
        if (slotIndex0to8 < 0 || slotIndex0to8 > 8) return null;

        var s = state.get(playerRef.getUsername());
        tickRecharge(s, slotIndex0to8, System.currentTimeMillis());
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
        info.cooldownTime = s.hotbarCooldownTimes[slotIndex0to8];
        info.rechargeTime = s.hotbarRechargeTimes[slotIndex0to8];
        info.startWithCooldown = s.hotbarStartWithCooldown[slotIndex0to8];
        info.cooldownUntilMs = s.hotbarCooldownUntilMs[slotIndex0to8];

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
        s.hotbarCooldownTimes[idx] = Math.max(0.0f, info.cooldownTime);
        s.hotbarRechargeTimes[idx] = Math.max(0.0f, info.rechargeTime);
        s.hotbarStartWithCooldown[idx] = info.startWithCooldown;
        s.hotbarCooldownUntilMs[idx] = Math.max(0L, info.cooldownUntilMs);
        s.hotbarLastUpdateMs[idx] = System.currentTimeMillis();
        s.hotbarRechargeAccumulatorSec[idx] = 0.0;

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

        return HasUsesLeft(playerRef, idx);
    }

    public static boolean HasUsesLeft(PlayerRef playerRef, int slotIndex0to8) {
        if (state == null) return false;
        if (playerRef == null) return false;
        if (slotIndex0to8 < 0 || slotIndex0to8 > 8) return false;

        var s = state.get(playerRef.getUsername());
        long now = System.currentTimeMillis();
        tickRecharge(s, slotIndex0to8, now);

        if (isLockedByCooldown(s, slotIndex0to8, now)) return false;

        int max = s.hotbarMaxUses[slotIndex0to8];
        if (max <= 0) return true;
        return s.hotbarRemainingUses[slotIndex0to8] > 0;
    }

    public static boolean SpendUse(PlayerRef playerRef, String AbilityID) {
        if (state == null) return false;
        if (playerRef == null) return false;

        int idx = FindSlotIndexByID(playerRef, AbilityID);
        if (idx < 0) return false;

        return SpendUse(playerRef, idx);
    }

    public static boolean SpendUse(PlayerRef playerRef, int slotIndex0to8) {
        if (state == null) return false;
        if (playerRef == null) return false;
        if (slotIndex0to8 < 0 || slotIndex0to8 > 8) return false;

        var s = state.get(playerRef.getUsername());
        long now = System.currentTimeMillis();
        tickRecharge(s, slotIndex0to8, now);

        if (isLockedByCooldown(s, slotIndex0to8, now)) return false;

        int max = s.hotbarMaxUses[slotIndex0to8];

        if (max <= 0) {
            applyPostUseCooldown(s, slotIndex0to8, now);
            return true;
        }

        int remaining = s.hotbarRemainingUses[slotIndex0to8];
        if (remaining <= 0) return false;

        s.hotbarRemainingUses[slotIndex0to8] = remaining - 1;
        s.hotbarRechargeAccumulatorSec[slotIndex0to8] = 0.0;
        s.hotbarLastUpdateMs[slotIndex0to8] = now;
        applyPostUseCooldown(s, slotIndex0to8, now);
        return true;
    }

    public static void TickAllSlots(PlayerRef playerRef) {
        if (state == null || playerRef == null) return;
        var s = state.get(playerRef.getUsername());
        long now = System.currentTimeMillis();
        for (int i = 0; i < 9; i++) {
            tickRecharge(s, i, now);
        }
    }

    static float getCooldownOverlayRatio(AbilityHotbarState.State s, int idx, long nowMs) {
        if (s == null || idx < 0 || idx > 8) return 0.0f;

        tickRecharge(s, idx, nowMs);

        float cooldownSec = sanitizeTime(s.hotbarCooldownTimes[idx]);
        if (cooldownSec <= 0.0f) return 0.0f;

        long lockRemainingMs = Math.max(0L, s.hotbarCooldownUntilMs[idx] - nowMs);
        float lockRemainingSec = (lockRemainingMs > 0L) ? (lockRemainingMs / 1000.0f) : 0.0f;
        float ratio = lockRemainingSec / cooldownSec;

        if (ratio < 0.0f) ratio = 0.0f;
        if (ratio > 1.0f) ratio = 1.0f;
        return ratio;
    }

    private static void tickRecharge(AbilityHotbarState.State s, int idx, long nowMs) {
        if (s == null || idx < 0 || idx > 8) return;

        int max = s.hotbarMaxUses[idx];
        if (max <= 0) {
            s.hotbarLastUpdateMs[idx] = nowMs;
            return;
        }

        float rechargeSec = sanitizeTime(s.hotbarRechargeTimes[idx]);
        if (rechargeSec <= 0.0f) {
            s.hotbarLastUpdateMs[idx] = nowMs;
            return;
        }

        long last = s.hotbarLastUpdateMs[idx];
        if (last <= 0L) {
            s.hotbarLastUpdateMs[idx] = nowMs;
            return;
        }

        long deltaMs = nowMs - last;
        if (deltaMs <= 0L) return;

        if (s.hotbarRemainingUses[idx] >= max) {
            s.hotbarRechargeAccumulatorSec[idx] = 0.0;
            s.hotbarLastUpdateMs[idx] = nowMs;
            return;
        }

        double accumulator = s.hotbarRechargeAccumulatorSec[idx] + (deltaMs / 1000.0);
        while (accumulator + 1e-9 >= rechargeSec && s.hotbarRemainingUses[idx] < max) {
            s.hotbarRemainingUses[idx]++;
            accumulator -= rechargeSec;
        }

        s.hotbarRechargeAccumulatorSec[idx] = Math.max(0.0, accumulator);
        s.hotbarLastUpdateMs[idx] = nowMs;
    }

    private static boolean isLockedByCooldown(AbilityHotbarState.State s, int idx, long nowMs) {
        if (s == null || idx < 0 || idx > 8) return false;
        return nowMs < s.hotbarCooldownUntilMs[idx];
    }

    private static void applyPostUseCooldown(AbilityHotbarState.State s, int idx, long nowMs) {
        if (s == null || idx < 0 || idx > 8) return;

        float cooldownSec = sanitizeTime(s.hotbarCooldownTimes[idx]);
        if (cooldownSec <= 0.0f) return;

        long lockMs = (long) Math.max(0L, Math.round(cooldownSec * 1000.0f));
        s.hotbarCooldownUntilMs[idx] = nowMs + lockMs;
    }

    public static void InitializeSlotRuntime(AbilityHotbarState.State s, int idx) {
        if (s == null || idx < 0 || idx > 8) return;

        long nowMs = System.currentTimeMillis();

        int maxUses = s.hotbarMaxUses[idx];
        s.hotbarRemainingUses[idx] = (maxUses > 0) ? maxUses : 0;

        s.hotbarRechargeAccumulatorSec[idx] = 0.0;
        s.hotbarLastUpdateMs[idx] = nowMs;

        float cooldownSec = sanitizeTime(s.hotbarCooldownTimes[idx]);
        if (s.hotbarStartWithCooldown[idx] && cooldownSec > 0.0f) {
            long lockMs = (long) Math.max(0L, Math.round(cooldownSec * 1000.0f));
            s.hotbarCooldownUntilMs[idx] = nowMs + lockMs;
        } else {
            s.hotbarCooldownUntilMs[idx] = 0L;
        }
    }

    private static float sanitizeTime(float time) {
        if (Float.isNaN(time) || Float.isInfinite(time)) return 0.0f;
        return Math.max(0.0f, time);
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
