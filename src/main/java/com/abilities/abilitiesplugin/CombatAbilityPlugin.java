package com.abilities.abilitiesplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class CombatAbilityPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AbilityHotbarState state = new AbilityHotbarState();
    private PacketFilter inboundFilter;

    public CombatAbilityPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Loaded %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {

        WeaponRegistry weaponRegistry = new WeaponRegistry();
        HCA_AbilityApi.Init(state);

        ModPackScanner.loadAllPacks(weaponRegistry, getClass().getClassLoader());

        // Now build ability system
        AbilityInteractionExecutor interactionExecutor = new AbilityInteractionExecutor();
        AbilitySystem abilitySystem = new AbilitySystem(weaponRegistry, state, interactionExecutor);

        HcaExternalExecutorChain chain = new HcaExternalExecutorChain();
        chain.discover();

        AbilityDispatch.register(new HCA_DoAbility(chain));


        // Commands
        this.getCommandRegistry().registerCommand(new AbilityToggleCommand(state, abilitySystem));
        this.getCommandRegistry().registerCommand(new AbilityDebugCommand(state));

        // Packet filter
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
