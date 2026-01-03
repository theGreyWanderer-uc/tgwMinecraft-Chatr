# Configuration

Chatr uses YAML configuration files. The main configuration is in `plugins/Chatr/config.yml`.

## Basic Setup

### AI Provider Configuration

Edit `config.yml` to set up your AI endpoint:

```yaml
ai:
  # Either a direct URL (LM Studio/Ollama) or a provider name
  # Direct URLs: "http://localhost:1234" (LM Studio), "http://localhost:11434" (Ollama)
  # Provider names: "groq", "mistral", "nvidia", "openrouter", "gemini" (NPCs only)
  endpoint: "http://localhost:1234"
  model: "your-model-name-here"
  temperature: 0.6
  max-tokens: 2000
  timeout-seconds: 30
```

### Security Settings

Control how API keys are loaded for maximum security:

```yaml
ai:
  # When true: Uses environment variables/system properties (recommended for production)
  # When false: Uses api-keys section below (simpler for development)
  use-environment-variables: false
```

### API Keys Configuration

#### Option 1: Config File (Development)
When `use-environment-variables: false`, add your API keys directly:

```yaml
ai:
  use-environment-variables: false
  api-keys:
    gemini: "your-gemini-key"
    groq: "gsk-your-groq-key"
    mistral: "your-mistral-key"
    nvidia: "nvapi-your-key"
    openrouter: "sk-or-your-key"
```

#### Option 2: Environment Variables (Production)
When `use-environment-variables: true`, keys are resolved in this order:

1. Java system property `chatr.<provider>.api.key`
2. Environment variable `CHATR_<PROVIDER>_API_KEY`
3. `ai.api-keys.<provider>` in `config.yml`

**Environment Variable Names:**
- `CHATR_GROQ_API_KEY`
- `CHATR_GEMINI_API_KEY`
- `CHATR_MISTRAL_API_KEY`
- `CHATR_NVIDIA_API_KEY`
- `CHATR_OPENROUTER_API_KEY`

**System Property Names:**
- `chatr.groq.api.key`
- `chatr.gemini.api.key`
- `chatr.mistral.api.key`
- `chatr.nvidia.api.key`
- `chatr.openrouter.api.key`

**Usage Examples:**

```bash
# Set environment variables
export CHATR_GROQ_API_KEY="gsk-your-key"
export CHATR_MISTRAL_API_KEY="your-key"

# Or use system properties when starting the server
java -Dchatr.groq.api.key=gsk-your-key -jar paper.jar
```

**Priority Order:** System Property → Environment Variable → Config File

## Free Tier Provider Examples

Here are tested configurations that work with free tier access to various AI providers.

### Gemini (Google AI)
```yaml
# Global config.yml
ai:
  endpoint: "gemini"
  model: "models/gemini-2.5-flash-lite"

# Or per-NPC: plugins/Chatr/npcs/<name>/config.yml
endpoint: "gemini"
model: "models/gemini-2.5-flash-lite"
```

### Groq
```yaml
# Global config.yml
ai:
  endpoint: "groq"
  model: "openai/gpt-oss-20b"

# Or per-NPC: plugins/Chatr/npcs/<name>/config.yml
endpoint: "groq"
model: "openai/gpt-oss-20b"
```

### Mistral
```yaml
# Global config.yml
ai:
  endpoint: "mistral"
  model: "mistral-medium-latest"

# Or per-NPC: plugins/Chatr/npcs/<name>/config.yml
endpoint: "mistral"
model: "mistral-medium-latest"
```

### NVIDIA
```yaml
# Global config.yml
ai:
  endpoint: "nvidia"
  model: "nvidia/llama-3.1-nemotron-nano-8b-v1"

# Or per-NPC: plugins/Chatr/npcs/<name>/config.yml
endpoint: "nvidia"
model: "nvidia/llama-3.1-nemotron-nano-8b-v1"
```

### OpenRouter
```yaml
# Global config.yml
ai:
  endpoint: "https://openrouter.ai/api/v1"
  model: "meta-llama/llama-3-8b-instruct"

# Or per-NPC: plugins/Chatr/npcs/<name>/config.yml
endpoint: "https://openrouter.ai/api/v1"
model: "meta-llama/llama-3-8b-instruct"
```

## ServerAI Configuration

The server-wide AI assistant has its own AI configuration that does **not** depend on the global `ai.*` settings. This ensures it works reliably even if you experiment with different NPC providers.

```yaml
server-ai:
  enabled: true
  name: "Heimdall"
  server-url: "http://localhost:1234"  # Independent of global ai.endpoint
  model: "local-model"                 # Independent of global ai.model
  chat-scan-interval-seconds: 60
  conversation-join-chance: 0.1
  max-chat-history: 25
  max-conversation-memory: 10
  max-tokens: 2000
```

## RAG System Setup

Enable the knowledge base system:

```yaml
server-ai:
  rag:
    enabled: true
    dataPath: "ragData"
    embeddingModel: "nomic-ai/nomic-embed-text-v1.5"
    lmStudioUrl: "http://localhost:1234"
    maxContextLength: 2000
```

**Note**: RAG requires LM Studio with the nomic-ai embedding model. See [[RAG-System]] for setup details.

## Rate Limiting

Control API usage and costs:

```yaml
rate-limit:
  enabled: true
  bypass-local: true              # Skip limits for localhost/LAN endpoints
  base-requests-per-minute: 10
  base-cooldown-seconds: 3        # Base cooldown between requests (seconds)
```

Permission-based tiers use `chatr.ratelimit.<tier>` permissions, for example:

```yaml
rate-limit:
  tiers:
    vip: 20        # players with chatr.ratelimit.vip
    premium: 30    # players with chatr.ratelimit.premium
    unlimited: -1  # players with chatr.ratelimit.unlimited (treated as unlimited)
```

## Response Caching

Cache identical requests to save API calls:

```yaml
cache:
  enabled: true
  ttl-seconds: 300  # 5 minutes
  max-size: 500
```

## Debug Mode

Enable detailed logging for troubleshooting:

```yaml
debug-mode: true
```

## Click-to-Chat Configuration

Configure right-click interaction with NPCs:

```yaml
click-to-chat:
  enabled: true
  interaction-distance: 5.0  # Max blocks from NPC to right-click
  timeout-seconds: 30        # Seconds before chat mode expires
  
  # Message colors for NPC interaction
  colors:
    npc-name: "&a"           # Color for [NPC1] (default: light green)
    instruction: "&e"        # Color for main text (default: yellow)
    cancel: "&7"             # Color for cancel instruction (default: gray)
```

Individual NPCs can override these global colors using the `/chatr color` command.

## Configuration Validation

After making changes:

1. Save the config file
2. Run `/chatr reload` (or restart server)
3. Check console for any configuration errors

## Configuration Files

Chatr creates these configuration files:

- `config.yml` - Main plugin configuration
- `npcs/npc-name/config.yml` - Individual NPC configurations (created when NPCs are made)

## See Also

- [[API-Providers|AI Provider Setup]]
- [[Context-Variables|Available Variables]]
- [[Troubleshooting|Configuration Issues]]