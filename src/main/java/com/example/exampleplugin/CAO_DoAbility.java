package com.example.exampleplugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;

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

    private boolean abilityRandomTeleport(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.HasUsesLeft(Context.playerRef, Data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }
        // Spend only when we actually cast
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }
        float FullPower = Data.PowerMultiplier;

        float dx = (rng.nextFloat() * 2f - 1f) * FullPower;
        float dy = (rng.nextFloat() * 2f - 1f) * FullPower;
        float dz = (rng.nextFloat() * 2f - 1f) * FullPower;

        String cmd = "tp ~" + dx + " ~" + dy + " ~" + dz;

        CommandManager.get().handleCommand(Context.playerRef, cmd);
        return true;
    }

    private boolean abilityTrololol(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
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

        if (heads) {
            Context.playerRef.sendMessage(Message.raw("Lucky!"));
            CommandManager.get().handleCommand(Context.playerRef, "heal");
        } else {
            Context.playerRef.sendMessage(Message.raw("Unlucky!"));
            CommandManager.get().handleCommand(Context.playerRef, "neardeath");
        }
        return true;
    }

    private boolean abilityFullHeal(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            //Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }

        CommandManager.get().handleCommand(Context.playerRef, "heal");
        return true;
    }

    private boolean abilityEmpty(PackagedAbilityData Data, AbilityContext Context) {

        Context.playerRef.sendMessage(Message.raw("[CAO] slot marked as empty"));
        return true;
    }
}
