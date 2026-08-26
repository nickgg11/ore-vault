package com.orevault.orevault.event;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.zone.DisturbedZoneManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import java.util.UUID;

/**
 * Animus tree mob-reward effects for Disturbed Zone kills (design spec section 6.2):
 * Animus orbs, Reaper's Claim XP, Corrupted Veins raw ore, Plunderer's Share quantities,
 * Soul Harvest item drops, and kill statistics.
 */
public final class MobDeathHandler {
    private MobDeathHandler() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        UUID teamId = DisturbedZoneManager.zoneTeamOf(mob);
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        ServerPlayer killer = mob.getKillCredit() instanceof ServerPlayer sp ? sp : null;

        DisturbedZoneManager.onZoneMobKilled(level, teamId, mob, killer);

        // Reaper's Claim: extra XP orbs from zone mobs.
        int reaper = data.nodeTier("reapers_claim");
        if (reaper > 0) {
            double mult = switch (reaper) {
                case 1 -> 0.5;
                case 2 -> 1.0;
                default -> 1.5;
            };
            int bonus = (int) Math.round(mob.getExperienceReward(level, killer) * mult);
            if (bonus > 0) {
                net.minecraft.world.entity.ExperienceOrb.award(level, mob.position(), bonus);
            }
        }

        // Kill stats for a team-member killer.
        if (killer != null && teamId.equals(TeamHelper.teamIdFor(killer))) {
            var stats = data.statsFor(killer.getUUID());
            stats.addMob(mob.getType().getDescriptionId(), 1);
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        UUID teamId = DisturbedZoneManager.zoneTeamOf(mob);
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);

        // Plunderer's Share: increased item drop quantity.
        int plunder = data.nodeTier("plunderers_share");
        if (plunder > 0) {
            double mult = switch (plunder) {
                case 1 -> 0.25;
                case 2 -> 0.50;
                default -> 0.80;
            };
            for (ItemEntity drop : event.getDrops()) {
                int extra = (int) Math.floor(drop.getItem().getCount() * mult);
                if (extra > 0) {
                    drop.getItem().grow(extra);
                }
            }
        }

        // Corrupted Veins: chance of raw ore.
        int corrupted = data.nodeTier("corrupted_veins");
        if (corrupted > 0 && level.getRandom().nextFloat() < corruptedChance(corrupted)) {
            ItemStack ore = pickRawOre(level, corrupted >= 2, corrupted >= 3);
            if (!ore.isEmpty()) {
                event.getDrops().add(new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), ore));
            }
        }

        // Soul Harvest keystone.
        if (data.hasNode("soul_harvest") && level.getRandom().nextFloat() < 0.05F) {
            event.getDrops().add(new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    new ItemStack(ModItems.SOUL_HARVEST.get())));
        }
    }

    private static float corruptedChance(int tier) {
        return switch (tier) {
            case 1 -> 0.05F;
            case 2 -> 0.10F;
            default -> 0.15F;
        };
    }

    private static ItemStack pickRawOre(ServerLevel level, boolean includeUncommon, boolean includeRare) {
        java.util.List<ItemStack> pool = new java.util.ArrayList<>();
        pool.add(new ItemStack(Items.RAW_IRON));
        pool.add(new ItemStack(Items.RAW_COPPER));
        if (includeUncommon) {
            pool.add(new ItemStack(Items.RAW_GOLD));
            pool.add(new ItemStack(Items.LAPIS_LAZULI));
            pool.add(new ItemStack(Items.REDSTONE));
        }
        if (includeRare) {
            pool.add(new ItemStack(Items.DIAMOND));
            pool.add(new ItemStack(Items.EMERALD));
        }
        return pool.get(level.getRandom().nextInt(pool.size()));
    }
}

