# Poseidon's Trident - Forge 1.21.1

## Crafting
```
 Gold  Diamond  Gold
 Gold  Gold     Gold
  -    Gold      -
```
(G = gold ingot, centre-top = diamond, bottom row only the middle slot.)

## Build
1. Install JDK 21.
2. In this folder run `./gradlew build` (Windows: `gradlew build`).
3. The mod jar is in `build/libs/poseidonstrident-1.0.0.jar` - drop it in your `mods` folder with Forge 1.21.1.
   To test from the IDE: `./gradlew runClient`.
If Gradle can't find `forge_version=52.1.0`, put the newest 1.21.1 build from files.minecraftforge.net in `gradle.properties`.

## Controls (rebindable in Options > Controls > Poseidon's Trident)
All keys only work in-game, and none of them clash with vanilla Minecraft keys.

**Ability selector (hold the trident)**
| Key | Action |
|-----|--------|
| V | Next ability |
| B | Previous ability |
| N | Use the selected ability |

Switching plays an advancement-style sound and shows the ability name in its colour
(Thunder Domain gold, God Speed orange, Water Cube aqua, Tsunami deep blue).
For God Speed, N charges the trident, N again activates it, and N again gives an extra burst.

**Direct keys (still work)**
| Key | Power |
|-----|-------|
| G | Thunder Domain |
| R, then K | Charge, then Zenitsu's God Speed (K again = extra burst) |
| H | Absolute Water Cube |
| J | The Great Tsunami |

Holding the trident also spirals particles up the staff; the theme follows the selected ability.
Each power lasts 60 s then has a 10 s cooldown. Passive powers work in main hand or off-hand.

## Credits
Staff model "Minecraft 3D Staff" by oGian (https://sketchfab.com/oGian), CC-BY-4.0.
