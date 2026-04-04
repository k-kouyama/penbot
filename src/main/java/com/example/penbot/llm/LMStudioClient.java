package com.example.penbot.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LMStudioClient {
    private static final String API_URL = "http://127.0.0.1:1234/v1/chat/completions";
    private static final String MODEL = "google/gemma-3n-e4b";
    private static final String API_TOKEN = "sk-lm-azPxtK9O:YdJGEeo22fMUMgv8sU4s";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String SYSTEM_PROMPT = 
        "あなたはペンギンです。好物は魚です。アザラシは苦手です。独り言をいいます。" +
        "人の会話には好意的にリアクションします。気が弱く優しいですが短気です。" +
        "ときどき語尾に「ぺん」と言ってしまいます。返答は1回につき1文から2文程度の手短なものにしてください。";

    public static void ask(String context, Consumer<String> callback) {
        generateResponse(context).thenAccept(callback);
    }

    public static CompletableFuture<String> generateResponse(String messageContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", SYSTEM_PROMPT);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", messageContext);

                JsonArray messages = new JsonArray();
                messages.add(systemMsg);
                messages.add(userMsg);

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", MODEL);
                requestBody.add("messages", messages);
                requestBody.addProperty("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + API_TOKEN)
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    if (json.has("choices")) {
                        JsonArray choices = json.getAsJsonArray("choices");
                        if (choices.size() > 0) {
                            JsonObject choice = choices.get(0).getAsJsonObject();
                            if (choice.has("message")) {
                                return choice.getAsJsonObject("message").get("content").getAsString();
                            }
                        }
                    }
                }
                return "接続できません（ステータスコード: " + response.statusCode() + "）";
            } catch (Exception e) {
                return "接続できません（エラー: " + e.getMessage() + "）";
            }
        });
    }
}
