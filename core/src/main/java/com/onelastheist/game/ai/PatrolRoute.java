package com.onelastheist.game.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PatrolRoute {
    private final List<String> roomIds = new ArrayList<>();
    public void addRoom(String roomId) { roomIds.add(roomId); }
    public List<String> getRoomIds() { return Collections.unmodifiableList(roomIds); }
}
