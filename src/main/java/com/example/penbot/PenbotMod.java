package com.example.penbot;

import com.example.penbot.entity.PenbotEntity;
import com.example.penbot.entity.client.PenbotRenderer;
import com.example.penbot.llm.ChatManager;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(PenbotMod.MOD_ID)
public class PenbotMod {
    public static final String MOD_ID = "penbot";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<PenbotEntity>> PENBOT = ENTITIES.register("penbot",
            () -> EntityType.Builder.of(PenbotEntity::new, MobCategory.CREATURE)
                    .sized(1.0f, 2.0f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "penbot"))));

    public PenbotMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getContainer().getModBusGroup();
        
        // Register entities
        ENTITIES.register(modBusGroup);

        // Register entity attributes (using specific bus as it does not implement IModBusEvent)
        EntityAttributeCreationEvent.getBus(modBusGroup).addListener(ModEventSubscriber::onEntityAttributeCreation);

        // Register forge bus events (Chat, Commands)
        MinecraftForge.EVENT_BUS.register(new ForgeEventSubscriber());

        // Defer renderer registration to the appropriate event to avoid NullPointerException (Registry Object not present)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EntityRenderersEvent.RegisterRenderers.getBus(modBusGroup).addListener(ModEventSubscriber::onRegisterRenderers);
        }
    }

    public static class ModEventSubscriber {
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(PENBOT.get(), PenbotEntity.createAttributes().build());
        }

        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(PENBOT.get(), PenbotRenderer::new);
        }
    }

    public static class ForgeEventSubscriber {
        @SubscribeEvent
        public void onChat(ServerChatEvent event) {
            ChatManager.onPlayerChat(event);
        }

        @SubscribeEvent
        public void onCommandsRegister(RegisterCommandsEvent event) {
            event.getDispatcher().register(
                Commands.literal("penbot")
                    .requires(source -> {
                        if (source.permissions() instanceof net.minecraft.server.permissions.LevelBasedPermissionSet lbps) {
                            return lbps.level().id() >= 2;
                        }
                        return false;
                    })
                    .executes(context -> {
                        var source = context.getSource();
                        if (source.getLevel() instanceof ServerLevel serverLevel) {
                            PenbotEntity bot = PENBOT.get().create(serverLevel, EntitySpawnReason.COMMAND);
                            if (bot != null) {
                                bot.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
                                bot.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(bot.blockPosition()), EntitySpawnReason.COMMAND, null);
                                serverLevel.addFreshEntity(bot);
                                source.sendSuccess(() -> Component.literal("Penbotを召喚しました！ 名前: " + (bot.hasCustomName() ? bot.getCustomName().getString() : "?")), false);
                            }
                        }
                        return 1;
                    })
            );
        }
    }
}
