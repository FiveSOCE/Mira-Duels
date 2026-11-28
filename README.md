# MiraDuels

Restorable 1v1 duels using staff-configured kits and arena spawns.

## Downloads and compatibility

| Server target | Java | Final JAR name | Download |
| --- | --- | --- | --- |
| **Paper 1.18.2** | 17 | `MiraDuels_1.18.2.jar` | [Download 1.18.2 JAR](https://github.com/FiveSOCE/Mira-Duels/releases/download/dev-1.18.2/MiraDuels_1.18.2.jar) |
| **Paper 1.21.11–26.2** | Java 21 baseline; Paper 26.2 may require Java 25 | Not yet published | [Check releases](https://github.com/FiveSOCE/Mira-Duels/releases) |

**Build status:** Version-specific direct links are provided where published. A GitHub Actions or Releases link means the corresponding versioned JAR has not been verified. Compilation alone does not establish in-game compatibility; intermediate versions 1.19–1.21.10 remain unverified unless specifically documented.

**Installation:** Back up plugin data, remove older JAR copies and install only the build matching your server. Never install both builds together.

[Legacy source](https://github.com/FiveSOCE/Mira-Duels/tree/compat/1.18.2) · [Modern source](https://github.com/FiveSOCE/Mira-Duels) · [All releases](https://github.com/FiveSOCE/Mira-Duels/releases)

## Players

- `/duel <player> [kit]`
- `/duel accept <player>`
- `/duel deny <player>`
- `/duel kits`

## Staff setup

- `/duelsadmin setkit <name>` - saves your current inventory/armor/offhand as the kit
- `/duelsadmin delkit <name>`
- `/duelsadmin setspawn 1`
- `/duelsadmin setspawn 2`

Duelists receive the configured kit, can only be damaged by their registered opponent, cannot drop duel items, and have their original inventory/location/state restored when the duel finishes or is cancelled.

Requires MiraCore.

Release: https://github.com/FiveSOCE/Mira-Duels/releases/tag/v0.1.0
SHA-256: `8e973e587e5c9a1d87fe1cbdf95b12953f6957682dd28f4464b5c0e7ece0e505`

## Dependencies and installation

- **Required:** MiraCore; matching Paper version and Java runtime.
- **Optional integrations:** None declared.
- Back up plugin data, remove old JARs, and restart after installing the appropriate release.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `miraduels.use` | true | Plugin permission |
| `miraduels.admin` | op | Plugin permission |

## Commands

| Command | Purpose |
| --- | --- |
| `/duel` | Challenge or respond to a 1v1 duel. |
| `/duelsadmin` | Configure duel kits and arena spawns. |
