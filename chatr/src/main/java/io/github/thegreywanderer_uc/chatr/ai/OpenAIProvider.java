package io.github.thegreywanderer_uc.chatr.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI-compatible API provider.
 * Works with: OpenAI, LM Studio, Ollama, Groq, Mistral, NVIDIA, OpenRouter
 */
public class OpenAIProvider extends AIProvider {
    
    public OpenAIProvider() {
        this.baseUrl = "http://localhost:1234"; // Default to LM Studio
    }
    
    public OpenAIProvider(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }
    
    @Override
    public String getName() {
        return "openai";
    }
    
    @Override
    public String chatCompletion(
            String model,
            String systemPrompt,
            String userMessage,
            List<Map<String, String>> history,
            double temperature,
            int maxTokens
    ) throws AIProviderException {
        try {
            // Build messages array
            JsonArray messages = new JsonArray();
            
            // System message
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
            
            // History messages
            if (history != null) {
                for (Map<String, String> msg : history) {
                    JsonObject historyMsg = new JsonObject();
                    historyMsg.addProperty("role", msg.get("role"));
                    historyMsg.addProperty("content", msg.get("content"));
                    messages.add(historyMsg);
                }
            }
            
            // User message
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);
            
            // Build request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxTokens);
            
            String jsonBody = gson.toJson(requestBody);
            String url = baseUrl + "/v1/chat/completions";
            
            Map<String, String> headers = new HashMap<>();
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.put("Authorization", "Bearer " + apiKey);
            }
            
            String response = httpPost(url, jsonBody, headers);
            
            // Parse response
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                JsonObject choice = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choice.has("message") && choice.getAsJsonObject("message").has("content")) {
                    String content = choice.getAsJsonObject("message").get("content").getAsString().trim();
                    return stripThinkingTags(content);
                }
            }
            
            throw new AIProviderException("Invalid response format");
            
        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AIProviderException("API call failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void chatCompletionStream(
            String model,
            String systemPrompt,
            String userMessage,
            List<Map<String, String>> history,
            double temperature,
            int maxTokens,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Exception> onError
    ) {
        new Thread(() -> {
            try {
                // Build messages array
                JsonArray messages = new JsonArray();
                
                // System message
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", systemPrompt);
                messages.add(systemMsg);
                
                // History messages
                if (history != null) {
                    for (Map<String, String> msg : history) {
                        JsonObject historyMsg = new JsonObject();
                        historyMsg.addProperty("role", msg.get("role"));
                        historyMsg.addProperty("content", msg.get("content"));
                        messages.add(historyMsg);
                    }
                }
                
                // User message
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userMessage);
                messages.add(userMsg);
                
                // Build request body with streaming
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.add("messages", messages);
                requestBody.addProperty("temperature", temperature);
                requestBody.addProperty("max_tokens", maxTokens);
                requestBody.addProperty("stream", true);
                
                String jsonBody = gson.toJson(requestBody);
                String url = baseUrl + "/v1/chat/completions";
                
                Map<String, String> headers = new HashMap<>();
                if (apiKey != null && !apiKey.isEmpty()) {
                    headers.put("Authorization", "Bearer " + apiKey);
                }
                
                HttpURLConnection conn = openStreamingConnection(url, jsonBody, headers);
                
                int statusCode = conn.getResponseCode();
                if (statusCode < 200 || statusCode >= 300) {
                    throw new AIProviderException("API returned status " + statusCode);
                }
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder thinkingBuffer = new StringBuilder();
                    boolean inThinkingBlock = false;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            
                            if (data.equals("[DONE]")) {
                                break;
                            }
                            
                            try {
                                JsonObject chunk = gson.fromJson(data, JsonObject.class);
                                if (chunk.has("choices") && chunk.getAsJsonArray("choices").size() > 0) {
                                    JsonObject choice = chunk.getAsJsonArray("choices").get(0).getAsJsonObject();
                                    if (choice.has("delta") && choice.getAsJsonObject("delta").has("content")) {
                                        String token = choice.getAsJsonObject("delta").get("content").getAsString();
                                        
                                        // Handle thinking tags in streaming
                                        if (token.contains("<think>")) {
                                            inThinkingBlock = true;
                                            thinkingBuffer.append(token);
                                            continue;
                                        }
                                        if (inThinkingBlock) {
                                            thinkingBuffer.append(token);
                                            if (token.contains("</think>")) {
                                                inThinkingBlock = false;
                                                // Extract any content after </think>
                                                String remaining = thinkingBuffer.toString();
                                                int endIdx = remaining.indexOf("</think>");
                                                if (endIdx >= 0 && endIdx + 8 < remaining.length()) {
                                                    onToken.accept(remaining.substring(endIdx + 8));
                                                }
                                                thinkingBuffer.setLength(0);
                                            }
                                            continue;
                                        }
                                        
                                        onToken.accept(token);
                                    }
                                }
                            } catch (Exception e) {
                                // Skip malformed chunks
                            }
                        }
                    }
                }
                
                onComplete.run();
                
            } catch (Exception e) {
                onError.accept(e);
            }
        }, "AIProvider-Stream").start();
    }
}
