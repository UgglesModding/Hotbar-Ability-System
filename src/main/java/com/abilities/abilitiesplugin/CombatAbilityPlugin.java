package com.abilities.abilitiesplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.InputStream;

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

        // 1) Init hotbar systems that depend on state
        HCA_AbilityApi.Init(state);

        // ✅ 2) Auto-discover packs from other mods BEFORE activating
        ModPackAutoDiscovery.scanAndRegisterAllPacks();

        // 3) Activate receiver + load Hotbar's own pack
        try (InputStream pack = getClass().getClassLoader().getResourceAsStream("HCA/HCA_pack.json")) {
            if (pack == null) {
                System.out.println("[HotbarAbilities] Missing resource: src/main/resources/HCA/HCA_pack.json");
                return;
            }

            boolean ok = AbilityReceiver.activate(
                    weaponRegistry,
                    getClass().getClassLoader(), // ✅ fixed (ClassLoader expected)
                    pack,
                    "HotbarAbilities"
            );

            System.out.println("[HotbarAbilities] activate ok=" + ok);
            if (!ok) return;

        } catch (Throwable t) {
            System.out.println("[HotbarAbilities] Failed activating: " + t.getMessage());
            return;
        }

        // 4) Now build ability system
        AbilityInteractionExecutor interactionExecutor = new AbilityInteractionExecutor();
        AbilitySystem abilitySystem = new AbilitySystem(weaponRegistry, state, interactionExecutor);

        AbilityDispatch.register(new HCA_DoAbility());

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
