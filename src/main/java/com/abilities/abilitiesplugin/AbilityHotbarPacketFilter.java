package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AbilityHotbarPacketFilter implements PlayerPacketFilter {

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    public AbilityHotbarPacketFilter(
            AbilityHotbarState state,
            AbilitySystem abilitySystem
    ) {
        this.state = state;
        this.abilitySystem = abilitySystem;
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
        long now = System.currentTimeMillis();

        // Keep HUD live-updating while enabled so cooldown overlays animate and recharges show up.
        if (s.enabled && now >= s.nextHudRefreshAtMs) {
            s.nextHudRefreshAtMs = now + 100L;
            world.execute(() -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) return;

                var s2 = state.get(playerRef.getUsername());
                if (!s2.enabled) return;

                HCA_AbilityApi.TickAllSlots(playerRef);
                abilitySystem.persistBoundRuntime(playerRef, store, ref, false);
                player.getHudManager().setCustomHud(playerRef, new AbilityHotbarHud(playerRef, state));
            });
        }

        // =========================================================
        // 1) SyncInteractionChains (Ability1 toggle + SwapFrom hotbar)
        // =========================================================
        if (packet instanceof SyncInteractionChains chains) {

            for (SyncInteractionChain chain : chains.updates) {
                if (!chain.initial) continue;

                // -------------------------
                // Ability1 (Q): toggle bar
                // -------------------------
                if (chain.interactionType.name().equalsIgnoreCase("Ability1")) {
                    CompletableFuture<Boolean> handled = new CompletableFuture<>();
                    world.execute(() -> {
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player == null) {
                            handled.complete(false);
                            return;
                        }

                        boolean hasBar = abilitySystem.refreshFromHeldWeapon(playerRef, store, ref);
                        var s2 = state.get(playerRef.getUsername());

                        if (!hasBar) {
                            if (s2.enabled) {
                                AbilityBarUtil.forceOff(state, player, playerRef);
                            }
                            handled.complete(false);
                            return;
                        }

                        s2.enabled = !s2.enabled;
                        if (s2.enabled) {
                            player.getHudManager().setCustomHud(playerRef, new AbilityHotbarHud(playerRef, state));
                        } else {
                            player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
                        }

                        handled.complete(true);
                    });

                    try {
                        return handled.get(250, TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                        return false;
                    }
                }

                // ---------------------------------------------------
                // SwapFrom: this is the REAL hotbar swap interaction
                // Intercept it while bar is enabled to prevent swapping
                // ---------------------------------------------------
                if (!s.enabled) continue;

                if (chain.interactionType == InteractionType.SwapFrom && chain.data != null) {
                    int original = chain.activeHotbarSlot;
                    int target = chain.data.targetSlot;

                    if (target < 0 || target > 8) continue;
                    if (original < 0 || original > 8) original = 0;

                    int slot1to9 = target + 1;
                    if (!abilitySystem.shouldConsumeHotbarInput(playerRef, slot1to9)) {
                        continue;
                    }

                    // Do everything on world thread
                    int finalOriginal = original;
                    world.execute(() -> {
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player == null) return;

                        var s2 = state.get(playerRef.getUsername());

                        // If this SwapFrom is our own correction echo, ignore it
                        long nowMsSwap = System.currentTimeMillis();
                        if (nowMsSwap <= s2.suppressNextSetActiveSlotUntilMs && target == s2.suppressNextSetActiveSlot) {
                            s2.suppressNextSetActiveSlot = -1;
                            s2.suppressNextSetActiveSlotUntilMs = 0;
                            return;
                        }

                        // Force server slot back to original (prevents actual item swap)
                        player.getInventory().setActiveHotbarSlot((byte) finalOriginal);

                        // Suppress the echo from the correction packet
                        s2.suppressNextSetActiveSlot = finalOriginal;
                        s2.suppressNextSetActiveSlotUntilMs = System.currentTimeMillis() + 250;

                        // Tell client "nope, stay on original"
                        playerRef.getPacketHandler().write(
                                new SetActiveSlot(Inventory.HOTBAR_SECTION_ID, finalOriginal)
                        );

                        // Trigger ability
                        abilitySystem.useSlot(playerRef, store, ref, world, slot1to9);
                    });

                    return true; // consume SwapFrom so hotbar doesn't change
                }
            }

            return false;
        }

        // =========================================================
        // 2) Fallback: SetActiveSlot interception (some builds send this)
        // =========================================================
        if (packet instanceof SetActiveSlot set) {

            if (!s.enabled) return false;

            int incomingSection = readInt(set,
                    "getSection", "section",
                    "getSectionId", "sectionId",
                    "getInventorySectionId", "inventorySectionId",
                    "getSectionID", "sectionID"
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
            if (!abilitySystem.shouldConsumeHotbarInput(playerRef, incomingSlot + 1)) return false;

            long nowMsSuppress = System.currentTimeMillis();
            if (nowMsSuppress <= s.suppressNextSetActiveSlotUntilMs && incomingSlot == s.suppressNextSetActiveSlot) {
                s.suppressNextSetActiveSlot = -1;
                s.suppressNextSetActiveSlotUntilMs = 0;
                return true;
            }

            final int pressed0to8 = incomingSlot;

            world.execute(() -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) return;

                var s2 = state.get(playerRef.getUsername());

                int original = player.getInventory().getActiveHotbarSlot();
                if (original < 0 || original > 8) original = 0;

                // suppress echo
                s2.suppressNextSetActiveSlot = original;
                s2.suppressNextSetActiveSlotUntilMs = System.currentTimeMillis() + 250;

                player.getInventory().setActiveHotbarSlot((byte) original);
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

        // methods
        for (String name : candidates) {
            try {
                Method m = c.getMethod(name);
                Object r = m.invoke(obj);
                if (r instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }

        // public fields
        for (String name : candidates) {
            try {
                Field f = c.getField(name);
                Object r = f.get(obj);
                if (r instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }

        // declared fields
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
