package io.github.thegreywanderer_uc.chatr.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Google Gemini API provider.
 * Uses the generateContent endpoint with different payload structure.
 */
public class GeminiProvider extends AIProvider {
    
    public GeminiProvider() {
        this.baseUrl = "https://generativelanguage.googleapis.com";
    }
    
    public GeminiProvider(String apiKey) {
        this();
        this.apiKey = apiKey;
    }
    
    @Override
    public String getName() {
        return "gemini";
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
            // Clean model name - remove "models/" prefix if present
            String cleanModel = model.startsWith("models/") ? model.substring(7) : model;
            
            // Build request body in Gemini format
            JsonObject requestBody = new JsonObject();
            
            // System instruction
            JsonObject systemInstruction = new JsonObject();
            JsonArray systemParts = new JsonArray();
            JsonObject systemTextPart = new JsonObject();
            systemTextPart.addProperty("text", systemPrompt);
            systemParts.add(systemTextPart);
            systemInstruction.add("parts", systemParts);
            requestBody.add("system_instruction", systemInstruction);
            
            // Contents (conversation history + current message)
            JsonArray contents = new JsonArray();
            
            // Add history
            if (history != null) {
                for (Map<String, String> msg : history) {
                    JsonObject content = new JsonObject();
                    // Gemini uses "user" and "model" instead of "user" and "assistant"
                    String role = msg.get("role").equals("assistant") ? "model" : msg.get("role");
                    content.addProperty("role", role);
                    
                    JsonArray parts = new JsonArray();
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("text", msg.get("content"));
                    parts.add(textPart);
                    content.add("parts", parts);
                    
                    contents.add(content);
                }
            }
            
            // Add user message
            JsonObject userContent = new JsonObject();
            userContent.addProperty("role", "user");
            JsonArray userParts = new JsonArray();
            JsonObject userTextPart = new JsonObject();
            userTextPart.addProperty("text", userMessage);
            userParts.add(userTextPart);
            userContent.add("parts", userParts);
            contents.add(userContent);
            
            requestBody.add("contents", contents);
            
            // Generation config
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", temperature);
            generationConfig.addProperty("maxOutputTokens", maxTokens);
            requestBody.add("generationConfig", generationConfig);
            
            String jsonBody = gson.toJson(requestBody);
            String url = baseUrl + "/v1beta/models/" + cleanModel + ":generateContent?key=" + apiKey;
            
            Map<String, String> headers = new HashMap<>();
            // No Authorization header - key is in URL
            
            String response = httpPost(url, jsonBody, headers);
            
            // Parse response
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.has("candidates") && jsonResponse.getAsJsonArray("candidates").size() > 0) {
                JsonObject candidate = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject();
                if (candidate.has("content") && candidate.getAsJsonObject("content").has("parts")) {
                    JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
                    if (parts.size() > 0 && parts.get(0).getAsJsonObject().has("text")) {
                        String content = parts.get(0).getAsJsonObject().get("text").getAsString().trim();
                        return stripThinkingTags(content);
                    }
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
                // Clean model name - remove "models/" prefix if present
                String cleanModel = model.startsWith("models/") ? model.substring(7) : model;
                
                // Build request body in Gemini format
                JsonObject requestBody = new JsonObject();
                
                // System instruction
                JsonObject systemInstruction = new JsonObject();
                JsonArray systemParts = new JsonArray();
                JsonObject systemTextPart = new JsonObject();
                systemTextPart.addProperty("text", systemPrompt);
                systemParts.add(systemTextPart);
                systemInstruction.add("parts", systemParts);
                requestBody.add("system_instruction", systemInstruction);
                
                // Contents
                JsonArray contents = new JsonArray();
                
                // Add history
                if (history != null) {
                    for (Map<String, String> msg : history) {
                        JsonObject content = new JsonObject();
                        String role = msg.get("role").equals("assistant") ? "model" : msg.get("role");
                        content.addProperty("role", role);
                        
                        JsonArray parts = new JsonArray();
                        JsonObject textPart = new JsonObject();
                        textPart.addProperty("text", msg.get("content"));
                        parts.add(textPart);
                        content.add("parts", parts);
                        
                        contents.add(content);
                    }
                }
                
                // Add user message
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray userParts = new JsonArray();
                JsonObject userTextPart = new JsonObject();
                userTextPart.addProperty("text", userMessage);
                userParts.add(userTextPart);
                userContent.add("parts", userParts);
                contents.add(userContent);
                
                requestBody.add("contents", contents);
                
                // Generation config
                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("temperature", temperature);
                generationConfig.addProperty("maxOutputTokens", maxTokens);
                requestBody.add("generationConfig", generationConfig);
                
                String jsonBody = gson.toJson(requestBody);
                // Use streamGenerateContent endpoint
                String url = baseUrl + "/v1beta/models/" + cleanModel + ":streamGenerateContent?alt=sse&key=" + apiKey;
                
                Map<String, String> headers = new HashMap<>();
                
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
                            
                            try {
                                JsonObject chunk = gson.fromJson(data, JsonObject.class);
                                if (chunk.has("candidates") && chunk.getAsJsonArray("candidates").size() > 0) {
                                    JsonObject candidate = chunk.getAsJsonArray("candidates").get(0).getAsJsonObject();
                                    if (candidate.has("content") && candidate.getAsJsonObject("content").has("parts")) {
                                        JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
                                        if (parts.size() > 0 && parts.get(0).getAsJsonObject().has("text")) {
                                            String token = parts.get(0).getAsJsonObject().get("text").getAsString();
                                            
                                            // Handle thinking tags
                                            if (token.contains("<think>")) {
                                                inThinkingBlock = true;
                                                thinkingBuffer.append(token);
                                                continue;
                                            }
                                            if (inThinkingBlock) {
                                                thinkingBuffer.append(token);
                                                if (token.contains("</think>")) {
                                                    inThinkingBlock = false;
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
        }, "GeminiProvider-Stream").start();
    }
}
