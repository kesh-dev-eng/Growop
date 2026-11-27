# GrowOps

Paper plugin (Minecraft 26.1.2) for growing, processing and hiding crops, with ItemsAdder support.

## Build
Push to GitHub: the included workflow builds `GrowOps-1.0.0.jar` (Actions -> Artifacts).
Local: `gradle build` with Java 25.

## ItemsAdder (custom items)
1. Copy `itemsadder/growops/` into `plugins/ItemsAdder/contents/growops/`
2. Run `/iazip` (or `/iareload`) and restart.
3. Replace the placeholder PNGs in `textures/item/` with your own art (same file names).
4. Each plugin item maps to an ItemsAdder id in `plugins/GrowOps/items.yml` (`itemsadder: "growops:pot"`).
   Point any of them at your own ItemsAdder item. Without ItemsAdder, vanilla fallbacks are used.
5. Plant stages use items `plant_stage_0..4` and `plant_dead`. Give a strain its own models with
   `stage-items: [a, b, c, d, e]` in strains.yml (keys from items.yml).

## Strains and effects
| Strain | Effects when smoked |
|---|---|
| Blue Dream | Night Vision, Slow Falling, Regeneration |
| Bubba Kush | Resistance, Slowness, Saturation |
| Super Lemon Haze | Speed, Haste, Jump Boost |
| Azure Haze | Water Breathing, Dolphin's Grace, Night Vision |
| Whitethorn Rose | Regeneration, Absorption, Health Boost |
| OG Kush | Strength, Resistance, Hunger |
| Panama Red | Fire Resistance, Strength, Speed |
| Purple Haze | Levitation, Night Vision, Nausea |
| Ghost Train Haze | Invisibility, Speed II, Night Vision |
| Pineapple Express | Luck, Haste, Jump Boost |

Every strain has its own plant colour (all 5 growth stages), bud, bag, ground weed, joint and brick models.
Edit effects in `strains.yml` (`effects: ["SPEED:2", "NIGHT_VISION"]`), timings in `config.yml` under `joint:`.

## Lighting a joint
Hold a Joint and right-click. You need a **Lighter** (`/growops give lighter`) or vanilla flint and steel in your inventory.
After a short smoking animation you get the strain's effects. Higher quality = longer effects.
Levitation is capped at 6 s and followed by Slow Falling so nobody falls to their death.

## Updating from the first version
Delete `plugins/GrowOps/strains.yml` and `plugins/GrowOps/items.yml` so the new ones are created, and replace the ItemsAdder
`growops` folder. Plants of the removed strain (White Widow) in `data.yml` show as unknown strain, clear them with sneak + click.

## Gameplay
- Place Pot / Tent / Auto-Waterer / Processing Table / Press / Lockup by right-clicking a block with the item.
- Plant seeds in a pot. Water with the Watering Can (refill by right-clicking water). Soil goes MUD -> DIRT -> COARSE_DIRT as it dries.
- Fertilizer: only while growth is under 30%, once per plant. Pests: cure with Pesticide Spray.
- Harvest ready plants (sparkles in strain colour). Buds carry strain + quality through bag / grind / roll / press.
- Lockup: sneak + right-click. Add crew with `/growops lockup add <player>`.
- Grow-ops: `/growops op create lab1`, then look at blocks: `door1`/`door2` (opposite corners of the container door),
  `switch`, `light` (redstone lamps), `perm <permission>` for job restriction (LuckPerms).
- Permanent lab stations: `/growops fixed pot` (cannot be broken by players).
- `/growops give <item> [amount] [player] [strain]` (strain for bud, weed_bag, ground, joint, brick), `/growops hud` toggles the status readout.

## Config
`config.yml` (timings, tent, fertilizer, placement limits, indoor-only, allowed worlds, mini-game difficulty),
`strains.yml` (water use, yield, seeds, mutation, decay, colour, requires-optimal).

## Notes
- Data saves to `data.yml` / `ops.yml` (every 5 min and on shutdown).
- Not compiled in the build sandbox (no access to the Paper repo). If GitHub Actions reports a compile error, send me the log.
