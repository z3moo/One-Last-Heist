# One Last Heist

A 2D top-down stealth/heist game built in Java with [LibGDX](https://libgdx.com/). The player breaks into a neighbourhood at night, loots cash, dodges traps, and tries to escape before the homeowner returns. A hidden route unlocks a true ending.

## Status

Pre-alpha. The vertical slice runs end-to-end: title screen → tutorial (operation manual) → exterior heist map with the player walking around a real Tiled map, blocked by hand-tuned collisions, and an animated door interaction prompt above the front door of the big house. The small house has a locked-door variant. NPCs are wired into the simulation but currently invisible — they will be flipped on once their behaviour is in.

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
| `E` | Interact (door, NPC, hiding spot) |
| `F` | Pick up |
| `Esc` | Pause / unpause |

Bindings live in [`ControlConfig.java`](core/src/main/java/com/onelastheist/game/config/ControlConfig.java) and are read every frame, so a future settings UI can swap them at runtime.

## Architecture

The codebase splits into a domain layer (state and rules), a render layer (LibGDX-aware drawing), and a screen layer (LibGDX lifecycle).

```
launcher (lwjgl3)
  └─> OneLastHeistGame (LibGDX Game)
        ├─> GameContext      // shared services: AssetManager, configs
        └─> ScreenNavigator  // routes between screens
              ├─> MainMenuScreen
              ├─> PlayScreen           // tutorial → fade → gameplay
              │     ├─ PlayerController (input → intent)
              │     ├─ GameWorld       (Player, NPCs, Doors, RoomGraph,
              │     │                   WorldClock, Objectives, AlarmSystem,
              │     │                   TiledMap, CollisionMap)
              │     └─ WorldRenderer   (tile + sprite drawing, two-pass for
              │                         overhead occlusion)
              ├─> PauseScreen
              └─> EndingScreen (per EndingType)
```

### Key packages

| Package | What lives here |
| --- | --- |
| `app/` | `OneLastHeistGame` entry point, `GameContext`, `ScreenNavigator` |
| `config/` | Static knobs: `GameConfig` (resolution), `ControlConfig` (key bindings), `BalanceConfig` (gameplay tuning) |
| `screen/` | LibGDX `Screen` implementations — title, gameplay, pause, ending |
| `world/` | Runtime simulation: `GameWorld`, `WorldFactory`, `CollisionMap`, `Door`, `WorldClock`, `ObjectiveTracker`, `RoomGraph` |
| `entity/` | Game objects. `base/` has `Entity`, `MovableEntity`, `FacingDirection`. `player/` and `npc/` host the concrete actors. |
| `render/` | `WorldRenderer` does all gameplay drawing |
| `interaction/` `item/` `environment/` `trap/` `ai/` `quest/` `ending/` `ui/` `audio/` `save/` | Stubs for upcoming systems. Most are still package skeletons. |

### Map pipeline

The exterior map is authored in Tiled and committed under `assets/maps/`:

- `Exterior_Neighbour_upgrade.tmx` — 60×40 tiles, 16 px tiles, scaled 3× at runtime so 1 tile = 48 world units.
- Tile layers are nested in groups (`Base_Ground`, `Buildings_&_Fences`, `farm`, …); the renderer recurses into them automatically.
- The `Overhead_Foreground` tile layer is rendered **after** the player so tree canopies and roof tops occlude the character.
- The `Collisions` object layer is the source of truth for blocking. Every rectangle drawn there becomes a world-space AABB at load time; `CollisionMap` shrinks each one by 4 world units to compensate for the artists drawing collision a hair larger than the visible sprite (without that inset, corners visibly "poke" past the sprite outline and snag the player).
- Door rectangles are added to `CollisionMap` as solids in `WorldFactory`, so the player can never walk through a door — it has to be opened via `E`.

### Doors

Defined in [`world/Door.java`](core/src/main/java/com/onelastheist/game/world/Door.java) and seeded by `WorldFactory.createDoors()`:

- **Big house** (House_demo) — unlocked, label `Enter House`. Pressing `E` will navigate to the interior map (currently logs the target id; navigation hooks in once that map is imported).
- **Small house** (House2) — locked, label `Locked`. Pressing `E` triggers a red shake/flash animation on the prompt instead of changing screens.

The prompt itself is an animated panel drawn by `PlayScreen.drawDoorPrompt` — alpha pulses, the panel bobs up and down, and a gold halo breathes behind it. Locked variant flips the palette to red and intensifies the pulse with a small horizontal shake on press.

## Project layout

```
core/src/main/java/com/onelastheist/game/
  OneLastHeistGame.java
  app/        config/        screen/      world/
  entity/     render/        interaction/ item/
  environment/ trap/          ai/          quest/
  ending/     ui/             audio/       save/
  map/

lwjgl3/src/main/java/com/onelastheist/game/lwjgl3/
  Lwjgl3Launcher.java
  StartupHelper.java

assets/
  characters/
    enemies/dog/        enemies/neighbour/
    player/
  maps/
    Exterior_Neighbour_upgrade.tmx
    tilesets/exterior/
  start_screen/
    button_play/  button_credits/  button_setting/  button_exit/
    button_resume/  button_restart/  button_backtomenu/
```

## Done so far

- Multi-module Gradle build (`core` + `lwjgl3`)
- Title screen with hover/pressed button states and credits popup
- Tutorial (operation manual) with four key-cards explaining controls
- Tiled map loaded with overhead occlusion
- Hand-tuned rectangle collisions sourced from a Tiled object layer
- Player movement with axis-separated wall sliding, walk + crouch speeds, 8-direction sprite animations
- Door interaction prompt with locked/unlocked variants and proper animation
- Pause overlay with Resume / Restart / Back to Menu buttons
- Camera that follows the player and clamps to the map edges
- Faster default move speed (`360` units/s) tuned for the current map size

## Not done yet

- Interior maps (big house and side house)
- Item pickups, inventory UI
- Trap reveal + alarm flow
- Hiding spots
- Homeowner AI (patrol, search, detection)
- Dog noise / wake-up reactions
- Hidden route + true ending content
- Final encounter
- Ending screens content
- Save / load
- Audio integration (music + SFX)
- HUD (timer, money, objectives)

## Contributing notes

- Don't commit directly to `main`; branch first.
- Map collisions are authored in the Tiled `Collisions` object group, not as tile metadata — that's intentional and the only path the runtime uses.
- The 4-world-unit collision inset in `CollisionMap.SOLID_INSET` is the single knob if corners feel too sticky or too loose.
- NPCs default to `setVisible(false)` until their behaviour is wired in. Flipping them on takes a one-line change in `WorldFactory` or wherever the spawn event lives.
