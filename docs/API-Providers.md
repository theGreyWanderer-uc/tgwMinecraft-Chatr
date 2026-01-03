# AI Providers

Chatr supports multiple AI providers for powering your NPCs and ServerAI.

## Supported Providers

### Local Providers

#### LM Studio (Default)
- **Type**: Local AI models
- **Default Port**: 1234
- **Setup**: Download and run LM Studio locally
- **Compatibility**: NPCs and ServerAI
- **Cost**: Free (local models)

Configuration:
```yaml
ai:
  endpoint: "http://localhost:1234"   # LM Studio local server
  model: "your-local-model-name"
```

#### Ollama
- **Type**: Local AI models
- **Default Port**: 11434
- **Setup**: Install Ollama and pull models
- **Compatibility**: NPCs and ServerAI
- **Cost**: Free (local models)

Configuration:
```yaml
ai:
  endpoint: "http://localhost:11434"   # Ollama local server
  model: "your-ollama-model"
```

### Cloud Providers

#### Groq
- **Type**: Fast inference
- **API**: OpenAI-compatible
- **Compatibility**: NPCs and ServerAI
- **Cost**: Pay per token

**Option 1: Config File (Development)**
```yaml
ai:
  use-environment-variables: false
  api-keys:
    groq: "gsk-your-groq-key"
```

**Option 2: Environment Variables (Production)**
```yaml
ai:
  use-environment-variables: true
  # Set: export CHATR_GROQ_API_KEY="gsk-your-key"
  # Or: java -Dchatr.groq.api.key=gsk-your-key -jar spigot.jar
```

#### Mistral
- **Type**: Open-source models
- **API**: OpenAI-compatible
- **Compatibility**: NPCs and ServerAI
- **Cost**: Pay per token

**Option 1: Config File (Development)**
```yaml
ai:
  use-environment-variables: false
  api-keys:
    mistral: "your-mistral-key"
```

**Option 2: Environment Variables (Production)**
```yaml
ai:
  use-environment-variables: true
  # Set: export CHATR_MISTRAL_API_KEY="your-key"
  # Or: java -Dchatr.mistral.api.key=your-key -jar spigot.jar
```

#### NVIDIA
- **Type**: Enterprise AI
- **API**: OpenAI-compatible
- **Compatibility**: NPCs and ServerAI
- **Cost**: Pay per token

**Option 1: Config File (Development)**
```yaml
ai:
  use-environment-variables: false
  api-keys:
    nvidia: "nvapi-your-key"
```

**Option 2: Environment Variables (Production)**
```yaml
ai:
  use-environment-variables: true
  # Set: export CHATR_NVIDIA_API_KEY="nvapi-your-key"
  # Or: java -Dchatr.nvidia.api.key=nvapi-your-key -jar spigot.jar
```

#### OpenRouter
- **Type**: Unified API access
- **API**: OpenAI-compatible
- **Compatibility**: NPCs and ServerAI
- **Cost**: Pay per token

**Option 1: Config File (Development)**
```yaml
ai:
  use-environment-variables: false
  api-keys:
    openrouter: "sk-or-your-key"
```

**Option 2: Environment Variables (Production)**
```yaml
ai:
  use-environment-variables: true
  # Set: export CHATR_OPENROUTER_API_KEY="sk-or-your-key"
  # Or: java -Dchatr.openrouter.api.key=sk-or-your-key -jar spigot.jar
```

#### Gemini
- **Type**: Google AI
- **API**: Google AI API
- **Compatibility**: NPCs only (not ServerAI)
- **Cost**: Free tier available

**Option 1: Config File (Development)**
```yaml
ai:
  use-environment-variables: false
  api-keys:
    gemini: "your-gemini-api-key"
```

**Option 2: Environment Variables (Production)**
```yaml
ai:
  use-environment-variables: true
  # Set: export CHATR_GEMINI_API_KEY="your-gemini-api-key"
  # Or: java -Dchatr.gemini.api.key=your-gemini-api-key -jar spigot.jar
```

## Free Tier Examples

Here are tested configurations that work with free tier access to various AI providers.

### Gemini (Google AI)
**Free tier available with generous limits**

