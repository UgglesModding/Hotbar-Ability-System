package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;

import java.util.List;

public class AbilitySystem {
    private static final String RUNTIME_META_KEY = "hca_ability_runtime";
    private static final int RUNTIME_META_VERSION = 1;

    private final WeaponRegistry weaponRegistry;
    private final AbilityHotbarState state;
    private final AbilityInteractionExecutor interactionExecutor;

    private static final class HeldItemRef {
        final boolean fromTools;
        final short slot;
        final ItemStack stack;

        HeldItemRef(boolean fromTools, short slot, ItemStack stack) {
            this.fromTools = fromTools;
            this.slot = slot;
            this.stack = stack;
        }
    }

    public AbilitySystem(
            WeaponRegistry weaponRegistry,
            AbilityHotbarState state,
            AbilityInteractionExecutor interactionExecutor
    ) {
        this.weaponRegistry = weaponRegistry;
        this.state = state;
        this.interactionExecutor = interactionExecutor;
    }

    public boolean refreshFromHeldWeapon(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        var s = state.get(playerRef.getUsername());

        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            s.fillAllEmpty();
            return false;
        }

        HeldItemRef held = getHeldItemRef(player);
        if (held == null || held.stack == null || held.stack.isEmpty()) {
            s.fillAllEmpty();
            return false;
        }

        String heldItemId = ItemIdUtil.normalizeItemId(held.stack.getItemId());
        if (heldItemId == null || heldItemId.isBlank()) {
            s.fillAllEmpty();
            return false;
        }

        // NEW: register via overrides if needed
        weaponRegistry.ensureRegistered(heldItemId);

        List<WeaponAbilitySlot> slots = weaponRegistry.getAbilitySlots(heldItemId);
        s.abilityBarUiPath = weaponRegistry.getAbilityBarPath(heldItemId);

        if (s.abilityBarUiPath == null || s.abilityBarUiPath.isBlank() || slots == null) {
            s.fillAllEmpty();
            return false;
        }

        for (int i = 0; i < 9; i++) {
            if (i < slots.size()) {
                WeaponAbilitySlot slot = slots.get(i);

                s.hotbarItemIds[i] = (slot == null) ? null : slot.Key;
                s.hotbarRootInteractions[i] = (slot == null) ? null : slot.RootInteraction;

                s.hotbarAbilityIds[i] = (slot == null) ? null : slot.ID;
                s.hotbarPluginFlags[i] = slot != null && slot.Plugin;
                s.hotbarConsumeFlags[i] = slot != null && slot.Consume;

                s.hotbarMaxUses[i] = (slot == null) ? 0 : slot.MaxUses;
                s.hotbarAbilityValues[i] = (slot == null) ? 0 : slot.AbilityValue;

                float power = 1.0f;
                if (slot != null && slot.PowerMultiplier > 0.0f) power = slot.PowerMultiplier;
                s.hotbarPowerMultipliers[i] = power;

                s.hotbarIcons[i] = (slot == null) ? null : slot.Icon;
                s.hotbarCooldownTimes[i] = (slot == null) ? 0.3f : slot.CooldownTime;
                s.hotbarRechargeTimes[i] = (slot == null) ? 1.0f : slot.RechargeTime;
                s.hotbarStartWithCooldown[i] = (slot == null) || slot.StartWithCooldown;

                HCA_AbilityApi.InitializeSlotRuntime(s, i);

            } else {
                s.hotbarItemIds[i] = null;
                s.hotbarRootInteractions[i] = null;

                s.hotbarAbilityIds[i] = null;
                s.hotbarPluginFlags[i] = false;
                s.hotbarConsumeFlags[i] = false;

                s.hotbarMaxUses[i] = 0;
                s.hotbarPowerMultipliers[i] = 1.0f;
                s.hotbarIcons[i] = null;
                s.hotbarRemainingUses[i] = 0;
                s.hotbarAbilityValues[i] = 0;
                s.hotbarCooldownTimes[i] = 0.3f;
                s.hotbarRechargeTimes[i] = 1.0f;
                s.hotbarStartWithCooldown[i] = true;
                s.hotbarCooldownUntilMs[i] = 0L;
                s.hotbarRechargeAccumulatorSec[i] = 0.0;
                s.hotbarLastUpdateMs[i] = 0L;
            }
        }

