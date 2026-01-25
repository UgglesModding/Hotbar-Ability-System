package com.abilities.abilitiesplugin;

import com.hypixel.hytale.builtin.hytalegenerator.fields.FastNoiseLite;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.commands.debug.server.ServerCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import org.jline.console.CommandInput;

import java.util.Random;

public class CAO_DoAbility implements IAbilityPlugin {

    private static final Random rng = new Random();

    @Override
    public boolean CAO_DoAbility(PackagedAbilityData Data, AbilityContext Context) {
        if (Data == null || Context == null || Context.playerRef == null) return false;
        if (Data.ID == null || Data.ID.isBlank()) return false;

        switch (Data.ID) {

            case "combat_abilities:randomteleport":
                return abilityRandomTeleport(Data, Context);

            case "combat_abilities::trololol":
                return abilityTrololol(Data, Context);

            case "combat_abilities:fullreload":
                return abilityFullReload(Data, Context);

            case "combat_abilities:reloadrandom":
                return abilityReloadRandom(Data, Context);

            case "combat_abilities:empty":
                return abilityEmpty(Data, Context);

            case "combat_abilities:coinflip":
                return abilityCoinflip(Data, Context);

            case "combat_abilities:full_heal":
                return abilityFullHeal(Data, Context);

            default:
                return false;
        }
    }

    // ----------------------------
    // Ability functions (one per case)
    // ----------------------------

    private boolean abilityRandomTeleport(PackagedAbilityData data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, data.ID)) {
            return true;
        }

        double fullPower = data.PowerMultiplier;

        // random in [-fullPower, +fullPower]
        double dx = (rng.nextDouble() * 2.0 - 1.0) * fullPower;
        double dy = (rng.nextDouble() * 2.0 - 1.0) * fullPower;
        double dz = (rng.nextDouble() * 2.0 - 1.0) * fullPower;

        Context.world.execute(() -> {
            TransformComponent transform =
                    Context.store.getComponent(Context.entityRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d curPos = transform.getPosition();
            Vector3d targetPos = curPos.add(new Vector3d(dx, dy, dz));

            Teleport teleport = Teleport.createForPlayer(
                    Context.world,
                    targetPos,
                    new Vector3f(0, 0, 0)
            );

            Context.store.addComponent(Context.entityRef, Teleport.getComponentType(), teleport);
        });

        return true;
    }


    private boolean abilityTrololol(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }
        Context.playerRef.sendMessage(Message.raw("Trolololololol"));
        return true;
    }


    private boolean abilityFullReload(PackagedAbilityData data, AbilityContext Context) {
        if (!CAO_AbilityApi.HasUsesLeft(Context.playerRef, data.ID)) return true;
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, data.ID)) return true;

        boolean didRefill = CAO_AbilityApi.RefillAllAbilitiesWithUses(Context.playerRef, data.ID);

        CAO_AbilityApi.UpdateHud(Context);

        return true;
    }

    private boolean abilityReloadRandom(PackagedAbilityData data, AbilityContext Context) {
        if (!CAO_AbilityApi.HasUsesLeft(Context.playerRef, data.ID)) return true;
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, data.ID)) return true;

        boolean didRefill = CAO_AbilityApi.RefillRandomAbilityWithUses(Context.playerRef, data.ID);

        CAO_AbilityApi.UpdateHud(Context);

        return true;
    }

    private boolean abilityCoinflip(PackagedAbilityData data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, data.ID)) {
            return true;
        }

        boolean heads = rng.nextBoolean();
        EntityStatMap EntityStatMapComponent = Context.store.getComponent(Context.entityRef, EntityStatMap.getComponentType());


        if (heads) {
            Context.playerRef.sendMessage(Message.raw("Lucky!"));
            int healthStat = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStatValue = EntityStatMapComponent.get(healthStat);
            EntityStatMapComponent.setStatValue(healthStat, healthStatValue.getMax());

        } else {
            Context.playerRef.sendMessage(Message.raw("Unlucky!"));
            int healthStat = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStatValue = EntityStatMapComponent.get(healthStat);
            EntityStatMapComponent.setStatValue(healthStat, 1);

        }
        return true;
    }

    private boolean abilityFullHeal(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }

        EntityStatMap EntityStatMapComponent = Context.store.getComponent(Context.entityRef, EntityStatMap.getComponentType());
        int healthStat = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStatValue = EntityStatMapComponent.get(healthStat);
        EntityStatMapComponent.setStatValue(healthStat, healthStatValue.getMax());

        return true;
    }

    private boolean abilityEmpty(PackagedAbilityData Data, AbilityContext Context) {

        Context.playerRef.sendMessage(Message.raw("[CAO] slot marked as empty"));
        return true;
    }
}
