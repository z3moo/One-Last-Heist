package com.onelastheist.game.save;

public interface SaveRepository {
    void save(SaveData data);
    SaveData load();
}