NPC Configuration (`plugins/Chatr/npcs/<name>/config.yml`):
```yaml
endpoint: "gemini"
model: "models/gemini-2.5-flash-lite"
temperature: 0.7
max-tokens: 1000
```

### Groq
**Free tier with fast inference**

NPC Configuration:
```yaml
endpoint: "groq"
model: "openai/gpt-oss-20b"
temperature: 0.7
max-tokens: 1000
```

### Mistral
**Free tier available**

NPC Configuration:
```yaml
endpoint: "mistral"
model: "mistral-medium-latest"
temperature: 0.7
max-tokens: 1000
```

### NVIDIA
**Free tier with enterprise models**

NPC Configuration:
```yaml
endpoint: "nvidia"
model: "nvidia/llama-3.1-nemotron-nano-8b-v1"
temperature: 0.7
max-tokens: 1000
```

### OpenRouter
**Free tier with access to multiple models**

NPC Configuration:
```yaml
endpoint: "https://openrouter.ai/api/v1"
model: "meta-llama/llama-3-8b-instruct"
temperature: 0.7
max-tokens: 1000
```

### Local Options (Always Free)

#### LM Studio
```yaml
ai:
  endpoint: "http://localhost:1234"
  model: "your-local-model-name"
```

#### Ollama
```yaml
ai:
  endpoint: "http://localhost:11434"
  model: "your-ollama-model"
```

## Provider Selection

### Global Default
Set the default provider/endpoint in `config.yml`:

```yaml
ai:
  # Direct URL for local providers, or a provider name for cloud
  # Example: "http://localhost:1234", "groq", "mistral", "nvidia", "openrouter", "gemini"
  endpoint: "http://localhost:1234"
  model: "default-model"
  api-keys:
    # Add keys for cloud providers (groq, mistral, nvidia, openrouter, gemini)
```

### Per-NPC Override
Override the global endpoint for specific NPCs:

Create/adjust `plugins/Chatr/npcs/<name>/config.yml`:
```yaml
ai:
  endpoint: "gemini"              # Override global endpoint
  model: "models/gemini-2.5-flash-lite"
```

## ServerAI Compatibility

**Important**: ServerAI only works with OpenAI-compatible HTTP APIs:
- ✅ LM Studio, Ollama, Groq, Mistral, NVIDIA, OpenRouter
- ❌ Gemini (not compatible)

For Gemini support, use individual NPCs instead of ServerAI.

## Setup Instructions

### LM Studio Setup
1. Download LM Studio from [lmstudio.ai](https://lmstudio.ai)
2. Install and run LM Studio
3. Download a model (e.g., llama2, codellama)
4. Start the local server in LM Studio
5. Note the model name for configuration

### Other Cloud Providers
1. Sign up for the service (Groq, Mistral, NVIDIA, OpenRouter, Gemini)
2. Generate API keys
3. Add the key to Chatr configuration (`ai.api-keys.<provider>` or environment variables)
4. Test with a simple NPC interaction

## Testing Providers

Test your AI provider setup:

1. Create a test NPC: `/chatr create TestNPC`
2. Chat with it: `/chatr TestNPC ai Hello`
3. Check for responses and console errors
4. Use `/chatr stats` to monitor API usage

## Cost Management

### Local vs Cloud
- **Local providers** (LM Studio, Ollama): No ongoing costs
- **Cloud providers**: Pay per API call/token

### Monitoring Usage
- Use `/chatr stats` to track API usage
- Enable caching to reduce duplicate requests
- Set up rate limiting to control costs

### Cost Optimization
```yaml
# Enable caching
cache:
  enabled: true
  ttl-seconds: 300

# Rate limiting
rate-limit:
  enabled: true
  base-requests-per-minute: 10
```

## Troubleshooting

### Connection Issues
- **Local providers**: Ensure the server is running on the correct port
- **Cloud providers**: Check API keys and account status
- **Network**: Verify firewall settings allow connections

### Authentication Errors
- Double-check API keys for typos
- Ensure keys have proper permissions
- Check account status and billing

### Model Not Found
- Verify the model name matches exactly
- For local providers, ensure the model is loaded
- Check provider documentation for available models

## See Also

- [Provider Configuration](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/Configuration.html)
- [NPC-Specific Providers](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/NPCs.html)
- [Provider Issues](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/Troubleshooting.html)