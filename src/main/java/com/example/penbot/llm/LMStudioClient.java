package com.example.penbot.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class LMStudioClient {
    private static final String API_URL = "http://192.168.0.17:1234/api/v1/chat";
    private static final String MODEL = "google/gemma-3n-e4b";
    private static final String API_TOKEN = "sk-lm-azPxtK9O:YdJGEeo22fMUMgv8sU4s";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String SYSTEM_PROMPT = "あなたはペンギンです。好物は魚です。アザラシは苦手です。独り言をいいます。" +
            "人の会話には好意的にリアクションします。気が弱く優しいですが短気です。" +
            "ときどき語尾に「ぺん」と言ってしまいます。返答は1回につき1文から2文程度の手短なものにしてください。";

    public static class LLMResponse {
        public final String content;
        public final String responseId;

        public LLMResponse(String content, String responseId) {
            this.content = content;
            this.responseId = responseId;
        }
    }

    /**
     * Stateless call (compat with old code)
     */
    public static void ask(String context, Consumer<String> callback) {
        generateResponse(context, null).thenAccept(res -> callback.accept(res.content));
    }

    /**
     * Stateful call
     */
    public static void askStateful(String prompt, String previousResponseId, BiConsumer<String, String> callback) {
        generateResponse(prompt, previousResponseId).thenAccept(res -> callback.accept(res.content, res.responseId));
    }

    public static CompletableFuture<LLMResponse> generateResponse(String prompt, String previousResponseId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", MODEL);
                
                // If it's a new conversation, prepend the system prompt
                String finalInput = (previousResponseId == null) ? SYSTEM_PROMPT + "\n\n" + prompt : prompt;
                requestBody.addProperty("input", finalInput);
                requestBody.addProperty("stream", false);
                
                if (previousResponseId != null) {
                    requestBody.addProperty("previous_response_id", previousResponseId);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + API_TOKEN)
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .build();

                System.out.println("[LMStudio] Sending request to " + API_URL + " (Stateful: " + (previousResponseId != null) + ")");
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("[LMStudio] Response received. Status code: " + response.statusCode());
                System.out.println("[LMStudio] Response body: " + response.body());

                if (response.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    String content = "";
                    
                    // Fix: Native API 0.4.x uses "output" array
                    if (json.has("output") && json.get("output").isJsonArray()) {
                        com.google.gson.JsonArray outputArr = json.getAsJsonArray("output");
                        if (outputArr.size() > 0) {
                            JsonObject first = outputArr.get(0).getAsJsonObject();
                            content = first.has("content") ? first.get("content").getAsString() : "";
                        }
                    } else if (json.has("content")) {
                        content = json.get("content").getAsString();
                    }
                    
                    String responseId = json.has("response_id") ? json.get("response_id").getAsString() : 
                                       (json.has("id") ? json.get("id").getAsString() : null);
                                       
                    return new LLMResponse(content, responseId);
                } else {
                    System.err.println("[LMStudio] Error status: " + response.statusCode() + " Body: " + response.body());
                    return new LLMResponse("（接続エラー: " + response.statusCode() + "）", null);
                }
            } catch (Exception e) {
                System.err.println("[LMStudio] Error during request: " + e.getMessage());
                e.printStackTrace();
                return new LLMResponse("（エラー: " + e.getMessage() + "）", null);
            }
        });
    }
}
