package com.example.penbot.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MoveToIceGoal extends MoveToBlockGoal {
    private final PathfinderMob mob;

    public MoveToIceGoal(PathfinderMob mob, double speedModifier, int searchRange) {
        super(mob, speedModifier, searchRange, 1);
        this.mob = mob;
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    @Override
    public double acceptedDistance() {
        return 2.0D;
    }

    @Override
    public boolean canUse() {
        if (this.mob.getRandom().nextFloat() > 0.05F) {
            return false;
        }
        return super.canUse();
    }
}
