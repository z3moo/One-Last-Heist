package com.onelastheist.game.map;

public record DoorDefinition(String fromRoomId, String toRoomId, String requiredKeyId, boolean hidden) {}
