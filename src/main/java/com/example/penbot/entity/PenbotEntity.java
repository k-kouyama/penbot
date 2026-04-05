package com.example.penbot.entity;

import com.example.penbot.PenbotMod;
import com.example.penbot.entity.ai.MoveToIceGoal;
import com.example.penbot.llm.LMStudioClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class PenbotEntity extends Animal {
    private static final String[] NAMES = { "ペン吉", "ぺん太郎", "ペン子", "ピーちゃん", "ペンペン", "南極の主", "氷の王", "魚泥棒" };
    private int chatTimer = 0;
    private int conversationCooldown = 0;
    private int delayedResponseTicks = -1;
    private String pendingResponsePrompt = "";
    private PenbotEntity pendingTarget = null;
    private int currentDepth = 0;
    private static final int MAX_CONVERSATION_DEPTH = 3;
    private String lastResponseId = null;

    public PenbotEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0f);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.5D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, PenbotEntity.class, 8.0F));
        this.goalSelector.addGoal(3, new MoveToIceGoal(this, 1.0D, 10));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(1, new BreedGoal(this, 1.0D));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
        String randomName = NAMES[new Random().nextInt(NAMES.length)];
        this.setCustomName(Component.literal(randomName));
        this.setCustomNameVisible(true);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.isAlive()) {
            chatTimer++;
            if (conversationCooldown > 0) {
                conversationCooldown--;
            }

            // 遅延返答の処理 (Game Thread で安全に実行)
            if (delayedResponseTicks > 0) {
                delayedResponseTicks--;
                if (delayedResponseTicks == 0) {
                    processDelayedResponse();
                }
            }

            // 独り言または他のペンギンとの会話開始
            if (chatTimer >= 400 + new Random().nextInt(800)) { // 20秒〜60秒おき
                chatTimer = 0;
                if (conversationCooldown <= 0 && new Random().nextFloat() < 0.3F) {
                    checkForNearbyPenbots();
                } else {
                    handleSoliloquy();
                }
            }
        }
    }

    private void checkForNearbyPenbots() {
        List<PenbotEntity> others = this.level().getEntitiesOfClass(PenbotEntity.class,
                this.getBoundingBox().inflate(8.0D));
        others.remove(this);
        if (!others.isEmpty()) {
            PenbotEntity target = others.get(new Random().nextInt(others.size()));
            initiateConversation(target);
        }
    }

    private void initiateConversation(PenbotEntity target) {
        this.conversationCooldown = 6000;
        String targetName = target.getCustomName() != null ? target.getCustomName().getString() : "仲間";
        String prompt = String.format("あなたはペンギンです。近くにいる仲間のペンギン「%s」に短く日本語で話しかけてください。", targetName);

        LMStudioClient.askStateful(prompt, this.lastResponseId, (response, nextId) -> {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    this.lastResponseId = nextId;
                    broadcastMessage(response);
                    target.receiveMessage(this, response, 1);
                });
            }
        });
    }

    public void receiveMessage(PenbotEntity sender, String message, int depth) {
        if (depth > MAX_CONVERSATION_DEPTH)
            return;
        this.conversationCooldown = 2000;
        this.currentDepth = depth;
        this.pendingTarget = sender;

        String senderName = sender.getCustomName() != null ? sender.getCustomName().getString() : "仲間";
        this.pendingResponsePrompt = String.format("あなたはペンギンです。仲間のペンギン「%s」から「%s」と話しかけられました。それに対して短く日本語で返答してください。",
                senderName, message);

        // 2〜4秒のランダムな遅延を設定
        this.delayedResponseTicks = 40 + new Random().nextInt(40);
    }

    private void processDelayedResponse() {
        if (pendingResponsePrompt.isEmpty() || pendingTarget == null)
            return;

        LMStudioClient.askStateful(pendingResponsePrompt, this.lastResponseId, (response, nextId) -> {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    this.lastResponseId = nextId;
                    broadcastMessage(response);
                    if (pendingTarget != null && pendingTarget.isAlive()) {
                        pendingTarget.receiveMessage(this, response, currentDepth + 1);
                    }
                    pendingResponsePrompt = "";
                    pendingTarget = null;
                });
            }
        });
    }

    public void speak(String context) {
        if (this.level().isClientSide())
            return;
        LMStudioClient.askStateful(context, this.lastResponseId, (response, nextId) -> {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    this.lastResponseId = nextId;
                    broadcastMessage(response);
                });
            }
        });
    }

    private void broadcastMessage(String message) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + this.getCustomName().getString() + "> " + message),
                    false);
        }
    }

    private void handleSoliloquy() {
        speak("あなたはペンギンです。独り言を日本語の一言でいってください。");
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return PENBOT_CREATE_HACK(serverLevel);
    }

    private AgeableMob PENBOT_CREATE_HACK(ServerLevel level) {
        PenbotEntity bot = PenbotMod.PENBOT.get().create(level, EntitySpawnReason.BREEDING);
        return bot;
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.COD);
    }
}
