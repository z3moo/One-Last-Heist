package com.onelastheist.game.world;

import com.onelastheist.game.environment.Room;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logical room layout for the main house. Currently a flat list — once room
 * adjacency, locked-door rules, and AI patrol routes need querying, this is
 * where the graph edges should be added.
 */
public class RoomGraph {
    private final List<Room> rooms = new ArrayList<>();

    /** Six-room main house preset. Order matches the planned floor plan. */
    public static RoomGraph createMainHouseGraph() {
        RoomGraph graph = new RoomGraph();
        graph.addRoom(new Room("Living Room"));
        graph.addRoom(new Room("Kitchen"));
        graph.addRoom(new Room("Bedroom"));
        graph.addRoom(new Room("Study"));
        graph.addRoom(new Room("Entertainment Room"));
        graph.addRoom(new Room("Garage"));
        return graph;
    }

    public void addRoom(Room room) { rooms.add(room); }
    public List<Room> getRooms() { return Collections.unmodifiableList(rooms); }
}
