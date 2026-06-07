# One Last Heist

A 2D top-down stealth/heist game built in Java with [LibGDX](https://libgdx.com/). The player infiltrates a neighbourhood at night, loots cash, and tries to escape before time runs out.

## Tech

- Java 21
- LibGDX (`core`) + LWJGL3 desktop launcher (`lwjgl3`)
- Maps authored in [Tiled](https://www.mapeditor.org/) (`.tmx` + `.tsx`)
- Gradle multi-module build

## Run

From the project root:

```powershell
.\gradlew.bat :lwjgl3:run
```

Or build a desktop jar:

```powershell
.\gradlew.bat :lwjgl3:dist
```

The jar lands in `lwjgl3/build/libs/`.

## Controls

| Key | Action |
| --- | --- |
| `W` / `A` / `S` / `D` | Move (8-directional) |
| `Ctrl` | Crouch (slower, quieter — see [`Player.CROUCH_SPEED`](core/src/main/java/com/onelastheist/game/entity/player/Player.java)) |
| `E` | Interact |
| `F` | Pick up |
| `G` | Drop |
| `Mouse wheel` | Cycle inventory |
| `Esc` | Pause / unpause |

Bindings live in [`ControlConfig.java`](core/src/main/java/com/onelastheist/game/config/ControlConfig.java) and are read every frame, so a future settings UI can swap them at runtime.

## Architecture

The codebase splits into a domain layer (state and rules), a render layer (LibGDX-aware drawing), and a screen layer (LibGDX lifecycle).

```
launcher (lwjgl3)
  └─> OneLastHeistGame (LibGDX Game)
        ├─> GameContext      // shared services: AssetManager, configs, audio
        └─> ScreenNavigator  // routes between screens
              ├─> MainMenuScreen
              ├─> PlayScreen
              │     ├─ PlayerController (input → intent)
              │     ├─ GameWorld       (Player, NPCs, Doors, RoomGraph,
              │     │                   WorldClock, Objectives, AlarmSystem,
              │     │                   TiledMap, CollisionMap)
              │     └─ WorldRenderer   (tile + sprite drawing, multi-pass for
              │                         overhead occlusion)
              ├─> PauseScreen
              └─> EndingScreen
```

### Key packages

| Package | What lives here |
| --- | --- |
| `app/` | `OneLastHeistGame` entry point, `GameContext`, `ScreenNavigator` |
| `config/` | Static knobs: `GameConfig` (resolution), `ControlConfig` (key bindings), `BalanceConfig` (gameplay tuning) |
| `screen/` | LibGDX `Screen` implementations |
| `world/` | Runtime simulation: `GameWorld`, `WorldFactory`, `CollisionMap`, `Door`, `WorldClock`, `ObjectiveTracker`, `RoomGraph` |
| `entity/` | Game objects. `base/` has `Entity`, `MovableEntity`, `FacingDirection`. `player/` and `npc/` host the concrete actors. |
| `ai/` | NPC behaviour and pathfinding |
| `render/` | `WorldRenderer` does all gameplay drawing |
| `audio/` | `AudioService`, `MusicId`, `SfxId` — process-wide mixer for music and SFX |
| `interaction/` `item/` `environment/` `trap/` `quest/` `ending/` `ui/` `save/` | Domain helpers and value types |

### Map pipeline

Maps are authored in Tiled and committed under `assets/maps/`:

- 16-pixel source tiles, scaled 3× at runtime so 1 tile = 48 world units.
- Tile layers can be nested in groups; the renderer recurses into them automatically.
- A `Collisions` object layer is the source of truth for blocking. Every rectangle drawn there becomes a world-space AABB at load time; `CollisionMap` shrinks each one by 4 world units to compensate for the artists drawing collision a hair larger than the visible sprite.
- A foreground tile layer is rendered after the player so canopies and rooftops occlude the character.
- Door rectangles are added to `CollisionMap` as solids in `WorldFactory`, so the player can never walk through a door — it has to be opened via `E`.

### Doors

Defined in [`world/Door.java`](core/src/main/java/com/onelastheist/game/world/Door.java) and seeded by `WorldFactory`. A door has a world-space AABB, a target map id, and a locked flag. Pressing `E` near an unlocked door triggers a screen swap; pressing it on a locked door triggers a red shake/flash on the prompt and consumes any matching key from the player's inventory before unlocking.

The prompt itself is an animated panel drawn by `PlayScreen.drawDoorPrompt` — alpha pulses, the panel bobs up and down, and a gold halo breathes behind it.

## Project layout

```
core/src/main/java/com/onelastheist/game/
  OneLastHeistGame.java
  app/         config/    screen/      world/
  entity/      render/    interaction/ item/
  environment/ trap/      ai/          quest/
  ending/      ui/        audio/       save/

lwjgl3/src/main/java/com/onelastheist/game/lwjgl3/
  Lwjgl3Launcher.java
  StartupHelper.java

assets/
  characters/  end/    items/   maps/
  sounds/      start_screen/    ui/
```

## Contributing notes

- Don't commit directly to `main`; branch first.
- Map collisions are authored in the Tiled `Collisions` object group, not as tile metadata — that's intentional and the only path the runtime uses.
- The 4-world-unit collision inset in `CollisionMap.SOLID_INSET` is the single knob if corners feel too sticky or too loose.
- `BalanceConfig` is the canonical place for gameplay tuning constants; gameplay code should read from there rather than hardcoding values.
