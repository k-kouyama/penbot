package com.example.penbot.llm;

import com.example.penbot.entity.PenbotEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ServerChatEvent;

import java.util.List;

public class ChatManager {
    public static void onPlayerChat(ServerChatEvent event) {
        if (event.getPlayer() == null)
            return;

        String message = event.getMessage().getString();

        AABB searchArea = event.getPlayer().getBoundingBox().inflate(10.0D);
        List<PenbotEntity> bots = event.getPlayer().level().getEntitiesOfClass(PenbotEntity.class, searchArea);

        for (PenbotEntity bot : bots) {
            bot.speak(event.getPlayer().getName().getString() + "が次のように発言しました: 「" + message
                    + "」\nこれに対して短い相槌やリアクションを返してください。");
        }
    }
}
