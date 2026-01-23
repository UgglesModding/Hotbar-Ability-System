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
                return abilityDaggerLeap(Data, Context);

            case "combat_abilities::trololol":
                return abilityTrololol(Data, Context);

            case "combat_abilities:fullreload":
                return abilityPower(Data, Context);

            case "combat_abilities:reloadrandom":
                return abilityRecharge(Data, Context);

            case "combat_abilities:shuffle":
                return abilityShuffle(Data, Context);

            case "combat_abilities:coinflip":
                return abilityCoinflip(Data, Context);

            case "combat_abilities:super_jump":
                return abilitySuperJump(Data, Context);

            case "combat_abilities:full_heal":
                return abilityFullHeal(Data, Context);

            case "combat_abilities:butter_fingers":
                return abilityButterFingers(Data, Context);

            default:
                return false;
        }
    }

    // ----------------------------
    // Ability functions (one per case)
    // ----------------------------

    private boolean abilityDaggerLeap(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.HasUsesLeft(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }

        // Spend only when we actually cast
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }

        Context.playerRef.sendMessage(Message.raw("[CAO] dagger_leap cast (movement API not wired yet)"));
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

    private boolean abilityPower(PackagedAbilityData Data, AbilityContext Context) {
        Context.playerRef.sendMessage(
                Message.raw("Power ability reserved for future system")
        );
        return true;
    }


    private boolean abilityRecharge(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }

        boolean ok = CAO_AbilityApi.AddUseToRandomAbility(Context.playerRef, Data.ID);
        Context.playerRef.sendMessage(Message.raw(
                ok ? "Recharge: +1 use added to a random ability" : "Recharge: no valid ability to refill"
        ));
        return true;
    }

    private boolean abilityShuffle(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("Out of uses: " + Data.ID));
            return true;
        }

        var s = ExamplePluginAccess.State(Context.playerRef);

        String[] pool = new String[9];
        int poolCount = 0;

        for (int i = 0; i < 9; i++) {
            String other = s.hotbarAbilityIds[i];
            if (other == null || other.isBlank()) continue;
            if (other.equalsIgnoreCase(Data.ID)) continue;
            pool[poolCount++] = other;
        }

        if (poolCount <= 0) {
            Context.playerRef.sendMessage(Message.raw("Shuffle: no pool"));
            return true;
        }

        for (int i = 0; i < 9; i++) {
            String cur = s.hotbarAbilityIds[i];
            if (cur == null || cur.isBlank()) continue;
            if (cur.equalsIgnoreCase(Data.ID)) continue;

            s.hotbarAbilityIds[i] = pool[rng.nextInt(poolCount)];
        }

        Context.playerRef.sendMessage(Message.raw("[CAO] Shuffle: abilities randomized"));
        return true;
    }

    private boolean abilityCoinflip(PackagedAbilityData data, AbilityContext ctx) {
        if (!CAO_AbilityApi.SpendUse(ctx.playerRef, data.ID)) {
            // out of uses
            return true;
        }

        boolean heads = rng.nextBoolean();

        if (heads) {
            ctx.playerRef.sendMessage(Message.raw("Lucky!"));
            CommandManager.get().handleCommand(ctx.playerRef, "heal");
        } else {
            ctx.playerRef.sendMessage(Message.raw("Unlucky!"));
            CommandManager.get().handleCommand(ctx.playerRef, "neardeath");
        }


        return true;
    }

    private boolean abilitySuperJump(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }

        Context.playerRef.sendMessage(Message.raw("[CAO] SuperJump (not wired yet: needs movement API)"));
        return true;
    }

    private boolean abilityFullHeal(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }

        Context.playerRef.sendMessage(Message.raw("[CAO] FullHeal (not wired yet: needs health API)"));
        return true;
    }

    private boolean abilityButterFingers(PackagedAbilityData Data, AbilityContext Context) {
        if (!CAO_AbilityApi.SpendUse(Context.playerRef, Data.ID)) {
            Context.playerRef.sendMessage(Message.raw("[CAO] Out of uses: " + Data.ID));
            return true;
        }

        Context.playerRef.sendMessage(Message.raw("[CAO] ButterFingers: (drop weapon not wired yet)"));
        return true;
    }
}
