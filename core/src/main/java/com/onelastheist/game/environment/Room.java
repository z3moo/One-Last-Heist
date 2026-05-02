package com.onelastheist.game.environment;

import com.onelastheist.game.interaction.Interactable;
import com.onelastheist.game.trap.Trap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Room {
    private final String name;
    private final List<Interactable> interactables = new ArrayList<>();
    private final List<Trap> traps = new ArrayList<>();

    public Room(String name) { this.name = name; }
    public String getName() { return name; }
    public void addInteractable(Interactable interactable) { interactables.add(interactable); }
    public void addTrap(Trap trap) { traps.add(trap); }
    public List<Interactable> getInteractables() { return Collections.unmodifiableList(interactables); }
    public List<Trap> getTraps() { return Collections.unmodifiableList(traps); }
}
