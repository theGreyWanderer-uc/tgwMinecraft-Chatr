# FAQ

Frequently asked questions about Chatr.

## General Questions

### What is Chatr?
Chatr is a Minecraft plugin that adds AI-powered NPCs and a server-wide AI assistant to your Minecraft server. Players can have conversations with NPCs and ask questions to ServerAI.

### Which Minecraft versions are supported?
Chatr supports Minecraft 1.20.x through 1.21.x on Spigot and Paper servers.

### Do I need an internet connection?
Chatr can work entirely locally with providers like LM Studio or Ollama. Cloud providers (Groq, Mistral, NVIDIA, OpenRouter, Gemini for NPCs) require internet access.

### Is Chatr free?
The plugin itself is free and open source. However, using AI providers may incur costs depending on your chosen service.

## Installation & Setup

### Why won't the plugin load?
Common issues:
- **Java Version**: Ensure Java 21 is installed
- **Dependencies**: Install ProtocolLib
- **Server Type**: Must be Spigot or Paper, not vanilla Minecraft

### How do I configure AI providers?
1. Choose a provider (LM Studio, Ollama, Groq, Mistral, NVIDIA, OpenRouter, Gemini)
2. Get an API key from the provider
3. Add the configuration to `config.yml`
4. Restart the server

See [[API-Providers|AI Provider Setup]] for detailed instructions.

### Can I use multiple AI providers?
Yes! Configure multiple providers and set a default. You can also specify different providers for different NPCs.

## NPCs

### How do NPCs work?
NPCs are interactive entities that players can click to start conversations. They remember conversation history and use AI to respond naturally.

### Why can't players interact with NPCs?
Ensure ProtocolLib is properly installed and click-to-chat is enabled in `config.yml`. Players need the `chatr.ai` permission to chat with NPCs.

### Do NPCs persist through restarts?
Yes, NPC data is saved automatically. NPCs will respawn at their configured locations when the server restarts.

### Can NPCs move around?
Currently, NPCs are stationary. They stay at their spawn location but can be teleported using commands.

### How many NPCs can I have?
There's no hard limit, but performance depends on your server hardware and AI provider response times.

## ServerAI

### What is ServerAI?
ServerAI is a server-wide AI assistant that players can ask questions about your server, Minecraft in general, or any topic you configure.

### How do players use ServerAI?
Players mention ServerAI by name in chat (e.g., "@Heimdall what's the server rules?") or use admin commands to manage it.

### Can ServerAI answer questions about my server?
Yes, with proper configuration. You can provide context about server rules, features, and custom information.

### What's RAG and do I need it?
RAG (Retrieval-Augmented Generation) provides more accurate Minecraft information by using a knowledge base. It's optional but recommended for better responses.

## AI Providers

### Which AI provider should I use?
- **LM Studio**: Free, local models, requires setup
- **Ollama**: Free, local models, requires setup
- **Groq**: Fast, good quality, API key required
- **Mistral/NVIDIA/OpenRouter**: Cloud providers with OpenAI-compatible APIs
- **Gemini**: Google's AI, usable for NPCs (not ServerAI)

### How much does it cost?
Costs vary by provider:
- LM Studio/Ollama: Free (local models)
- Groq/Mistral/NVIDIA/OpenRouter: Pay per token (see provider docs)
- Gemini: Free tier available (subject to Google terms)

### Can I change providers later?
Yes, you can reconfigure providers at any time. Existing conversations will continue with the new provider.

## Performance

### Does Chatr cause lag?
Chatr is designed to be lightweight. AI requests are processed asynchronously to avoid blocking the main server thread.

### How can I improve performance?
- Use faster AI providers
- Reduce NPC count
- Enable rate limiting
- Use local providers like LM Studio

### What's the impact on server resources?
- Memory: Minimal additional usage
- CPU: Depends on AI request frequency
- Network: Outbound requests to AI providers
- Storage: Conversation logs and NPC data

