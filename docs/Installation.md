# Installation

This guide will help you install and set up Chatr on your Minecraft server.

## Requirements

Before installing Chatr, ensure your server meets these requirements:

- **Minecraft Version**: 1.20 or higher (tested on 1.21)
- **Server Software**: Spigot or Paper (Java Edition only)
- **Java Version**: Java 21 or higher
- **Dependencies**:
  - ProtocolLib (required for click-to-chat feature)
  - AI server or API keys (LM Studio, Ollama, Groq, Mistral, NVIDIA, OpenRouter, Gemini for NPCs)

## Download

1. Visit the [GitHub Releases](https://github.com/theGreyWanderer-uc/tgwMinecraft-Chatr/releases) page
2. Download the latest `Chatr-1.0.0.jar` file
3. Save it to your server's `plugins/` directory

## Installation Steps

### Step 1: Install Dependencies

First, install the required plugins:

1. Download ProtocolLib from [SpigotMC](https://www.spigotmc.org/resources/protocollib.1997/)
2. Place `ProtocolLib.jar` in your `plugins/` directory
3. Restart your server to generate ProtocolLib's configuration

### Step 2: Install Chatr

1. Place `Chatr-1.0.0.jar` in your `plugins/` directory
2. **Important**: Perform a full server restart (not just `/reload`)
3. Chatr will create its configuration files in `plugins/Chatr/`

### Step 3: Verify Installation

Check your server console for:
```
[Chatr] Chatr plugin has been enabled!
```

If you see this message, Chatr is successfully installed.

## Initial Configuration

After installation, you'll need to configure at least one AI provider. See [[Configuration]] for detailed setup instructions.

## Common Installation Issues

### Plugin doesn't load
- Ensure you're using Spigot/Paper 1.20+
- Check that ProtocolLib is installed and working
- Verify Java 21+ is being used

### Configuration files missing
- Make sure to do a full server restart after adding the JAR
- Check file permissions on the plugins directory

### Console errors
- Enable debug mode in config.yml for detailed error information
- Check that all required dependencies are installed

## Next Steps

Once installed, proceed to:
1. [[Configuration|Configure your AI provider]]
2. [[NPCs|Create your first NPC]]
3. [[ServerAI|Set up the server assistant]] (optional)