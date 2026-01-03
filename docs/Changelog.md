# Changelog

Version history and release notes for Chatr.

> **Note**
> This page is an overview. For the authoritative changelog that matches this repository, see the root `CHANGELOG.md` file in the project.

## [1.0.0] - 2025-12-30

This summarizes the initial 1.0.0 release as implemented in this repo. For the complete, structured list, see `CHANGELOG.md`.

### Added
- AI-powered NPCs with individual personalities and configurations
- Server-wide AI assistant with chat monitoring
- Multi-provider AI support (LM Studio/Ollama via OpenAI-compatible endpoints, plus Groq, Mistral, NVIDIA, OpenRouter, Gemini for NPCs)
- RAG (Retrieval-Augmented Generation) system for accurate Minecraft knowledge used by ServerAI
- Context-aware conversations with persistent memory
- Click-to-chat functionality using ProtocolLib
- Rate limiting and response caching
- Metrics and statistics for API usage
- YAML-based configuration system (`config.yml`, `npcs.yml`, per-NPC configs)
- Command system for NPC and ServerAI management
- Permission-based access control
- Debug logging and troubleshooting options

### Technical Details
- Java 21 runtime
- Spigot/Paper API (1.20+ / 1.21 tested)
- Gradle build with paperweight plugin
- Gson for JSON processing
- Async processing for AI requests where applicable

Older alpha/beta sections on this page are historical and may not correspond exactly to the current code layout. For precise version-by-version detail, always prefer `CHANGELOG.md` in the repo root.

## Version Numbering

Chatr follows [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

## Release Channels

- **Stable**: Production-ready releases
- **Beta**: Feature-complete with testing
- **Alpha**: Early testing releases
- **Dev**: Development builds

## Installation Notes

### Upgrading from Beta
1. Backup your `config.yml` and NPC data
2. Download the new JAR file
3. Replace the old JAR
4. Start server to generate new config
5. Merge your settings
6. Test functionality

### Breaking Changes
- None in 1.0.0 (initial release)
- Future versions will document breaking changes

## Bug Fixes

### Fixed in 1.0.0
- Configuration validation issues
- NPC spawning in unloaded chunks
- Memory leaks in conversation handling
- Permission checking edge cases
- API timeout handling

## Performance Improvements

### 1.0.0 Improvements
- Async AI request processing
- Connection pooling for API calls
- Memory-efficient conversation storage
- Optimized NPC entity handling
- Reduced server tick impact

## Compatibility

### Minecraft Versions
- 1.20.x - 1.21.x (Spigot/Paper)

### Java Versions
- Java 21 (required)

### Plugin Dependencies
- ProtocolLib 5.x

## Future Plans

### 1.1.0 (Planned)
- Enhanced RAG system
- Web interface for NPC management
- Additional AI providers
- Advanced conversation flows

### 1.2.0 (Planned)
- Multi-language support
- Voice integration
- Advanced NPC behaviors
- Plugin API for developers

### 2.0.0 (Planned)
- Complete rewrite for performance
- New AI framework
- Enhanced mod compatibility

## Support

### Getting Help
- Check the [wiki](../wiki) for documentation
- Report issues on [GitHub](https://github.com/thegreywanderer-uc/Chatr/issues)
- Join our [Discord](https://discord.gg/chatr) for community support

### Reporting Bugs
Please include:
- Plugin version
- Server version
- Java version
- Full error logs
- Steps to reproduce

## Credits

### Contributors
- TheGreyWanderer (Lead Developer)

### Libraries Used
- Spigot API
- ProtocolLib
- SnakeYAML
- SQLite JDBC
- OkHttp

### Special Thanks
- Minecraft community for feedback
- AI provider communities
- Open source contributors

## License

Chatr is released under the MIT License. See LICENSE file for details.

---

*For the latest updates, check the [GitHub repository](https://github.com/thegreywanderer-uc/Chatr/releases).*