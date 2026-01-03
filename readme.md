# Chatr - AI-Powered Minecraft NPCs

Chatr is a sophisticated Minecraft AI plugin and NPC chat bot that brings intelligent conversational NPCs and a server-wide AI assistant to your Spigot/Paper server. Create AI-powered NPCs that can converse with players, answer questions, and provide helpful information about Minecraft. Features a server-wide AI assistant that monitors chat and helps players proactively.

## Table of Contents

- [Features](#features)
  - [🤖 AI-Powered NPCs](#-ai-powered-npcs)
  - [🛡️ Server AI Assistant](#️-server-ai-assistant)
  - [🧠 Advanced AI Features](#-advanced-ai-features)
  - [🔧 Technical Features](#-technical-features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Commands](#commands)
- [Permissions](#permissions)
- [API Usage & Costs](#api-usage--costs)
- [Troubleshooting](#troubleshooting)
- [License](#license)
- [Support](#support)
- [Changelog](#changelog)

## Features

### 🤖 AI-Powered NPCs
- Create NPCs with individual personalities and AI configurations
- Support for multiple AI providers (Groq, Mistral, NVIDIA, OpenRouter)
- Context-aware conversations with memory
- Click-to-chat functionality
- Customizable NPC skins and display names

### 🛡️ Server AI Assistant
- Server-wide AI assistant named "Heimdall" (configurable)
- Responds to chat mentions and proactively helps players
- Monitors chat for conflicts and mediates disputes
- Configurable chat scanning and conversation joining
- Advanced system prompt with context variables

### 🧠 Advanced AI Features
- **RAG (Retrieval-Augmented Generation)**: ServerAI can pull relevant information from a pre-built knowledge base to make responses more accurate and context-specific
- **Multi-Provider Support**: Choose from various AI providers for different NPCs
- **Conversation Memory**: Persistent chat history per player per NPC
- **Rate Limiting**: Prevent API abuse with configurable limits
- **Response Caching**: Cache identical requests to save API costs
- **Metrics & Analytics**: Track API usage and performance

### 🔧 Technical Features
- ProtocolLib integration for click-to-chat
- Gson for JSON processing
- Comprehensive logging and debug modes
- YAML configuration with extensive customization options
- Gradle build with paperweight plugin

## Installation

1. Download the latest `Chatr-1.0.0.jar` from releases
2. Place the JAR in your server's `plugins/` directory
3. **Full server restart recommended** for initial setup (or use `/reload` cautiously)
4. Configure the plugin in `plugins/Chatr/config.yml`

### Dependencies
- **Required**: Spigot/Paper 1.20+ (tested on 1.21) - Java Edition only
- **Required**: ProtocolLib (for click-to-chat feature)
- **Required**: AI server or API keys (LM Studio default port 1234, Ollama port 11434, or cloud provider API keys)
- **Optional**: For RAG system - Download `ragData.zip` from the repository's `ragData/` folder, unzip into `plugins/Chatr/` as `ragData/` folder, and configure in `config.yml`

## Quick Start

### 1. Basic Configuration
Edit `plugins/Chatr/config.yml`:

```yaml
# Set your AI endpoint (LM Studio is default)
ai:
  # Either a direct URL (LM Studio/Ollama) or a provider name
  # Direct URLs: "http://localhost:1234" (LM Studio), "http://localhost:11434" (Ollama)
  # Provider names: "groq", "mistral", "nvidia", "openrouter", "gemini" (NPCs only)
  endpoint: "http://localhost:1234"  # LM Studio default port
  model: "your-model-name-here"
  
  # Security: Use environment variables instead of config file keys (recommended for production)
  use-environment-variables: false
  
  # API keys for cloud providers (only used when use-environment-variables: false)
  api-keys:
    gemini: ""        # NPCs only, not ServerAI
    groq: ""          # gsk-your-key-here
    mistral: ""       # your-mistral-key
    nvidia: ""        # nvapi-your-key
    openrouter: ""    # sk-or-your-key
```

#### Security Options

**For Development (Simple):**
```yaml
ai:
  use-environment-variables: false
  api-keys:
    groq: "gsk-your-key-here"
```

**For Production (Secure):**
```yaml
ai:
  use-environment-variables: true
  # Set environment variables instead, e.g.:
  # CHATR_GROQ_API_KEY=gsk-your-key-here
  # CHATR_MISTRAL_API_KEY=...
  # CHATR_NVIDIA_API_KEY=...
  # CHATR_OPENROUTER_API_KEY=...
  # CHATR_GEMINI_API_KEY=...
  # Or use system properties: -Dchatr.groq.api.key=gsk-your-key-here
```

**Priority Order:** System Property → Environment Variable → Config File

### 2. Create Your First NPC
```
/chatr create <name>  # Creates NPC where you're standing
```

### 3. Configure the NPC (Optional)
Edit `plugins/Chatr/npcs/<name>/config.yml` to override default settings:

```yaml
enabled: true
provider: groq  # Override default provider (optional)
model: "llama3-8b-8192"    # Override default model (optional)
personality: "A friendly shopkeeper who sells enchanted items"
system-prompt: "You are a shopkeeper in a medieval village..."
```

*Note: NPC configuration is optional. NPCs will use global defaults if no specific config is provided.*

### 4. Enable Server AI (Optional)
In `config.yml`:

```yaml
server-ai:
  enabled: true
  name: "Heimdall"
```

## Configuration

Variables like `{player_name}` or `{server_time}` can be used in system prompts to personalize responses with real-time context.

### Available Context Variables

The following variables can be used in system prompts. ServerAI and NPCs support slightly different sets.

**Common Variables:**
- `{player_name}` - The player's username
- `{player_biome}` - The biome the player is currently in

**ServerAI Variables:**
- `{name}` - The ServerAI's name
- `{server_time}` - Current server time in 24-hour format (e.g., "14:30")
- `{server_weather}` - Current server weather (clear/rain/thunderstorm)
- `{player_time}` - Player's personal time in 24-hour format
- `{player_weather}` - Weather in player's world
- `{player_x}`, `{player_y}`, `{player_z}` - Player coordinates

**NPC Variables:**
- `{npc_name}` - The NPC's name
- `{time}` - Time of day (morning/day/dusk/night/dawn)
- `{time_exact}` - Time in 24-hour format (HH:MM)
- `{weather}` - Current weather (clear/rain/thunderstorm)
- `{biome}` - Biome where NPC is located
- `{world}` - World name
- `{player_health}`, `{player_max_health}` - Player health values
- `{player_hunger}` - Player hunger level
- `{player_level}` - Player XP level
- `{player_gamemode}` - Player game mode

See the [Context-Variables page](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/Context-Variables.html) for complete details.

### AI Providers
Chatr supports multiple AI options:

- **LM Studio** (default): Local AI models over HTTP (OpenAI-compatible)
- **Ollama**: Local AI models over HTTP (OpenAI-compatible)
- **Groq**: Fast cloud inference (OpenAI-compatible)
- **Mistral**: Open-source models (OpenAI-compatible)
- **NVIDIA**: Enterprise AI (OpenAI-compatible)
- **OpenRouter**: Unified API access (OpenAI-compatible)
- **Gemini**: Google AI (NPCs only, not ServerAI)

### Free Tier Examples

Here are tested configurations that work with free tier access to various AI providers:

#### Gemini (Google AI)
```yaml
# NPC Configuration: plugins/Chatr/npcs/<name>/config.yml
endpoint: "gemini"
model: "models/gemini-2.5-flash-lite"
```

#### Groq
```yaml
# NPC Configuration: plugins/Chatr/npcs/<name>/config.yml
endpoint: "groq"
model: "openai/gpt-oss-20b"
```

#### Mistral
```yaml
# NPC Configuration: plugins/Chatr/npcs/<name>/config.yml
endpoint: "mistral"
model: "mistral-medium-latest"
```

#### NVIDIA
```yaml
# NPC Configuration: plugins/Chatr/npcs/<name>/config.yml
endpoint: "nvidia"
model: "nvidia/llama-3.1-nemotron-nano-8b-v1"
```

#### OpenRouter
```yaml
# NPC Configuration: plugins/Chatr/npcs/<name>/config.yml
endpoint: "https://openrouter.ai/api/v1"
model: "meta-llama/llama-3-8b-instruct"
```

### ServerAI vs NPCs
- **ServerAI**: Server-wide assistant, uses OpenAI-compatible HTTP APIs (LM Studio, Ollama, Groq, Mistral, NVIDIA, OpenRouter). Gemini is **not** supported for ServerAI.
- **NPCs**: Individual characters, support all providers above including Gemini

### Key Settings

#### Global AI Settings
```yaml
ai:
  # Default endpoint for all NPCs
  # Direct URL (LM Studio/Ollama) or provider name (groq, mistral, nvidia, openrouter, gemini)
  endpoint: "http://localhost:1234"  # LM Studio (default), Ollama: 11434
  model: "your-model-name-here"
  temperature: 0.6
  max-tokens: 2000
  timeout-seconds: 30
```

#### ServerAI Configuration
```yaml
server-ai:
  enabled: false
  name: "Heimdall"
  chat-scan-interval-seconds: 60
  conversation-join-chance: 0.1
  max-tokens: 2000
```

#### RAG System
```yaml
server-ai:
  rag:
    enabled: false
    dataPath: "ragData"
    embeddingModel: "nomic-ai/nomic-embed-text-v1.5"
    lmStudioUrl: "http://localhost:1234"
    maxContextLength: 2000
```

**Note**: The RAG embedding model requires LM Studio and the nomic-ai model:
- Download from LM Studio: Search for "nomic-ai/nomic-embed-text-v1.5-GGUF"
- Or download from HuggingFace: https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF
- Load the model in LM Studio and ensure it's accessible at the configured URL

#### Click-to-Chat Configuration
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

**Note**: When NPC colors are adjusted using `/chatr color <npc> <type> [code]`, the custom colors are saved to `npcs.yml` under the NPC's ID for persistence across server restarts.

## Commands

### Admin Commands
- `/chatr version` - Show plugin version
- `/chatr reload` - Reload configuration
- `/chatr create <name>` - Create NPC at your location
- `/chatr remove <name>` - Remove NPC
- `/chatr skin <npc> <player>` - Set NPC skin
- `/chatr color <npc> <type> [code]` - Set NPC interaction message colors
- `/chatr reload-npc <name>` - Reload NPC config
- `/chatr info` - Show debug information about NPCs
- `/chatr <npc> ai <message>` - Chat with NPC (admin only)
- `/chatr stats` - View API usage statistics
- `/chatr cache [stats|clear]` - Manage response cache
- `/chatr serverai [status|refresh|clear]` - Manage ServerAI

### Player Commands
- `/chatr r <message>` - Reply to last NPC
- `/chatr clear <npc>` - Clear conversation history

**Note**: NPC names in commands are case-sensitive. Use the exact name as specified when creating the NPC.

## Permissions

- `chatr.admin` - Full plugin access
- `chatr.create` - Create NPCs
- `chatr.remove` - Remove NPCs
- `chatr.skin` - Change NPC skins
- `chatr.color` - Change NPC interaction message colors
- `chatr.reload` - Reload configurations
- `chatr.ai` - Reply to NPCs (use /chatr r)
- `chatr.ratelimit.vip` - Higher rate limits
- `chatr.ratelimit.premium` - Premium rate limits
- `chatr.ratelimit.unlimited` - No rate limits

## API Usage & Costs

Chatr includes comprehensive rate limiting and caching to manage API costs:

- **Rate Limiting**: Configurable requests per minute per player
- **Response Caching**: 5-minute cache for identical requests
- **Metrics**: Track usage by provider and NPC
- **Local Bypass**: Skip rate limits for local AI servers

## Troubleshooting

### Common Issues

**NPCs not responding:**
- Check AI server is running and accessible
- Verify API keys are set correctly
- Check server logs for connection errors

**Unknown provider warnings:**
- If you see "Unknown AI provider 'xyz' requested, falling back to local provider"
- The plugin automatically falls back to local AI (LM Studio/Ollama)
- Check your `ai.endpoint` configuration for valid provider names

**ServerAI not working:**
- Ensure it's enabled in config
- ServerAI only supports OpenAI-compatible APIs
- Check chat scanning settings

**High API costs:**
- Enable response caching
- Adjust rate limiting
- Use local AI models when possible

### Debug Mode
Enable debug logging in `config.yml`:
```yaml
debug-mode: true
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

- **Documentation**: [📖 Full Docs](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/)
- **Issues**: [GitHub Issues](https://github.com/theGreyWanderer-uc/tgwMinecraft-Chatr/issues)
- **Discussions**: [GitHub Discussions](https://github.com/theGreyWanderer-uc/tgwMinecraft-Chatr/discussions)

## Changelog

### v1.0.0
- Initial release with AI-powered NPCs
- ServerAI assistant
- Multi-provider AI support
- RAG system for accurate knowledge
- Comprehensive configuration options
- Rate limiting and caching
- Metrics and analytics

*For future releases, see [CHANGELOG.md](CHANGELOG.md) for detailed version history.*
