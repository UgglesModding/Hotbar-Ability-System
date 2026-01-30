package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AbilityHotbarPacketFilter implements PlayerPacketFilter {

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;
    private final WeaponRegistry weaponRegistry;

    public AbilityHotbarPacketFilter(
            AbilityHotbarState state,
            AbilitySystem abilitySystem,
            WeaponRegistry weaponRegistry
    ) {
        this.state = state;
        this.abilitySystem = abilitySystem;
        this.weaponRegistry = weaponRegistry;
    }

    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef ref) { super(ref); }
        @Override protected void build(@Nonnull UICommandBuilder ui) {}
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {

        var s = state.get(playerRef.getUsername());

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return false;

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        // =========================================================
        // 1) Ability1 (Q)
        //    If held item is ours => consume + toggle our HUD.
        //    Otherwise => let vanilla Ability1 continue.
        // =========================================================
        if (packet instanceof SyncInteractionChains chains) {
            for (SyncInteractionChain chain : chains.updates) {
                if (!chain.initial) continue;

                if (chain.interactionType.name().equalsIgnoreCase("Ability1")) {

                    String held = s.cachedHeldItemId; // MUST exist in your State

                    boolean isRegisteredWeapon =
                            held != null && !held.isBlank() &&
                                    ((weaponRegistry.getAbilityBarPath(held) != null) ||
                                            (weaponRegistry.getAbilitySlots(held) != null));

                    // Not ours => vanilla continues
                    if (!isRegisteredWeapon) return false;

                    // Ours => toggle on world thread
                    world.execute(() -> {
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player == null) return;

                        // refresh updates abilityBarUiPath + cachedHeldItemId
                        abilitySystem.refreshFromHeldWeapon(playerRef, store, ref);

                        var s2 = state.get(playerRef.getUsername());

                        // if missing ui, force off
                        if (s2.abilityBarUiPath == null || s2.abilityBarUiPath.isBlank()) {
                            s2.enabled = false;
                            player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
                            return;
                        }

                        s2.enabled = !s2.enabled;

                        if (s2.enabled) {
                            player.getHudManager().setCustomHud(playerRef, new AbilityHotbarHud(playerRef, state));
                        } else {
                            player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
                        }
                    });

                    // Consume so the item's Ability1 interaction doesn't fight our HUD
                    return true;
                }
            }
            return false;
        }

        // =========================================================
        // 2) While bar enabled: intercept SetActiveSlot -> press 1-9
        // =========================================================
        if (packet instanceof SetActiveSlot set) {

            if (!s.enabled) return false;

            int incomingSection = readInt(set,
                    "getSection", "section",
                    "getSectionId", "sectionId",
                    "getInventorySectionId", "inventorySectionId"
            );

            int incomingSlot = readInt(set,
                    "getSlot", "slot",
                    "getActiveSlot", "activeSlot",
                    "getSelectedSlot", "selectedSlot",
                    "getTargetSlot", "targetSlot"
            );

            if (incomingSection == Integer.MIN_VALUE || incomingSlot == Integer.MIN_VALUE) return false;
            if (incomingSection != Inventory.HOTBAR_SECTION_ID) return false;
            if (incomingSlot < 0 || incomingSlot > 8) return false;

            long now = System.currentTimeMillis();
            if (now <= s.suppressNextSetActiveSlotUntilMs && incomingSlot == s.suppressNextSetActiveSlot) {
                s.suppressNextSetActiveSlot = -1;
                s.suppressNextSetActiveSlotUntilMs = 0;
                return true;
            }

            final int pressed0to8 = incomingSlot;

            world.execute(() -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) return;

                // If they swapped away from our weapon while bar is open, close it
                var s2 = state.get(playerRef.getUsername());
                String held = s2.cachedHeldItemId;

                boolean isRegisteredWeapon =
                        held != null && !held.isBlank() &&
                                ((weaponRegistry.getAbilityBarPath(held) != null) ||
                                        (weaponRegistry.getAbilitySlots(held) != null));

                if (!isRegisteredWeapon) {
                    s2.enabled = false;
                    player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
                    return;
                }

                int original = player.getInventory().getActiveHotbarSlot();
                if (original < 0 || original > 8) original = 0;

                s2.suppressNextSetActiveSlot = original;
                s2.suppressNextSetActiveSlotUntilMs = System.currentTimeMillis() + 250;

                player.getInventory().setActiveHotbarSlot((byte) original);

                // If you ever get weird desync/kicks again, comment this out.
                playerRef.getPacketHandler().write(new SetActiveSlot(Inventory.HOTBAR_SECTION_ID, original));

                int slot1to9 = pressed0to8 + 1;
                abilitySystem.useSlot(playerRef, store, ref, world, slot1to9);
            });

            return true;
        }

        return false;
    }

    private static int readInt(Object obj, String... candidates) {
        if (obj == null) return Integer.MIN_VALUE;
        Class<?> c = obj.getClass();

        for (String name : candidates) {
            try {
                Method m = c.getMethod(name);
                Object r = m.invoke(obj);
                if (r instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }

        for (String name : candidates) {
            try {
                Field f = c.getField(name);
                Object r = f.get(obj);
                if (r instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }

        for (String name : candidates) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object r = f.get(obj);
                if (r instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }

        return Integer.MIN_VALUE;
    }
}
