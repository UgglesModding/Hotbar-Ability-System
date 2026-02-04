package com.abilities.abilitiesplugin;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.abilities.abilitiesplugin.HCA_AbilityApi.rng;

public class HCA_DoAbility implements IAbilityPlugin {

    private final HcaExternalExecutorChain externalChain;

    public HCA_DoAbility(HcaExternalExecutorChain externalChain) {
        this.externalChain = externalChain;
    }

    @Override
    public boolean HCA_DoAbility(PackagedAbilityData Data, AbilityContext Context) {
        if (Data == null || Context == null || Context.PlayerRef == null) return false;
        if (Data.ID == null || Data.ID.isBlank()) return false;

        switch (Data.ID) {

            case "combat_abilities:randomteleport":
                return abilityRandomTeleport(Data, Context);

            case "combat_abilities:trololol":
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

            case "combat_abilities:teleport_axis":
                return abilityTeleportAxis(Data, Context);

            case "combat_abilities:toggle_local_global":
                return abilityToggleLocalGlobal(Data,Context);

            case "combat_abilities:set_multiplication_power":
                return abilitySetMultiplicationPower(Data,Context);

            default:
                if (externalChain != null) {
                    return externalChain.tryExecute(Data, Context);
                }
                return false;
        }
    }


    // ----------------------------
    // Ability functions (one per case)
    // ----------------------------

    private boolean abilityRandomTeleport(PackagedAbilityData data, AbilityContext Context) {
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) {
            return true;
        }

        double fullPower = data.PowerMultiplier * Context.PowerMultiplier;


        double dx = (rng.nextDouble() * 2.0 - 1.0) * fullPower;
        double dy = (rng.nextDouble() * 2.0 - 1.0) * fullPower;
        double dz = (rng.nextDouble() * 2.0 - 1.0) * fullPower;

        Context.World.execute(() -> {
            TransformComponent transform =
                    Context.Store.getComponent(Context.EntityRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d curPos = transform.getPosition();

            Vector3d targetPos = curPos.add(new Vector3d(dx, dy, dz));

            Teleport teleport = Teleport.createForPlayer(
                    Context.World,
                    targetPos,
                    transform.getRotation()
            );

            Context.Store.addComponent(Context.EntityRef, Teleport.getComponentType(), teleport);
        });

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);

