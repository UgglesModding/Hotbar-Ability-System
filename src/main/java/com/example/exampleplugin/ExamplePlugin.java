package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class ExamplePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER =
            HytaleLogger.forEnclosingClass();

    // ONE shared state instance
    private final AbilityHotbarState state = new AbilityHotbarState();

    private PacketFilter inboundFilter;

    public ExamplePlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log(
                "Loaded %s version %s",
                this.getName(),
                this.getManifest().getVersion().toString()
        );
    }

    @Override
    protected void setup() {

        // --- Registries ---
        AbilityRegistry abilityRegistry = new AbilityRegistry();
        abilityRegistry.loadAllFromResources();

        WeaponRegistry weaponRegistry = new WeaponRegistry();
        weaponRegistry.loadAllFromResources();
        CAO_AbilityApi.Init(state);
        CAO_CommandApi.Init(this);

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

        LOGGER.atInfo().log("[CAO] ExamplePlugin setup complete");
    }

    @Override
    protected void shutdown() {
        if (inboundFilter != null) {
            PacketAdapters.deregisterInbound(inboundFilter);
            inboundFilter = null;
        }
    }
}
