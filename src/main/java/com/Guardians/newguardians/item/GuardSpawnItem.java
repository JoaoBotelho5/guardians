package com.Guardians.newguardians.item;

import com.Guardians.newguardians.entity.Guard;
import com.Guardians.newguardians.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class GuardSpawnItem extends Item {

    public GuardSpawnItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());

        if (level instanceof ServerLevel serverLevel) {
            Guard guard = new Guard(ModEntities.GUARD.get(), serverLevel);
            guard.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
            guard.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.SPAWN_EGG, null);
            if (context.getPlayer() != null) {
                guard.setOwnerId(context.getPlayer().getUUID());
            }
            serverLevel.addFreshEntity(guard);

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }

        return InteractionResult.SUCCESS;
    }
}