        s.selectedAbilitySlot = 1;
        s.boundToTools = held.fromTools;
        s.boundSlot = held.slot;
        s.boundItemId = heldItemId;

        loadRuntimeFromItem(s, held.stack);
        persistBoundRuntime(playerRef, store, entityRef, true);

        return true;
    }

    public void useSlot(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world, int slot1to9) {
        var s = state.get(playerRef.getUsername());

        if (slot1to9 < 1 || slot1to9 > 9) return;
        int slot0to8 = slot1to9 - 1;
        s.selectedAbilitySlot = slot1to9;

        boolean plugin = s.hotbarPluginFlags[slot0to8];
        boolean consume = s.hotbarConsumeFlags[slot0to8]; // FIXED

        String id = s.hotbarAbilityIds[slot0to8];
        String rootInteraction = s.hotbarRootInteractions[slot0to8];

        if (plugin) {
            if (id == null || id.isBlank()) return;
            HCA_AbilityApi.TickAllSlots(playerRef);

            String key = s.hotbarItemIds[slot0to8];
            int maxUses = s.hotbarMaxUses[slot0to8];
            int remainingUses = s.hotbarRemainingUses[slot0to8];
            float powerMultiplier = s.hotbarPowerMultipliers[slot0to8];
            int abilityValue = s.hotbarAbilityValues[slot0to8];

            PackagedAbilityData data = new PackagedAbilityData(
                    slot0to8,
                    key,
                    id,
                    maxUses,
                    powerMultiplier,
                    abilityValue,
                    rootInteraction,
                    remainingUses,
                    consume
            );

            world.execute(() -> {
                AbilityContext ctx = AbilityContext.from(
                        playerRef,
                        store,
                        ref,
                        world,
                        state,
                        abilityValue
                );

                if (!HCA_AbilityApi.SpendUse(ctx.PlayerRef, data.ID)) {
                    return;
                } //check for uses. If none, then stop logic
                persistBoundRuntime(playerRef, store, ref, true);

                boolean handled = AbilityDispatch.dispatch(data, ctx);
                if (!handled) { return;} else { //auto consumes charge if consume is true
                    HCA_AbilityApi.ConsumeChargeInHand(ctx, 1);
                };


                if (s.enabled && ctx.Player != null) {
                    ctx.Player.getHudManager().setCustomHud(
                            ctx.PlayerRef,
                            new AbilityHotbarHud(ctx.PlayerRef, state)
                    );
                }
            });

            return;
        }

        if (rootInteraction == null || rootInteraction.isBlank()) return;

        world.execute(() -> {
            boolean ok = interactionExecutor.execute(rootInteraction, playerRef, store, ref, world);

            Player player = store.getComponent(ref, Player.getComponentType());
            if (s.enabled && player != null) {
                player.getHudManager().setCustomHud(
                        playerRef,
                        new AbilityHotbarHud(playerRef, state)
                );
            }
        });
    }

    public boolean shouldConsumeHotbarInput(PlayerRef playerRef, int slot1to9) {
        if (slot1to9 < 1 || slot1to9 > 9) return false;
        var s = state.get(playerRef.getUsername());
        int idx = slot1to9 - 1;

        if (s.hotbarPluginFlags[idx]) {
            String id = s.hotbarAbilityIds[idx];
            return id != null && !id.isBlank();
        }

        String root = s.hotbarRootInteractions[idx];
        return interactionExecutor.canExecute(root);
    }

    public void persistBoundRuntime(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> entityRef, boolean force) {
        var s = state.get(playerRef.getUsername());
        long now = System.currentTimeMillis();
        if (!force && now < s.nextRuntimePersistAtMs) return;

        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) return;

        if (s.boundSlot < 0 || s.boundSlot > 8 || s.boundItemId == null || s.boundItemId.isBlank()) return;

        Inventory inv = player.getInventory();
        ItemContainer container = s.boundToTools ? inv.getTools() : inv.getHotbar();
        short slot = (short) s.boundSlot;
        ItemStack stack = container.getItemStack(slot);
        if (stack == null || stack.isEmpty()) return;

        String stackId = ItemIdUtil.normalizeItemId(stack.getItemId());
        if (stackId == null || !stackId.equalsIgnoreCase(s.boundItemId)) return;

        ItemStack updated = writeRuntimeToItem(stack, s);
        ItemStackSlotTransaction tx = container.setItemStackForSlot(slot, updated);
        if (tx != null && tx.succeeded()) {
            s.nextRuntimePersistAtMs = now + 1000L;
        }
    }

    private static HeldItemRef getHeldItemRef(Player player) {
        Inventory inv = player.getInventory();

        if (inv.usingToolsItem()) {
            short toolSlot = (short) inv.getActiveToolsSlot();
            ItemContainer tools = inv.getTools();
            ItemStack toolStack = tools.getItemStack(toolSlot);
            if (toolStack != null && !toolStack.isEmpty()) {
                return new HeldItemRef(true, toolSlot, toolStack);
            }
        }

        short hotbarSlot = (short) inv.getActiveHotbarSlot();
        ItemContainer hotbar = inv.getHotbar();
        ItemStack hotbarStack = hotbar.getItemStack(hotbarSlot);
        if (hotbarStack == null || hotbarStack.isEmpty()) return null;

        return new HeldItemRef(false, hotbarSlot, hotbarStack);
    }

    private static void loadRuntimeFromItem(AbilityHotbarState.State s, ItemStack stack) {
        if (s == null || stack == null || stack.isEmpty()) return;

        BsonDocument meta = stack.getMetadata();
        if (meta == null || !meta.containsKey(RUNTIME_META_KEY)) return;
        if (!meta.get(RUNTIME_META_KEY).isDocument()) return;

        BsonDocument runtime = meta.getDocument(RUNTIME_META_KEY);
        if (!runtime.containsKey("slots") || !runtime.get("slots").isArray()) return;

        BsonArray slots = runtime.getArray("slots");
        for (int i = 0; i < 9 && i < slots.size(); i++) {
            if (!slots.get(i).isDocument()) continue;
            BsonDocument slot = slots.get(i).asDocument();

            if (slot.containsKey("rem") && slot.get("rem").isInt32()) {
                int rem = slot.getInt32("rem").getValue();
                int max = s.hotbarMaxUses[i];
                if (max > 0) rem = Math.max(0, Math.min(max, rem));
                else rem = Math.max(0, rem);
                s.hotbarRemainingUses[i] = rem;
            }
            if (slot.containsKey("cd") && slot.get("cd").isInt64()) {
                s.hotbarCooldownUntilMs[i] = Math.max(0L, slot.getInt64("cd").getValue());
            }
            if (slot.containsKey("acc")) {
                if (slot.get("acc").isDouble()) s.hotbarRechargeAccumulatorSec[i] = Math.max(0.0, slot.getDouble("acc").getValue());
                else if (slot.get("acc").isInt32()) s.hotbarRechargeAccumulatorSec[i] = Math.max(0.0, slot.getInt32("acc").getValue());
                else if (slot.get("acc").isInt64()) s.hotbarRechargeAccumulatorSec[i] = Math.max(0.0, slot.getInt64("acc").getValue());
            }
            if (slot.containsKey("last") && slot.get("last").isInt64()) {
                s.hotbarLastUpdateMs[i] = Math.max(0L, slot.getInt64("last").getValue());
            }
        }
    }

    private static ItemStack writeRuntimeToItem(ItemStack stack, AbilityHotbarState.State s) {
        BsonArray slots = new BsonArray();
        for (int i = 0; i < 9; i++) {
            BsonDocument slot = new BsonDocument();
            slot.put("id", new BsonString(s.hotbarAbilityIds[i] == null ? "" : s.hotbarAbilityIds[i]));
            slot.put("rem", new BsonInt32(s.hotbarRemainingUses[i]));
            slot.put("cd", new BsonInt64(s.hotbarCooldownUntilMs[i]));
            slot.put("acc", new BsonDouble(s.hotbarRechargeAccumulatorSec[i]));
            slot.put("last", new BsonInt64(s.hotbarLastUpdateMs[i]));
            slots.add(slot);
        }

        BsonDocument runtime = new BsonDocument();
        runtime.put("v", new BsonInt32(RUNTIME_META_VERSION));
        runtime.put("slots", slots);

        return stack.withMetadata(RUNTIME_META_KEY, runtime);
    }
}
