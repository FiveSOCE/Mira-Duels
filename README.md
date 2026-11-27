
## Version-specific source branches

- **Modern Paper 1.21.11 and newer:** [main](https://github.com/FiveSOCE/Mira-Duels/tree/main) (existing implementation, retained unchanged).
- **Legacy Paper 1.18.2 / Java 17:** [compat/1.18.2](https://github.com/FiveSOCE/Mira-Duels/tree/compat/1.18.2) (migration branch, with an automated compile check).

**Important:** The legacy branch explicitly targets the Paper 1.18.2 API, but runtime support and working release downloads are **not yet verified**. Do not substitute the modern JAR for the legacy version. The build workflow must pass and 1.18.2 server testing must be completed before this can be marked a supported release.

# MiraDuels

## Download

**Latest compatibility release: v0.1.1**

[**Download MiraDuels-0.1.1.jar**](https://github.com/FiveSOCE/Mira-Duels/releases/download/v0.1.1/MiraDuels-0.1.1.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Duels/releases)

Configurable 1v1 duels with staff-defined kits and arena spawn points.

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
