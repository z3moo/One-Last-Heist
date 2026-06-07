# One Last Heist

A 2D top-down stealth heist game written in Java with [LibGDX](https://libgdx.com/). The player has one night to break into a quiet neighbourhood, loot whatever cash they can find, and slip back out before the homeowner returns.

## Premise

The clock is set for ten minutes. After seven minutes pass the neighbour pulls into the driveway, walks up to the house, and starts hunting room by room. The job is simple: collect enough money before that happens, get out, stay out of sight, and avoid the dog asleep on the rug in the south room.

## Gameplay

- Walk, crouch, and slip past patrolling NPCs in a 2D top-down view.
- Loot coins (worth 10) and diamonds (worth 20) scattered across a fenced garden, a main house, and a locked side storage building.
- Distract the guard dog by dropping meat on the floor; drugged meat puts it to sleep, plain meat just breaks its line of pursuit.
- Solve a small environmental puzzle on the piano in the entry room to recover the side house key.
- Read the newspaper on the kitchen counter for a story beat.
- Endings depend on what the player did: caught or out of time without enough cash is a loss; escaping the garden with the target money in hand is a win. A third ending is reserved for a hidden route.

## Controls

| Key | Action |
| - | - |
| `W` / `A` / `S` / `D` | Move (8-directional) |
| `Ctrl` | Crouch (slower, quieter) |
| `E` | Interact (doors, newspaper, piano) |
| `F` | Pick up the item under the player |
| `G` | Drop a piece of meat |
| `Mouse wheel` | Cycle the inventory hotbar |
| `Esc` | Pause / unpause |
| `Q` | Close the piano keyboard overlay |

Bindings live in [`ControlConfig.java`](core/src/main/java/com/onelastheist/game/config/ControlConfig.java) and are read every frame, so a future settings screen can rebind them at runtime.

## Tech stack

- Java 21
- LibGDX as the rendering, input, and audio backend
- LWJGL3 desktop launcher
- Tiled (`.tmx` + `.tsx`) for level authoring
- Gradle multi-module build (`core` + `lwjgl3`)

## Build and run

From the project root, on Windows:

```powershell
.\gradlew.bat :lwjgl3:run
```

On macOS or Linux:

```bash
./gradlew :lwjgl3:run
```

To produce a runnable desktop jar:

```powershell
.\gradlew.bat :lwjgl3:dist
```

The output jar lands in `lwjgl3/build/libs/`. Java 21 or newer is required.

## Project layout

```
core/src/main/java/com/onelastheist/game/
  OneLastHeistGame.java    main LibGDX Game subclass
  app/                     GameContext, ScreenNavigator, shared services
  audio/                   AudioService, MusicId, SfxId
  config/                  GameConfig, ControlConfig, BalanceConfig
  screen/                  splash, main menu, pause, play, ending
  world/                   GameWorld, WorldFactory, CollisionMap, clock,
                           objective tracker, room graph
  entity/
    base/                  Entity, MovableEntity, FacingDirection
    player/                Player, PlayerController, PlayerState
    npc/                   Dog, HomeOwner, NpcState
  ai/                      DogBrain, HomeOwnerBrain, Pathfinder,
                           DetectionService
  environment/             Door, KeyPickup, MeatPickup, MoneyPickup,
                           Newspaper, PianoPuzzle, DroppedMeat
  render/                  WorldRenderer
  ending/, ui/, item/, interaction/, save/, trap/, quest/

lwjgl3/src/main/java/com/onelastheist/game/lwjgl3/
  Lwjgl3Launcher.java
  StartupHelper.java

assets/
  characters/              player + NPC sprite sheets
  items/                   coin, diamond, key, meat, newspaper
  maps/                    Tiled .tmx files and tilesets
  sounds/                  music, SFX, piano notes
  start_screen/            menu and pause UI buttons
  end/                     win and lose splash art
  ui/                      HUD chrome
```

## Architecture

A single LibGDX `Game` instance routes between screens through `ScreenNavigator`. Long-lived services (asset manager, audio mixer, gameplay tuning, control bindings) live on `GameContext` so they survive screen transitions.

```
launcher (lwjgl3)
  -> OneLastHeistGame
       -> GameContext       shared services
       -> ScreenNavigator   routes between screens
            -> MainMenuScreen
            -> PlayScreen
                 -> PlayerController     input handling
                 -> GameWorld            world state
                 -> WorldRenderer        drawing
            -> PauseScreen
            -> EndingScreen
```

`GameWorld` owns the player, NPCs, the active TiledMap, derived collision data, doors, the world clock, and the objective tracker. `WorldFactory` constructs it from on-disk maps and seeds the runtime entities. `WorldRenderer` does the actual drawing in three passes: floor, Y-sorted actors and walls, and overhead canopies.

## Asset pipeline

Maps are authored in [Tiled](https://www.mapeditor.org/) and committed under `assets/maps/`.

- Source tiles are 16 pixels and scaled 3x at runtime, so one tile equals 48 world units.
- Tile layers may be nested in groups; the renderer recurses into them.
- The `Collisions` object layer is the single source of truth for blocking. Every authored rectangle becomes an axis-aligned solid at load time. `CollisionMap` insets each rect by 4 world units to compensate for the slight overdraw artists tend to leave on collision shapes.
- A foreground layer is drawn after the player so canopies and rooftops occlude the character.
- Door rectangles are registered as solids at load time, so the player cannot walk through a door without pressing `E`.

## Audio

`AudioService` is a process-wide mixer that lazily loads each clip on first use. Clips that fail to decode through the LibGDX `Sound` backend (typically because they exceed the in-RAM size limit) automatically fall back to the streamed `Music` backend; failures on both backends are cached so a missing or unreadable file logs once and then degrades to silence. Music tracks are exclusive (one at a time); SFX support both one-shots and de-duped looping playback.

## Contributing notes

- Don't push directly to `main`; branch first.
- Map collisions are authored only in the Tiled `Collisions` object group. Tile-metadata collision is not consulted.
- Gameplay tuning constants belong in `BalanceConfig`; gameplay code should read from there rather than hardcoding values inline.
- Audio assets in `assets/sounds/` should be 16-bit PCM WAVs with no extended RIFF chunks. The bundled clips were re-exported through ffmpeg with metadata stripped to satisfy the LibGDX WAV decoder.

## Credits

A coursework project for an Object-Oriented Programming course. See the per-commit `Co-Authored-By` trailers for the contributor list.
