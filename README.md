# One Last Heist

One Last Heist is a work-in-progress Java/LibGDX OOP project. The goal is to build a 2D top-down stealth, adventure, and puzzle game where the player breaks into a mysterious house, collects valuables, avoids traps, hides from danger, and can discover a hidden route with a secret ending.

## Project Status

WIP. The project currently has:

- LibGDX multi-module setup with `core` and `lwjgl3`
- Desktop launcher using LWJGL3
- Main game entry class: `OneLastHeistGame`
- Main menu screen with background and image buttons
- Credits popup with custom themed UI
- Basic screen navigation structure
- Initial OOP package structure for gameplay systems

## Current Entry Flow

```text
lwjgl3 launcher
  -> com.onelastheist.game.OneLastHeistGame
  -> GameContext
  -> ScreenNavigator
  -> MainMenuScreen
```

## Folder Structure

```text
core/src/main/java/com/onelastheist/game/
  OneLastHeistGame.java
  app/
  config/
  screen/
  world/
  map/
  entity/
  interaction/
  item/
  environment/
  trap/
  ai/
  quest/
  ending/
  ui/
  render/
  audio/
  save/

lwjgl3/src/main/java/com/onelastheist/game/lwjgl3/
  Lwjgl3Launcher.java
  StartupHelper.java

assets/
  start_screen/
```

## Implemented So Far

### Main Menu

- Full-screen start background from `assets/start_screen/bg_main_menu.png`
- Play, Credits, and Exit buttons using normal, hover, and pressed images
- Play button opens the current placeholder play screen
- Credits button opens the credits popup
s

## Planned Gameplay

The full game design is still WIP. Planned features include:

- Top-down player movement
- Six connected main house rooms
- Three hidden-route rooms or an equivalent hidden-route sequence
- Money, keys, weapons, and evidence items
- Inventory system
- Locked doors and safes
- Trap reveal and alarm behavior
- Hiding spots
- Homeowner search behavior
- Dog/noise danger system
- Main map timer
- Hidden route without timer pressure
- Final confrontation
- Four endings:
  - Normal escape
  - Caught or time over
  - Hidden route failure
  - True ending

## Controls Plan

Current planned controls:

```text
W - Move up
A - Move left
S - Move down
D - Move right
E - Hide or interact with hiding spot
F - Collect or interact with objects
Left mouse button - Use selected item
```

## How To Run

From the project root:

```powershell
.\gradlew.bat :lwjgl3:run
```

## How To Build

```powershell
.\gradlew.bat build
```

The desktop jar is generated under:

```text
lwjgl3/build/libs/
```

## Development Notes

- Java version: 21
- Framework: LibGDX
- Desktop backend: LWJGL3

## WIP Checklist

Not completed yet:

- Player movement and collision
- Real gameplay map
- Room transitions
- Item placement and collection
- Inventory UI
- Trap trigger system
- Alarm event flow
- Hiding system
- Homeowner behavior
- Dog/noise system
- Hidden route
- Final encounter
- Ending screens
- Save/load
- Audio integration
- Polished HUD
- Unit tests for domain rules

## Contributors

