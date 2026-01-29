# Hytale Ability Hotbar Plugin

This repository contains a Hytale Java plugin that provides an ability hotbar system intended to be used as a dependency by other mods.

The plugin handles the core logic for ability hotbars, abilities, and ability-based items, allowing other plugins to hook into the system without needing to reimplement UI, input handling, or ability execution logic themselves.

The goal is to let mod authors focus on designing abilities and items, while this plugin manages the underlying systems.

---

## Project Status

This plugin is actively developed and considered near-complete.

- Core systems are implemented and functional
- Dependency and expansion handling is still being finalized
- Some plugin-to-plugin interactions are currently limited

While usable, the dependency system is still stabilizing. Other plugins should avoid relying on it as a hard dependency until the remaining issues are resolved.

---

## What This Plugin Handles

- Ability hotbar UI and input handling
- Ability execution and routing
- Custom ability bars per weapon or item
- Integration of abilities provided by other plugins
- Shared ability logic across multiple mods

Other plugins can register abilities, hotbars, or ability-based items, and this plugin will handle how they are displayed and activated.

---

## Example Content

This repository includes example weapons and abilities used to demonstrate what the system supports.

These are provided as reference implementations and showcases, not as required gameplay content.

---

## Dependency Usage

This plugin is designed to be included as a dependency for other Hytale plugins.

At the moment:
- Dependency support is still being fixed
- Expansion-related functionality is work in progress

Until these issues are resolved, plugins should integrate cautiously and expect potential changes.

---

## Contributors

- NicholasH909 — creator and maintainer  
  https://github.com/NicholasH909

- ColtonMelhase — contributor and support  
  https://github.com/ColtonMelhase

Community contributions, testing, and feedback are welcome.

---

## Contributing

If you would like to help improve the plugin:

- Fork the repository
- Create a branch for your changes
- Submit a pull request with a clear description

Bug reports and suggestions are also appreciated.

---

## Development Setup

This project is based on a Hytale Java plugin template originally created by UpcraftLP and later modified by Kaupenjoe.
Upcraft: https://github.com/UpcraftLP

If your Hytale installation is in a non-standard location, you may need to configure Gradle.

Create the following file if it does not already exist:

%USERPROFILE%/.gradle/gradle.properties

Example configuration:

```properties
# Set a custom game install location
hytale.install_dir=path/to/Hytale

# Speed up the decompilation process by only including core Hytale packages
hytale.decompile_partial=true
