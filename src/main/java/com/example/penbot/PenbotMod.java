package com.example.penbot;

import com.example.penbot.entity.PenbotEntity;
import com.example.penbot.entity.client.PenbotRenderer;
import com.example.penbot.llm.ChatManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(PenbotMod.MOD_ID)
public class PenbotMod {
    public static final String MOD_ID = "penbot";
    
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<EntityType<PenbotEntity>> PENBOT = ENTITIES.register("penbot",
            () -> EntityType.Builder.of(PenbotEntity::new, MobCategory.CREATURE)
                    .sized(1.0f, 2.0f)
                    .build("penbot"));

    public static final RegistryObject<Item> PENBOT_SPAWN_EGG = ITEMS.register("penbot_spawn_egg",
            () -> new SpawnEggItem(PENBOT.get(), 0x000000, 0xFFFFFF, new Item.Properties()));

    public PenbotMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        
        modEventBus.addListener(this::addCreative);
        
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(PENBOT_SPAWN_EGG);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventSubscriber {
        @SubscribeEvent
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(PENBOT.get(), PenbotEntity.createAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Note: In 1.20+, EntityRenderersEvent.RegisterRenderers is usually preferred over client setup
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEventSubscriber {
        @SubscribeEvent
        public static void onChat(ServerChatEvent event) {
            ChatManager.onPlayerChat(event);
        }

        @SubscribeEvent
        public static void onCommandsRegister(RegisterCommandsEvent event) {
            event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("penbot")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        net.minecraft.commands.CommandSourceStack source = context.getSource();
                        if (source.getEntity() != null) {
                            PenbotEntity bot = PENBOT.get().create(source.getLevel());
                            if (bot != null) {
                                bot.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
                                source.getLevel().addFreshEntity(bot);
                                source.sendSuccess(() -> Component.literal("Penbotを召喚しました！"), false);
                            }
                        }
                        return 1;
                    })
            );
        }
    }
}
