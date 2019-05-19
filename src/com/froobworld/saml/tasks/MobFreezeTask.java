package com.froobworld.saml.tasks;

import com.froobworld.saml.Config;
import com.froobworld.saml.Saml;
import com.froobworld.saml.Messages;
import com.froobworld.saml.utils.TpsSupplier;
import com.froobworld.saml.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MobFreezeTask implements Runnable {
    private Saml saml;
    private Config config;
    private Messages messages;
    private TpsSupplier tpsSupplier;

    public MobFreezeTask(Saml saml, Config config, Messages messages) {
        this.saml = saml;
        this.config = config;
        this.messages = messages;
        this.tpsSupplier = new TpsSupplier(saml);
    }

    @Override
    public void run() {
        Bukkit.getScheduler().scheduleSyncDelayedTask(saml, this, config.getLong("ticks-per-operation"));
        double tps = tpsSupplier.get();
        long startTime = System.currentTimeMillis();
        long maxOperationTime = config.getLong("maximum-operation-time");

        if(tps > config.getDouble("tps-unfreezing-threshold")) {
            int unfrozen = 0;
            double unfreezeLimit = config.getDouble("unfreeze-limit");
            for(World world : Bukkit.getWorlds()) {
                for(LivingEntity entity : world.getLivingEntities()) {
                    if(unfrozen >= unfreezeLimit) {
                        break;
                    }
                    if(!entity.hasAI()) {
                        unfrozen++;
                        entity.setAI(true);
                    }
                }
            }
            return;
        }
        double thresholdTps = config.getDouble("tps-freezing-threshold");
        if(tps > thresholdTps) {
            return;
        }
        MessageUtils.broadcastToOpsAndConsole(messages.getMessage("starting-freezing-operation")
                .replaceAll("%TPS", "" + tps)
                , config);
        int numberFrozen = 0;
        int totalFrozen = 0;
        int totalMobs = 0;
        boolean groupBias = config.getBoolean("group-bias");
        boolean smartScaling = config.getBoolean("use-smart-scaling");
        double baseMinimumSize = config.getDouble("group-minimum-size");
        double baseMaximumRadiusSq = Math.pow(config.getDouble("group-maximum-radius"), 2);

        double minimumSize = smartScaling ? baseMinimumSize * (1 - (thresholdTps - tps) / thresholdTps) : baseMinimumSize;
        double maximumRadiusSq = smartScaling ? baseMaximumRadiusSq / Math.pow((1 - (thresholdTps - tps) / thresholdTps), 2) : baseMaximumRadiusSq;

        boolean ignoreTamed = config.getBoolean("ignored-tamed");
        boolean ignoreLeashed = config.getBoolean("ignore-leashed");
        boolean ignoreLoveMode = config.getBoolean("ignore-love-mode");
        Set<String> neverFreeze = new HashSet<String>(config.getStringList("never-freeze"));
        Set<String> alwaysFreeze = new HashSet<String>(config.getStringList("always-freeze"));

        for(World world : Bukkit.getWorlds()) {
            if(System.currentTimeMillis() - startTime > maxOperationTime) {
                break;
            }
            if(config.getStringList("ignore-worlds").contains(world.getName())) {
                continue;
            }
            if(!groupBias) {
                for(LivingEntity entity : world.getLivingEntities()) {
                    totalMobs++;
                    if(neverFreeze.contains(entity.getType().name())) {
                        continue;
                    }
                    if(ignoreTamed && entity instanceof Tameable && ((Tameable) entity).getOwner() != null) {
                        continue;
                    }
                    if(ignoreLeashed && entity.isLeashed()) {
                        continue;
                    }
                    if(ignoreLoveMode && entity instanceof Animals && ((Animals) entity).isLoveMode()) {
                        continue;
                    }
                    if(entity.hasAI()) {
                        entity.setAI(false);
                        numberFrozen++;
                    }
                    totalFrozen++;
                }
                continue;
            }

            List<NeighbouredEntity> neighbouredEntities = new ArrayList<NeighbouredEntity>();
            for(LivingEntity entity : world.getLivingEntities()) {
                if(System.currentTimeMillis() - startTime > maxOperationTime) {
                    break;
                }
                totalMobs++;
                if(!entity.hasAI()) {
                    totalFrozen++;
                    continue;
                }
                if(neverFreeze.contains(entity.getType().name())) {
                    continue;
                }
                if(ignoreTamed && entity instanceof Tameable && ((Tameable) entity).getOwner() != null) {
                    continue;
                }
                if(ignoreLeashed && entity.isLeashed()) {
                    continue;
                }
                if(ignoreLoveMode && entity instanceof Animals && ((Animals) entity).isLoveMode()) {
                    continue;
                }
                NeighbouredEntity thisEntity = new NeighbouredEntity(entity);
                neighbouredEntities.add(thisEntity);
                if(alwaysFreeze.contains(entity.getType().name())) {
                    continue;
                }
                for(NeighbouredEntity otherEntity : neighbouredEntities) {
                    if(thisEntity.entity.getLocation().distanceSquared(otherEntity.entity.getLocation())  < maximumRadiusSq) {
                        thisEntity.addNeighbour(otherEntity);
                    }
                }
            }

            for(NeighbouredEntity neighbouredEntity : neighbouredEntities) {
                if(neighbouredEntity.neighbours.size() > minimumSize || alwaysFreeze.contains(neighbouredEntity.entity.getType().name())) {
                    for(NeighbouredEntity neighbour : neighbouredEntity.neighbours) {
                        if(neighbour.entity.hasAI()) {
                            neighbour.entity.setAI(false);
                            totalFrozen++;
                            numberFrozen++;
                        }
                    }
                }
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        MessageUtils.broadcastToOpsAndConsole(messages.getMessage("freezing-operation-complete")
                        .replaceAll("%TIME", "" + elapsedTime)
                        .replaceAll("%NUMBER_FROZEN", "" + numberFrozen)
                        .replaceAll("%TOTAL_FROZEN", "" + totalFrozen)
                        .replaceAll("%TOTAL_MOBS", "" + totalMobs)
                , config);
    }

    private class NeighbouredEntity {
        private LivingEntity entity;
        private Set<NeighbouredEntity> neighbours;

        public NeighbouredEntity(LivingEntity entity) {
            this.entity = entity;
            this.neighbours = new HashSet<NeighbouredEntity>();
            neighbours.add(this);
        }

        public void addNeighbour(NeighbouredEntity neighbour) {
            neighbours.add(neighbour);
            neighbour.neighbours.add(this);
        }
    }

}