package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public class AbilitySystem {

    private final WeaponRegistry weaponRegistry;
    private final AbilityHotbarState state;
    private final AbilityInteractionExecutor interactionExecutor;

    public AbilitySystem(
            WeaponRegistry weaponRegistry,
            AbilityHotbarState state,
            AbilityInteractionExecutor interactionExecutor
    ) {
        this.weaponRegistry = weaponRegistry;
        this.state = state;
        this.interactionExecutor = interactionExecutor;
    }

    public void refreshFromHeldWeapon(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        var s = state.get(playerRef.getUsername());

        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            s.fillAllEmpty();
            return;
        }

        String heldItemId = ItemIdUtil.normalizeItemId(getHeldItemId(player));
        if (heldItemId == null || heldItemId.isBlank()) {
            s.fillAllEmpty();
            return;
        }

        // NEW: register via overrides if needed
        weaponRegistry.ensureRegistered(heldItemId);

        List<WeaponAbilitySlot> slots = weaponRegistry.getAbilitySlots(heldItemId);
        s.abilityBarUiPath = weaponRegistry.getAbilityBarPath(heldItemId);

        if (s.abilityBarUiPath == null || s.abilityBarUiPath.isBlank() || slots == null) {
            s.fillAllEmpty();
            return;
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

                if (s.hotbarMaxUses[i] > 0) s.hotbarRemainingUses[i] = s.hotbarMaxUses[i];
                else s.hotbarRemainingUses[i] = 0;

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
            }
        }

        s.selectedAbilitySlot = 1;
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

    private static String getHeldItemId(Player player) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        byte active = player.getInventory().getActiveHotbarSlot();

        ItemStack stack = hotbar.getItemStack((short) active);
        if (stack == null) return null;

        return stack.getItemId();
    }
}
