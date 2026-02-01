package com.abilities.abilitiesplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.InputStream;

public class CombatAbilityPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER =
            HytaleLogger.forEnclosingClass();

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

        // init receiver so other mods can contribute later
        AbilityReceiver.init(weaponRegistry);

        // self-register OUR pack using OUR classloader
        try (InputStream pack = getClass().getClassLoader().getResourceAsStream("HCA/hca_pack.json")) {
            if (pack == null) {
                System.out.println("[HotbarAbilities] Missing HCA/hca_pack.json (cannot load internal weapons)");
            } else {
                AbilityReceiver.registerContributionPack(getClass().getClassLoader(), pack, "HotbarAbilities");
            }
        } catch (Throwable t) {
            System.out.println("[HotbarAbilities] Failed self-register pack: " + t.getMessage());
        }

        CAO_AbilityApi.Init(state);
        AbilityReceiver.init(weaponRegistry);

        AbilityInteractionExecutor interactionExecutor =
                new AbilityInteractionExecutor();

        // Ability system (uses plugin + root paths)
        AbilitySystem abilitySystem =
                new AbilitySystem(weaponRegistry, state, interactionExecutor);

        AbilityDispatch.register(new CAO_DoAbility());

        // --- Commands ---
        this.getCommandRegistry().registerCommand(
                new AbilityToggleCommand(state, abilitySystem)
        );
        this.getCommandRegistry().registerCommand(
                new AbilityDebugCommand(state)
        );

        // --- Packet Filter ---
        inboundFilter = PacketAdapters.registerInbound(
                new AbilityHotbarPacketFilter(state, abilitySystem)
        );

        //LOGGER.atInfo().log("[CAO] ExamplePlugin setup complete");
    }

    @Override
    protected void shutdown() {
        if (inboundFilter != null) {
            PacketAdapters.deregisterInbound(inboundFilter);
            inboundFilter = null;
        }
    }
}
