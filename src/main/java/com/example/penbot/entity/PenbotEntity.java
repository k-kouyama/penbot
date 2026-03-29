package com.example.penbot.entity;

import com.example.penbot.entity.ai.MoveToIceGoal;
import com.example.penbot.llm.OllamaClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class PenbotEntity extends PathfinderMob {
    private static final String[] NAMES = {"ペン吉", "ペン太郎", "ペン子", "ピーちゃん", "銀河ペンギン", "皇帝ペンギンジュニア"};
    private int mutterTimer;

    public PenbotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.mutterTimer = this.random.nextInt(6000) + 6000; // 5 to 10 minutes (1 min = 1200 ticks)
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D);
    }

    @Override
    protected void registerGoals() {
        // Runs away when attacked (Panic)
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.5D));
        
        // Move towards water if nearby
        this.goalSelector.addGoal(2, new MoveToBlockGoal(this, 1.0D, 8) {
            @Override
            protected boolean isValidTarget(LevelReader level, BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.is(Blocks.WATER);
            }
        });

        // Move to Ice Goal
        this.goalSelector.addGoal(3, new MoveToIceGoal(this, 1.0D, 12));

        // Walking around randomly
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        // Looking at Player and Randomly
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        Random rand = new Random();
        String chosenName = NAMES[rand.nextInt(NAMES.length)];
        this.setCustomName(Component.literal(chosenName));
        this.setCustomNameVisible(true);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.mutterTimer--;
            if (this.mutterTimer <= 0) {
                this.mutterTimer = this.random.nextInt(6000) + 6000;
                this.speak("現在の状況について、何か短い独り言をつぶやいてください。");
            }
        }
    }

    public void speak(String prompt) {
        if (this.level().isClientSide) return;
        
        String myName = this.hasCustomName() ? this.getCustomName().getString() : "ペンギン";
        
        OllamaClient.generateResponse(prompt).thenAccept(reply -> {
            this.level().getServer().execute(() -> {
                Component message = Component.literal("<" + myName + "> " + reply);
                this.level().getServer().getPlayerList().broadcastSystemMessage(message, false);
            });
        });
    }
}