## Permissions

### What permissions do players need?
Basic permissions:
- `chatr.use` – basic `/chatr` info
- `chatr.ai` – player AI chat (`/chatr r`, `/chatr clear`)
- `chatr.create`, `chatr.remove`, `chatr.skin`, `chatr.color`, `chatr.reload`, `chatr.admin` – admin features

### How do I set up permissions?
Use your permission plugin (LuckPerms, PermissionsEx, etc.) to grant permissions. See [[Permissions|Permission Guide]] for details.

### Why do players get "no permission" errors?
- Permission not granted
- Permission plugin not configured
- Plugin not loaded correctly

## Configuration

### How do I edit the configuration?
Edit `plugins/Chatr/config.yml` with a text editor. Changes require a server restart or `/chatr reload`.

### What settings should I change?
Essential settings:
- AI provider configuration
- Default models
- Rate limiting
- Permission settings

### Can I reset the configuration?
Delete `config.yml` and restart the server to generate a fresh configuration file.

## Troubleshooting

### NPCs aren't responding
Check:
- AI provider configuration
- Internet connection
- API key validity
- Console for error messages

### ServerAI gives poor answers
Try:
- Better prompt configuration
- Enable RAG system
- Use higher-quality AI models
- Provide more context

### Plugin commands don't work
Verify:
- Plugin is loaded (`/plugins`)
- You have the correct permissions (`chatr.use`, `chatr.ai`, `chatr.admin`, etc.)
- Command syntax is correct
- No conflicting plugins

### High CPU usage
Possible causes:
- Too many concurrent AI requests
- Slow AI provider responses
- Large conversation histories
- Debug logging enabled

## Advanced Features

### How do context variables work?
Context variables inject dynamic information into AI prompts. Examples used by Chatr include `{npc_name}`, `{time}`, `{time_exact}`, `{weather}`, `{player_name}`, `{player_biome}`, `{player_x}`, `{player_y}`, `{player_z}`.

### Can I customize NPC personalities?
Yes, edit the NPC prompt to define personality, knowledge, and behavior.

### What's the difference between NPCs and ServerAI?
- **NPCs**: Individual characters with per-player conversation memory managed by the plugin
- **ServerAI**: General server assistant with its own chat history and optional RAG knowledge base

### Can I backup NPC data?
Yes. Use your normal server backup process to back up the `plugins/Chatr/` folder, which contains all NPC and conversation data.

## Development

### Can I contribute to Chatr?
Yes! Chatr is open source. See [[Contributing|Contributing Guide]] for details.

### Is there an API for developers?
At the moment there is no stable public API beyond accessing the plugin instance via the Bukkit API. The [[API-Reference|API Reference]] page documents planned functionality rather than the current code.

### How do I report bugs?
Use GitHub Issues with:
- Plugin version
- Server version
- Steps to reproduce
- Error logs

## Legal & Ethics

### Is AI content appropriate for my server?
Configure content filters and monitor usage. Chatr includes safety measures but parental supervision is recommended.

### Can I use Chatr on a public server?
Yes, but consider:
- Age-appropriate content
- Privacy implications
- Server rules compliance
- Resource usage

### What about data privacy?
- Conversations are stored locally
- AI providers may log requests
- No personal data is sent without configuration
- Check provider privacy policies

## Getting Help

### Where can I get support?
- [[Home|Wiki Documentation]]
- [GitHub Issues](https://github.com/thegreywanderer-uc/Chatr/issues)
- Community Discord
- Plugin discussion forums

### I found a bug, what should I do?
1. Check if it's already reported
2. Gather information (logs, steps to reproduce)
3. Create a GitHub issue
4. Provide as much detail as possible

### Can I request features?
Yes! Use GitHub Issues with the "enhancement" label. Describe the feature and why it would be useful.

---

*Still have questions? Check the [[Troubleshooting|Troubleshooting Guide]] or ask in our community.*