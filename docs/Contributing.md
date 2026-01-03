# Contributing

Guidelines for contributing to the Chatr project.

## Ways to Contribute

### Code Contributions
- Bug fixes
- New features
- Performance improvements
- Code refactoring

### Documentation
- Wiki improvements
- Code comments
- README updates
- Tutorial creation

### Testing
- Bug reports
- Feature testing
- Compatibility testing
- Performance benchmarking

### Community Support
- Helping other users
- Answering questions
- Creating tutorials
- Reporting issues

## Development Setup

### Prerequisites
- Java 21 JDK
- Gradle 8.x+ (included via wrapper)
- Git
- IDE (IntelliJ IDEA recommended)

### Getting Started
1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/Chatr.git
   cd Chatr
   ```
3. Set up upstream remote:
   ```bash
   git remote add upstream https://github.com/thegreywanderer-uc/Chatr.git
   ```
4. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```

### Building the Project
```bash
# Build the plugin (from chatr directory)
./gradlew build

# Run tests
./gradlew test

# Build reobfuscated JAR for production
./gradlew reobfJar
```

### Development Workflow
1. Create a feature branch from `main`
2. Make your changes
3. Write/update tests
4. Ensure code compiles and tests pass
5. Update documentation if needed
6. Commit with clear messages
7. Push to your fork
8. Create a Pull Request

## Code Standards

### Java Style
- Follow Oracle Java Code Conventions
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use meaningful variable and method names

### Code Structure
```
src/main/java/io/github/thegreywanderer_uc/chatr/
├── Chatr.java              # Main plugin class
├── commands/               # Command handlers
├── config/                 # Configuration management
├── npc/                    # NPC system
├── serverai/               # ServerAI system
├── ai/                     # AI provider integrations
├── rag/                    # RAG system
└── utils/                  # Utility classes
```

### Naming Conventions
- Classes: `PascalCase`
- Methods: `camelCase`
- Variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: `lowercase`

### Documentation
- All public methods must have JavaDoc
- Comments for complex logic
- Update README for new features

## Testing

### Unit Tests
- Test individual components
- Mock external dependencies
- Cover edge cases
- Use JUnit 5

### Integration Tests
- Test plugin loading
- Test AI provider connections
- Test NPC interactions
- Use test server environment

### Manual Testing
- Test on different server versions
- Test with different AI providers
- Test permission systems
- Test performance under load

## Pull Request Process

### Before Submitting
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Code follows style guidelines
- [ ] Documentation updated
- [ ] Commit messages are clear
- [ ] Branch is up to date with main

### PR Template
```
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing completed

## Additional Notes
Any additional information or context
```

### Review Process
1. Automated checks run
2. Code review by maintainers
3. Testing verification
4. Merge approval

## Issue Reporting

### Bug Reports
Use the bug report template:
- Clear title
- Steps to reproduce
- Expected vs actual behavior
- Environment details
- Logs and screenshots

### Feature Requests
Use the feature request template:
- Clear description
- Use case
- Benefits
- Implementation ideas

### Questions
- Check existing issues first
- Use discussions for questions
- Provide context and details

## Documentation

### Wiki Contributions
- Follow existing structure
- Use clear, concise language
- Include examples
- Update navigation links

### Code Documentation
- JavaDoc for all public APIs
- Comments for complex algorithms
- Update README for changes

## Community Guidelines

### Code of Conduct
- Be respectful and inclusive
- Focus on constructive feedback
- Help newcomers
- Follow project guidelines

### Communication
- Use English for all communications
- Be clear and concise
- Provide context
- Stay on topic

## Recognition

### Contributors
All contributors are recognized:
- GitHub contributor list
- Changelog mentions
- Special thanks in releases

### Rewards
- Early access to features
- Recognition in documentation
- Priority support
- Community recognition

## Getting Help

### Resources
- [Wiki](../wiki) - Documentation
- [Issues](https://github.com/thegreywanderer-uc/Chatr/issues) - Bug reports
- [Discussions](https://github.com/thegreywanderer-uc/Chatr/discussions) - Questions

### Contact
- GitHub Issues for bugs/features
- Discord for community discussion
- Email for private matters

## License

By contributing, you agree to license your contributions under the MIT License.

---

*Thank you for contributing to Chatr! Your help makes the plugin better for everyone.*