        return true;
    }

    private boolean abilityTeleportAxis(PackagedAbilityData data, AbilityContext Context)
    {
        if (HCA_AbilityApi.HasAbilityString(Context.PlayerRef, data.ID, "GrimoireToggle"))
        {
            return abilityLocalTeleportAxis(data, Context);
        }

        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) {
            return true;
        } // teleports the player in desired direction

        double fullPower = data.PowerMultiplier * Context.PowerMultiplier;

        int AV = Context.AbilityValue;
        if (AV == 0) return true;

        if (AV < 0) {
            fullPower *= -1;
            AV *= -1;
        }

        double dx = 0;
        double dy = 0;
        double dz = 0;

        switch (AV)
        {
            case 1: dx = fullPower;
            //Context.PlayerRef.sendMessage(Message.raw("X"));
            break; // x
            case 2: dy = fullPower;
            //Context.PlayerRef.sendMessage(Message.raw("Y"));
            break; // y
            case 3: dz = fullPower;
            //Context.PlayerRef.sendMessage(Message.raw("Z"));
            break; // z
            default: return true;
        }

        final double fdx = dx;
        final double fdy = dy;
        final double fdz = dz;

        Context.World.execute(() -> {
            TransformComponent transform =
                    Context.Store.getComponent(Context.EntityRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d curPos = transform.getPosition();
            Vector3d targetPos = curPos.add(new Vector3d(fdx, fdy, fdz));

            Teleport teleport = Teleport.createForPlayer(
                    Context.World,
                    targetPos,
                    transform.getRotation()
            );

            Context.Store.addComponent(Context.EntityRef, Teleport.getComponentType(), teleport);
        });

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }

    private boolean abilityLocalTeleportAxis(PackagedAbilityData data, AbilityContext Context)
    {
        //Context.PlayerRef.sendMessage(Message.raw("Local TP"));


        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) {
            return true;
        }

        double fullPower = data.PowerMultiplier * Context.PowerMultiplier;

        int AV = Context.AbilityValue;
        if (AV == 0) return true;

        int dir;
        if (AV < 0) {
            dir = -1;
            AV = -AV;
        } else {
            dir = 1;
        }

        final int axis = AV;
        final double amount = fullPower;

        Context.World.execute(() -> {
            TransformComponent transform =
                    Context.Store.getComponent(Context.EntityRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d curPos = transform.getPosition();
            Vector3f rot = Context.PlayerRef.getHeadRotation();

            double yaw = Math.toRadians(rot.z); //no yaw yet


            Vector3d forward = new Vector3d(
                    -Math.sin(yaw),
                    0.0,
                    Math.cos(yaw)
            );

            Vector3d right = new Vector3d(
                    Math.cos(yaw),
                    0.0,
                    Math.sin(yaw)
            );

            Vector3d up = new Vector3d(0, 1, 0);

            Vector3d offset;

            switch (axis) {
                case 1: // LEFT / RIGHT
                    offset = new Vector3d(
                            right.x * (-dir * amount),
                            0.0,
                            right.z * (-dir * amount)
                    );
                    break;

                case 3: // FORWARD / BACKWARD
                    offset = new Vector3d(
                            forward.x * (dir * amount),
                            0.0,
                            forward.z * (dir * amount)
                    );
                    break;

                case 2: // UP / DOWN
                    offset = new Vector3d(
                            0.0,
                            (dir * amount),
                            0.0
                    );
                    break;

                default:
                    return;
            }

            Vector3d targetPos = curPos.add(offset);

            Teleport teleport = Teleport.createForPlayer(
                    Context.World,
                    targetPos,
                    transform.getRotation()
            );

            Context.Store.addComponent(Context.EntityRef, Teleport.getComponentType(), teleport);
        });

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }



    private boolean abilityTrololol(PackagedAbilityData Data, AbilityContext Context) {
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, Data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }
        Context.PlayerRef.sendMessage(Message.raw("Trolololololol")); //ideal for testing

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }


    private boolean abilityFullReload(PackagedAbilityData data, AbilityContext Context) {
        if (!HCA_AbilityApi.HasUsesLeft(Context.PlayerRef, data.ID)) return true;
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) return true;

        HCA_AbilityApi.AbilitySlotInfo[] all = HCA_AbilityApi.GetAllAbilities(Context.PlayerRef);

        boolean changed = false;

        for (HCA_AbilityApi.AbilitySlotInfo info : all) {
            if (info == null) continue;
            if (info.id == null || info.id.isBlank()) continue;

            if (info.id.equalsIgnoreCase(data.ID)) continue; // ignore self
            if (info.maxUses <= 0) continue; // unlimited or no-uses slot

            if (info.remainingUses < info.maxUses) {
                info.remainingUses = info.maxUses;
                HCA_AbilityApi.SetSlotInformation(Context.PlayerRef, info);
                changed = true;
            }
        }

        if (changed) {
            HCA_AbilityApi.UpdateHud(Context);
        }

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }

    private boolean abilityReloadRandom(PackagedAbilityData data, AbilityContext Context) {
        if (!HCA_AbilityApi.HasUsesLeft(Context.PlayerRef, data.ID)) return true;
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) return true;

        HCA_AbilityApi.AbilitySlotInfo[] all = HCA_AbilityApi.GetAllAbilities(Context.PlayerRef);

        List<HCA_AbilityApi.AbilitySlotInfo> candidates = new ArrayList<>();
        for (HCA_AbilityApi.AbilitySlotInfo info : all) {
            if (info == null) continue;
            if (info.id == null || info.id.isBlank()) continue;

            if (info.id.equalsIgnoreCase(data.ID)) continue; // ignore self
            if (info.maxUses <= 0) continue;
            if (info.remainingUses >= info.maxUses) continue;

            candidates.add(info);
        }

        if (!candidates.isEmpty()) {
            int pick = HCA_AbilityApi.rng.nextInt(candidates.size());
            HCA_AbilityApi.AbilitySlotInfo chosen = candidates.get(pick);

            chosen.remainingUses = chosen.maxUses;
            HCA_AbilityApi.SetSlotInformation(Context.PlayerRef, chosen);

            HCA_AbilityApi.UpdateHud(Context);
        }

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }



    private boolean abilityCoinflip(PackagedAbilityData data, AbilityContext Context) {
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) {
            return true;
        }

        boolean heads = rng.nextBoolean();
        EntityStatMap EntityStatMapComponent = Context.Store.getComponent(Context.EntityRef, EntityStatMap.getComponentType());


        if (heads) {
            Context.PlayerRef.sendMessage(Message.raw("Lucky!"));
            int healthStat = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStatValue = EntityStatMapComponent.get(healthStat);
            EntityStatMapComponent.setStatValue(healthStat, healthStatValue.getMax());

        } else {
            Context.PlayerRef.sendMessage(Message.raw("Unlucky!"));
            int healthStat = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStatValue = EntityStatMapComponent.get(healthStat);
            EntityStatMapComponent.setStatValue(healthStat, 1);

        }
        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }

    private boolean abilityFullHeal(PackagedAbilityData Data, AbilityContext Context) {
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, Data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }

        EntityStatMap EntityStatMapComponent = Context.Store.getComponent(Context.EntityRef, EntityStatMap.getComponentType());
        int healthStat = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStatValue = EntityStatMapComponent.get(healthStat);
        EntityStatMapComponent.setStatValue(healthStat, healthStatValue.getMax());

        return true;
    }

    private boolean abilityEmpty(PackagedAbilityData Data, AbilityContext Context) {

        Context.PlayerRef.sendMessage(Message.raw("[CAO] slot marked as empty"));
        return true;
    }

    private boolean abilitySetMultiplicationPower(PackagedAbilityData data, AbilityContext Context)
    {
        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }
        float fullPower = data.PowerMultiplier * Context.PowerMultiplier;
        HCA_AbilityApi.SetPlayerPowerMultiplier(Context.PlayerRef, fullPower);

        HCA_AbilityApi.ConsumeChargeInHand(Context, 1);
        return true;
    }

    private boolean abilityToggleLocalGlobal(PackagedAbilityData data, AbilityContext Context)
    {

        if (!HCA_AbilityApi.SpendUse(Context.PlayerRef, data.ID)) {
        return true;
        }

       final String teleportAxisId = "combat_abilities:teleport_axis";

        if (HCA_AbilityApi.HasAbilityString(Context.PlayerRef, teleportAxisId, "GrimoireToggle"))
        {
        Context.PlayerRef.sendMessage(Message.raw("Teleports set to global axis"));
        HCA_AbilityApi.RemoveAbilityString(Context.PlayerRef, teleportAxisId, "GrimoireToggle");
        }
        else
        {
          Context.PlayerRef.sendMessage(Message.raw("Teleports set to local axis"));
          HCA_AbilityApi.AddAbilityString(Context.PlayerRef, teleportAxisId, "GrimoireToggle");
        }

       return true;
    }


    private static Vector3d normalizeSafe(Vector3d v) {
        double len = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (len <= 0.000001) return new Vector3d(0, 0, 0);
        return new Vector3d(v.x / len, v.y / len, v.z / len);
        }
}
