# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project setup and documentation

## [1.0.0] - 2025-12-30

### Added
- AI-powered NPCs with individual personalities and configurations
- Server-wide AI assistant "Heimdall" with chat monitoring
- Multi-provider AI support (Groq, Mistral, NVIDIA, OpenRouter, Gemini)
- RAG (Retrieval-Augmented Generation) system for accurate Minecraft knowledge
- Context-aware conversations with persistent memory
- Click-to-chat functionality using ProtocolLib
- Comprehensive rate limiting and response caching
- API usage metrics and analytics
- YAML-based configuration system
- Extensive command system for NPC management
- Permission-based access control
- Debug logging and troubleshooting features

### Technical Features
- Gradle build system with paperweight plugin
- Gson integration for JSON processing
- Comprehensive logging with debug modes
- Cross-platform compatibility (Windows/Linux)
- Plugin metrics and performance tracking

### Documentation
- Complete README with installation and configuration guides
- API provider setup instructions
- Troubleshooting section
- Command reference and permissions guide

### Configuration
- Extensive config.yml with all features documented
- NPC-specific configuration files
- System prompt customization with context variables
- Rate limiting and caching controls
- RAG system configuration

### Dependencies
- Spigot/Paper 1.20+ compatibility
- ProtocolLib for advanced features
- Support for local AI servers (LM Studio, Ollama) and cloud providers

---

## Types of Changes
- `Added` for new features
- `Changed` for changes in existing functionality
- `Deprecated` for soon-to-be removed features
- `Removed` for now removed features
- `Fixed` for any bug fixes
- `Security` in case of vulnerabilities

## Version Format
This project uses [Semantic Versioning](https://semver.org/):
- **MAJOR.MINOR.PATCH** (e.g., 1.0.0)
- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible