package com.onelastheist.game.world;

import com.onelastheist.game.environment.Room;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Cac ket noi giua phong trong nha. */
public class RoomGraph {
    private final List<Room> rooms = new ArrayList<>();

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
