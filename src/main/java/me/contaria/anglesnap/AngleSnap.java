package me.contaria.anglesnap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class ArrowTracker {
    private static final Map<UUID, List<NbtCompound>> playerArrowData = new HashMap<>();

    public ArrowTracker() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            UUID uuid = player.getUuid();
            ServerWorld world = player.getServerWorld();

            List<NbtCompound> arrows = new ArrayList<>();
            for (var entity : world.getEntitiesByClass(PersistentProjectileEntity.class, player.getBoundingBox().expand(128),
                    e -> e.getOwner() != null && e.getOwner().getUuid().equals(uuid))) {
                NbtCompound tag = new NbtCompound();
                entity.writeNbt(tag);
                arrows.add(tag);
                entity.discard();
            }

            playerArrowData.put(uuid, arrows);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            UUID uuid = player.getUuid();
            ServerWorld world = player.getServerWorld();

            if (playerArrowData.containsKey(uuid)) {
                for (NbtCompound tag : playerArrowData.get(uuid)) {
                    PersistentProjectileEntity arrow = new PersistentProjectileEntity(world, player);
                    arrow.readNbt(tag);
                    arrow.setOwner(player);
                    arrow.setPosition(player.getX(), player.getY(), player.getZ());
                    world.spawnEntity(arrow);
                }
                playerArrowData.remove(uuid);
            }
        });
    }
}
