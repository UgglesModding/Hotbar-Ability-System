package com.example.exampleplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class ExamplePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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
        AbilityRegistry registry = new AbilityRegistry();
        registry.loadAllFromResources();

        AbilitySystem abilitySystem = new AbilitySystem(registry, state);

        this.getCommandRegistry().registerCommand(new AbilityToggleCommand(state, abilitySystem));
        this.getCommandRegistry().registerCommand(new AbilityDebugCommand(state));
        this.getCommandRegistry().registerCommand(new LoadBarCommand(state, abilitySystem));
        this.getCommandRegistry().registerCommand(new GiveAbilityCommand(registry));


        inboundFilter = PacketAdapters.registerInbound(new AbilityHotbarPacketFilter(state));
    }



    @Override
    protected void shutdown() {
        if (inboundFilter != null) {
            PacketAdapters.deregisterInbound(inboundFilter);
            inboundFilter = null;
        }
    }
}
