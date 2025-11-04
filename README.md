# OmniHUD

An open-source ATAK plugin for viewing and controlling HUD (Heads-Up Display) devices via USB-C cable.

## Overview

OmniHUD enables ATAK integration with HUD devices like ECOTI, allowing real-time tactical data display directly in night vision and thermal optics. This plugin communicates via USB-C to send position, waypoint, and tactical information to compatible HUD hardware.

### Features

- **USB-C HUD Communication**: Direct connection to HUD devices
- **Real-time Data Streaming**: Send ATAK data to HUD displays
- **ECOTI Support**: Optimized for ECOTI integration
- **Position Overlay**: Display user position and heading
- **Waypoint Navigation**: Send waypoints and routes to HUD
- **Contact Information**: Display nearby teammates and contacts
- **Open Source**: Enable other manufacturers to integrate

### Hardware Compatibility

- **ECOTI**: Via USB-C adapter (https://www.reddit.com/r/NightVision/comments/1oj7oiq/)
- Additional HUD devices can be added via open-source contributions

## Screenshots

### HUD Dashboard

![OmniHUD Dashboard](screenshots/dashboard.png)

The plugin features a modern dashboard with:
- HUD connection status
- Data streaming controls
- Device configuration
- Real-time preview

## Requirements

- **ATAK Version**: 5.4.0 or newer (CIV/MIL)
- **Android**: 5.0 (API 21) or higher
- **Hardware**: USB-C capable Android device
- **HUD Device**: Compatible heads-up display unit

## Installation

1. Download the latest APK from [Releases](https://github.com/engindearing-projects/omni-HUD/releases)
2. Install on Android device with ATAK installed
3. Enable plugin in ATAK Settings → Plugin Management
4. Restart ATAK
5. Connect HUD device via USB-C

## Usage

### Connecting HUD Device

1. Connect HUD to Android device via USB-C
2. Grant USB permissions when prompted
3. Open OmniHUD from ATAK toolbar
4. Select your HUD model
5. Enable data streaming

### Data Configuration

Configure what data streams to your HUD:
- **Position**: Own location and heading
- **Waypoints**: Active navigation points
- **Contacts**: Nearby teammates
- **Routes**: Planned paths
- **Markers**: Map markers and POIs

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

MIT License - See [LICENSE](LICENSE) for details

## Credits

- **Hardware**: ECOTI USB-C adapter
- **ATAK SDK**: TAK Product Center
- **Community**: Thanks to r/NightVision for testing and feedback

## Support

- **Issues**: https://github.com/engindearing-projects/omni-HUD/issues
- **Discussions**: https://github.com/engindearing-projects/omni-HUD/discussions
- **Reddit**: r/NightVision

## Links

- **ECOTI HUD Adapter**: https://www.reddit.com/r/NightVision/comments/1oj7oiq/
- **TAK.gov**: https://tak.gov
- **ATAK Documentation**: https://tak.gov/products/atak

---

🤖 Built with [Claude Code](https://claude.com/claude-code)
