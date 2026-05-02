package com.onelastheist.game.trap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AlarmSystem {
    private final List<AlarmEvent> events = new ArrayList<>();

    public void raise(AlarmEvent event) { events.add(event); }
    public boolean hasActiveAlarm() { return !events.isEmpty(); }
    public List<AlarmEvent> getEvents() { return Collections.unmodifiableList(events); }
    public void clear() { events.clear(); }
}
