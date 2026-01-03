# API Reference

This page documents planned API functionality. The current released code does **not** yet expose a stable public API beyond the standard Bukkit plugin access.

## Current State

- You can obtain the plugin instance via the Bukkit API:

```java
import io.github.thegreywanderer_uc.chatr.Chatr;

public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Chatr chatr = (Chatr) getServer().getPluginManager().getPlugin("Chatr");
        if (chatr != null) {
            getLogger().info("Chatr plugin found!");
        }
    }
}
```

- Internal classes (NPC storage, ServerAI, metrics, etc.) are implementation details and may change without notice.
- There is no public `ChatrAPI` class, no custom events, and no supported extension points yet.

## Roadmap

Planned (but not yet implemented) API features include:
- Read-only access to NPC metadata and conversation statistics
- Events for NPC interactions and ServerAI responses
- A registration mechanism for custom AI providers

## See Also

- [Configuration Guide](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/Configuration.html)
- [Command Reference](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/Commands.html)
- [Contributing Guide](https://thegreywanderer-uc.github.io/tgwMinecraft-Chatr/Contributing.html)
- [GitHub Repository](https://github.com/theGreyWanderer-uc/tgwMinecraft-Chatr)