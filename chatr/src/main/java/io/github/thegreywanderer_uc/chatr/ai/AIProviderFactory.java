package io.github.thegreywanderer_uc.chatr.ai;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating AI providers based on configuration.
 */
public class AIProviderFactory {
    
    private final JavaPlugin plugin;
    private final Map<String, AIProvider> providers = new HashMap<>();
    
    public AIProviderFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }
    
    /**
     * Reload configuration and reinitialize providers
     */
    public void reload() {
        providers.clear();
        
        var config = plugin.getConfig();
        int timeoutMs = config.getInt("ai.timeout-seconds", 30) * 1000;
        boolean useEnvVars = config.getBoolean("ai.use-environment-variables", false);
        
        // Get the unified endpoint
        String endpoint = config.getString("ai.endpoint", "http://localhost:1234");
        plugin.getLogger().info("[DEBUG] AIProviderFactory.reload() - endpoint from config: '" + endpoint + "'");
        
        // Create providers based on endpoint type
        if (isUrl(endpoint)) {
            // Direct URL - create generic OpenAI-compatible provider
            OpenAIProvider directProvider = new OpenAIProvider();
            directProvider.setBaseUrl(endpoint);
            directProvider.setApiKey(""); // No auth for direct URLs
            directProvider.setTimeoutMs(timeoutMs);
            providers.put("direct", directProvider);
            providers.put("lmstudio", directProvider);
            providers.put("ollama", directProvider);
        }
        
        // Always create cloud providers for NPC overrides
        createProviderProviders(timeoutMs, useEnvVars, config);
        
        plugin.getLogger().info("[DEBUG] AIProviderFactory.reload() - providers created: " + providers.keySet());
    }
    
    /**
     * Create providers for known provider names
     */
    private void createProviderProviders(int timeoutMs, boolean useEnvVars, org.bukkit.configuration.file.FileConfiguration config) {
        // Groq (OpenAI-compatible)
        OpenAIProvider groqProvider = new OpenAIProvider();
        groqProvider.setBaseUrl("https://api.groq.com/openai");
        groqProvider.setApiKey(getApiKey("groq", config, useEnvVars));
        groqProvider.setTimeoutMs(timeoutMs);
        providers.put("groq", groqProvider);
        
        // Mistral (OpenAI-compatible)
        OpenAIProvider mistralProvider = new OpenAIProvider();
        mistralProvider.setBaseUrl("https://api.mistral.ai");
        mistralProvider.setApiKey(getApiKey("mistral", config, useEnvVars));
        mistralProvider.setTimeoutMs(timeoutMs);
        providers.put("mistral", mistralProvider);
        
        // NVIDIA (OpenAI-compatible)
        OpenAIProvider nvidiaProvider = new OpenAIProvider();
        nvidiaProvider.setBaseUrl("https://integrate.api.nvidia.com");
        nvidiaProvider.setApiKey(getApiKey("nvidia", config, useEnvVars));
        nvidiaProvider.setTimeoutMs(timeoutMs);
        providers.put("nvidia", nvidiaProvider);
        
        // OpenRouter (OpenAI-compatible)
        OpenAIProvider openrouterProvider = new OpenAIProvider();
        openrouterProvider.setBaseUrl("https://openrouter.ai/api");
        openrouterProvider.setApiKey(getApiKey("openrouter", config, useEnvVars));
        openrouterProvider.setTimeoutMs(timeoutMs);
        providers.put("openrouter", openrouterProvider);
        
        // Gemini (unique format)
        GeminiProvider geminiProvider = new GeminiProvider();
        geminiProvider.setApiKey(getApiKey("gemini", config, useEnvVars));
        geminiProvider.setTimeoutMs(timeoutMs);
        providers.put("gemini", geminiProvider);
    }
    
    /**
     * Check if a string is a URL
     */
    private boolean isUrl(String endpoint) {
        return endpoint != null && (endpoint.startsWith("http://") || endpoint.startsWith("https://"));
    }
    
    /**
     * Get API key with priority: System Property -> Environment Variable -> Config File
     * @param provider The provider name (openai, groq, gemini, etc.)
     * @param config The plugin configuration
     * @param useEnvVars Whether to use environment variables/system properties
     * @return The API key or empty string if not found
     */
    private String getApiKey(String provider, org.bukkit.configuration.file.FileConfiguration config, boolean useEnvVars) {
        if (!useEnvVars) {
            // Use config file directly
            return config.getString("ai.api-keys." + provider, "");
        }
        
        // Priority order: System Property -> Environment Variable -> Config File
        
        // 1. Try system property first (highest priority)
        String sysPropKey = "chatr." + provider + ".api.key";
        String key = System.getProperty(sysPropKey);
        if (key != null && !key.isEmpty()) {
            plugin.getLogger().info("Using API key from system property: " + sysPropKey);
            return key;
        }
        
        // 2. Try environment variable
        String envVarKey = "CHATR_" + provider.toUpperCase() + "_API_KEY";
        key = System.getenv(envVarKey);
        if (key != null && !key.isEmpty()) {
            plugin.getLogger().info("Using API key from environment variable: " + envVarKey);
            return key;
        }
        
        // 3. Fall back to config file (lowest priority)
        key = config.getString("ai.api-keys." + provider, "");
        if (!key.isEmpty()) {
            plugin.getLogger().info("Using API key from config file for provider: " + provider);
        } else {
            plugin.getLogger().warning("No API key found for provider '" + provider + "' in system properties, environment variables, or config file!");
        }
        
        return key;
    }
    
    /**
     * Get a provider by name or URL
     * @param providerNameOrUrl The provider name (nvidia, openai, etc.) or direct URL
     * @return The provider, or null if not found
     */
    public AIProvider getProvider(String providerNameOrUrl) {
        if (providerNameOrUrl == null) return null;
        
        String key = providerNameOrUrl.toLowerCase();
        
        // First try direct lookup (for provider names)
        AIProvider provider = providers.get(key);
        if (provider != null) {
            return provider;
        }
        
        // If it's a URL, return the direct provider
        if (isUrl(providerNameOrUrl)) {
            return providers.get("direct");
        }
        
        return null;
    }
    
    /**
     * Get the default provider based on endpoint configuration
     */
    public AIProvider getDefaultProvider() {
        var config = plugin.getConfig();
        String endpoint = config.getString("ai.endpoint", "http://localhost:1234");
        plugin.getLogger().info("[DEBUG] getDefaultProvider() - endpoint: '" + endpoint + "'");
        
        if (isUrl(endpoint)) {
            plugin.getLogger().info("[DEBUG] getDefaultProvider() - using direct provider for URL");
            return providers.get("direct");
        } else {
            // Try the endpoint as a provider name
            AIProvider provider = providers.get(endpoint.toLowerCase());
            if (provider != null) {
                plugin.getLogger().info("[DEBUG] getDefaultProvider() - found provider for endpoint '" + endpoint + "': " + provider.getClass().getSimpleName());
                return provider;
            }
            // Fallback to local provider if endpoint provider not found
            plugin.getLogger().warning("Unknown AI provider '" + endpoint + "' requested, falling back to local provider");
            return providers.get("direct");
        }
    }
    
    /**
     * Get provider for an NPC based on its config
     * @param npcName The NPC name
     * @return The configured provider, or default if not specified
     */
    public AIProvider getProviderForNpc(String npcName) {
        var config = plugin.getConfig();
        
        // Check for NPC-specific endpoint in npc config folder
        java.io.File npcFolder = new java.io.File(plugin.getDataFolder(), "npcs/" + npcName);
        java.io.File npcConfigFile = new java.io.File(npcFolder, "config.yml");
        
        plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - checking file: " + npcConfigFile.getAbsolutePath());
        
        if (npcConfigFile.exists()) {
            plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - NPC config file exists");
            org.bukkit.configuration.file.YamlConfiguration npcConfig = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(npcConfigFile);
            
            // Check for unified endpoint first (new format: ai.endpoint)
            String endpoint = npcConfig.getString("ai.endpoint", null);
            plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - NPC ai.endpoint: '" + endpoint + "'");
            
            // Also check root level endpoint for backward compatibility
            if (endpoint == null) {
                endpoint = npcConfig.getString("endpoint", null);
                plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - NPC root endpoint: '" + endpoint + "'");
            }
            
            if (endpoint != null) {
                AIProvider provider = getProvider(endpoint);
                if (provider != null) {
                    plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - Found NPC-specific provider: " + provider.getClass().getSimpleName());
                    return provider;
                } else {
                    plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - NPC endpoint '" + endpoint + "' not found, falling back to default");
                }
            }
            
            // Backward compatibility: check for old provider + server-url setup
            String providerName = npcConfig.getString("provider", null);
            plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - NPC provider (legacy): '" + providerName + "'");
            if (providerName != null) {
                AIProvider provider = getProvider(providerName);
                if (provider != null) {
                    // Check for NPC-specific server URL override
                    String serverUrl = npcConfig.getString("server-url", null);
                    if (serverUrl != null && provider instanceof OpenAIProvider) {
                        // Create a copy with the custom URL
                        OpenAIProvider customProvider = new OpenAIProvider();
                        customProvider.setBaseUrl(serverUrl);
                        customProvider.setApiKey(((OpenAIProvider) provider).apiKey);
                        customProvider.setTimeoutMs(provider.timeoutMs);
                        plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - Created custom provider with URL: " + serverUrl);
                        return customProvider;
                    }
                    plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - Using legacy provider: " + provider.getClass().getSimpleName());
                    return provider;
                }
            }
        } else {
            plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - No NPC config file found");
        }
        
        // Fall back to default
        plugin.getLogger().info("[DEBUG] getProviderForNpc('" + npcName + "') - Using default provider");
        return getDefaultProvider();
    }
    
    /**
     * Check if a provider has valid configuration
     */
    public boolean isProviderConfigured(String providerName) {
        AIProvider provider = getProvider(providerName);
        if (provider == null) return false;
        
        // Local providers don't need API key
        if (providerName.equals("lmstudio") || providerName.equals("ollama")) {
            return true;
        }
        
        // Remote providers need API key
        return provider.apiKey != null && !provider.apiKey.isEmpty();
    }
    
    /**
     * Get list of available provider names
     */
    public java.util.Set<String> getAvailableProviders() {
        return providers.keySet();
    }
}
