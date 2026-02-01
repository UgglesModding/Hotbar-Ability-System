package com.abilities.abilitiesplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.InputStream;

public class CombatAbilityPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ONE shared state instance
    private final AbilityHotbarState state = new AbilityHotbarState();

    private PacketFilter inboundFilter;

    public CombatAbilityPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log(
                "Loaded %s version %s",
                this.getName(),
                this.getManifest().getVersion().toString()
        );
    }

    @Override
    protected void setup() {

        WeaponRegistry weaponRegistry = new WeaponRegistry();

        // Load Hotbar's own pack FIRST, then flush any queued packs from other mods.
        try (InputStream pack = getClass().getClassLoader().getResourceAsStream("HCA/HCA_pack.json")) {
            if (pack == null) {
                System.out.println("[HotbarAbilities] Missing HCA/HCA_pack.json");
                return;
            }

            boolean ok = AbilityReceiver.activate(
                    weaponRegistry,
                    getClass().getClassLoader(),
                    pack,
                    "HotbarAbilities"
            );

            System.out.println("[HotbarAbilities] Activated ok=" + ok);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Init your API/state after registry is active
        CAO_AbilityApi.Init(state);

        AbilityInteractionExecutor interactionExecutor = new AbilityInteractionExecutor();

        // Ability system (uses registry + state)
        AbilitySystem abilitySystem = new AbilitySystem(weaponRegistry, state, interactionExecutor);

        AbilityDispatch.register(new CAO_DoAbility());

        // --- Commands ---
        this.getCommandRegistry().registerCommand(new AbilityToggleCommand(state, abilitySystem));
        this.getCommandRegistry().registerCommand(new AbilityDebugCommand(state));

        // --- Packet Filter ---
        inboundFilter = PacketAdapters.registerInbound(new AbilityHotbarPacketFilter(state, abilitySystem));
    }

    @Override
    protected void shutdown() {
        if (inboundFilter != null) {
            PacketAdapters.deregisterInbound(inboundFilter);
            inboundFilter = null;
        }
    }
}
