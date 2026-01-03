package io.github.thegreywanderer_uc.chatr.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract AI provider interface for multi-provider support.
 * Implementations handle different API formats (OpenAI, Gemini, etc.)
 */
public abstract class AIProvider {
    
    protected final Gson gson = new Gson();
    protected String apiKey;
    protected String baseUrl;
    protected int timeoutMs = 30000;
    
    /**
     * Get the provider name for display/config
     */
    public abstract String getName();
    
    /**
     * Make a chat completion request
     * @param model The model name
     * @param systemPrompt The system prompt
     * @param userMessage The user's message
     * @param history Previous conversation messages (list of role/content maps)
     * @param temperature Generation temperature
     * @param maxTokens Maximum tokens to generate
     * @return The AI's response text
     */
    public abstract String chatCompletion(
            String model,
            String systemPrompt,
            String userMessage,
            List<Map<String, String>> history,
            double temperature,
            int maxTokens
    ) throws AIProviderException;
    
    /**
     * Make a streaming chat completion request
     * @param onToken Called for each token received
     * @param onComplete Called when complete
     * @param onError Called on error
     */
    public abstract void chatCompletionStream(
            String model,
            String systemPrompt,
            String userMessage,
            List<Map<String, String>> history,
            double temperature,
            int maxTokens,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Exception> onError
    );
    
    /**
     * Check if this provider supports streaming
     */
    public boolean supportsStreaming() {
        return true; // Most providers support SSE streaming
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getBaseUrl() {
        return this.baseUrl;
    }
    
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
    
    /**
     * Helper to make HTTP POST request
     */
    protected String httpPost(String url, String jsonBody, Map<String, String> headers) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoOutput(true);
        
        for (Map.Entry<String, String> header : headers.entrySet()) {
            conn.setRequestProperty(header.getKey(), header.getValue());
        }
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        
        int statusCode = conn.getResponseCode();
        InputStream is = (statusCode >= 200 && statusCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        
        String responseBody;
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                responseBody = sb.toString();
            }
        } else {
            responseBody = "No response body available";
        }
        
        if (statusCode >= 200 && statusCode < 300) {
            return responseBody;
        } else {
            throw new IOException("API returned status " + statusCode + ": " + responseBody);
        }
    }
    
    /**
     * Helper to open streaming connection
     */
    protected HttpURLConnection openStreamingConnection(String url, String jsonBody, Map<String, String> headers) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs * 2); // Longer timeout for streaming
        conn.setDoOutput(true);
        
        for (Map.Entry<String, String> header : headers.entrySet()) {
            conn.setRequestProperty(header.getKey(), header.getValue());
        }
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        
        return conn;
    }
    
    /**
     * Strip thinking tags from response
     */
    protected String stripThinkingTags(String content) {
        if (content == null) return null;
        
        // Remove complete thinking blocks
        content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        
        // Handle unclosed thinking tag
        if (content.startsWith("<think>")) {
            return null; // Response was only thinking
        }
        
        return content.isEmpty() ? null : content;
    }
    
    /**
     * Exception for AI provider errors
     */
    public static class AIProviderException extends Exception {
        public final int statusCode;
        public final String responseBody;
        
        public AIProviderException(String message) {
            super(message);
            this.statusCode = -1;
            this.responseBody = null;
        }
        
        public AIProviderException(String message, int statusCode, String responseBody) {
            super(message);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
        
        public AIProviderException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
            this.responseBody = null;
        }
    }
}
