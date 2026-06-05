package com.onelastheist.game.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.ai.DogBrain;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.environment.DroppedMeat;
import com.onelastheist.game.environment.MeatPickup;
import com.onelastheist.game.item.Inventory;
import com.onelastheist.game.item.Item;
import com.onelastheist.game.item.ItemType;
import com.onelastheist.game.item.Meat;
import com.onelastheist.game.trap.AlarmSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Runtime model of the active heist. Bundles every long-lived gameplay object
 * — player, NPCs, rooms, clock, objectives, alarm system, the active TiledMap,
 * its derived collision data, and the door definitions — behind a single handle
 * that screens and renderers can consume.
 *
 * <p>Construction is delegated to {@link WorldFactory}; once built, screens
 * call {@link #update(float)} every frame and read the rest as needed. Rendering
 * is intentionally kept out of this class — see {@link com.onelastheist.game.render.WorldRenderer}.
 *
 * <p>The active map is swappable: {@link #enterInterior()} loads the house
 * interior on first use (cached afterwards) and replaces the active map, collision
 * data, door list, dog AI, meat pickups, and dropped meat. {@link #returnToExterior()}
 * swaps back and restores the exterior position the player held when they entered.
 * Renderers detect a swap via {@link #getMapVersion()} so they can rebuild their
 * tile renderer.
 *
 * <p>Dropped meat is owned per-map: meat dropped indoors stays indoors, and
 * leaves no trail outside. The dog AI ticks only while the interior is active —
 * the dog doesn't exist outdoors.
 */
public class GameWorld implements Disposable {
    private final WorldFactory factory;
    private final BalanceConfig balance;
    private final Player player;
    private final HomeOwner homeOwner;
    private final Dog dog;
    private final RoomGraph roomGraph;
    private final WorldClock clock;
    private final ObjectiveTracker objectives;
    private final AlarmSystem alarmSystem;

    private WorldFactory.MapBundle exterior;
    private WorldFactory.MapBundle interior;
    private WorldFactory.MapBundle active;
    /** True while the interior map is active. */
    private boolean inInterior;
    /** Where the player stood on the exterior when they last entered the interior. */
    private float exteriorReturnX;
    private float exteriorReturnY;
    /** Bumped on every map swap so renderers can detect when to rebuild. */
    private int mapVersion;

    /** The dog's brain, alive only on the interior map. Recreated when the interior bundle is built. */
    private DogBrain dogBrain;
    /** Pre-placed meat the player can pick up. Per-map, like dropped meat. */
    private final List<MeatPickup> interiorPickups = new ArrayList<>();
    /** Meat the player has dropped on the interior floor. */
    private final List<DroppedMeat> interiorDroppedMeat = new ArrayList<>();

    public GameWorld(WorldFactory factory, BalanceConfig balance, Player player, HomeOwner homeOwner, Dog dog,
                     RoomGraph roomGraph, WorldClock clock, ObjectiveTracker objectives,
                     AlarmSystem alarmSystem, WorldFactory.MapBundle exterior) {
        this.factory = factory;
        this.balance = balance;
        this.player = player;
        this.homeOwner = homeOwner;
        this.dog = dog;
        this.roomGraph = roomGraph;
        this.clock = clock;
        this.objectives = objectives;
        this.alarmSystem = alarmSystem;
        this.exterior = exterior;
        this.active = exterior;
    }

    /**
     * Per-frame tick. Advances the world clock, the dog AI (interior only),
     * and purges any meat the dog has consumed this frame.
     */
    public void update(float deltaSeconds) {
        clock.update(deltaSeconds);
        if (inInterior && dogBrain != null) {
            dogBrain.update(deltaSeconds, player, interiorDroppedMeat);
            for (Iterator<DroppedMeat> it = interiorDroppedMeat.iterator(); it.hasNext(); ) {
                if (it.next().isConsumed()) it.remove();
            }
        }
    }

    /**
     * Returns the first door whose bounds, expanded by {@code interactRadius},
     * overlap the player's hitbox — or {@code null} if the player is not near
     * any door. Used by {@link com.onelastheist.game.screen.PlayScreen} to decide
     * whether to draw the interact prompt and respond to the E key.
     */
    public Door findActiveDoor(float interactRadius) {
        List<Door> doors = active.doors;
        if (doors.isEmpty()) return null;
        float px = player.getX() + player.getHitboxOffsetX();
        float py = player.getY() + player.getHitboxOffsetY();
        float pw = player.getHitboxWidth();
        float ph = player.getHitboxHeight();
        for (Door door : doors) {
            if (door.playerInRange(px, py, pw, ph, interactRadius)) return door;
        }
        return null;
    }

    /**
     * Pick up the closest meat the player is standing on, if any. Returns true
     * if a pickup was consumed. Pickups beyond a tile or so are ignored — the
     * player must literally be on top of one. Both pre-placed pickups <em>and</em>
     * meat the player previously dropped on the floor count: walking back over
     * a dropped chunk lets the player reclaim it.
     */
    public boolean tryPickUpMeat() {
        if (!inInterior) return false;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        // Pre-placed pickups first.
        for (Iterator<MeatPickup> it = interiorPickups.iterator(); it.hasNext(); ) {
            MeatPickup pickup = it.next();
            float dx = pickup.getX() - pcx;
            float dy = pickup.getY() - pcy;
            if (dx * dx + dy * dy <= 48f * 48f) {
                pickup.collectInto(player.getInventory());
                it.remove();
                return true;
            }
        }
        // Then anything the player previously dropped. Skip consumed entries —
        // those are the ones the dog already ate; they're scheduled for purge.
        for (Iterator<DroppedMeat> it = interiorDroppedMeat.iterator(); it.hasNext(); ) {
            DroppedMeat dropped = it.next();
            if (dropped.isConsumed()) continue;
            float dx = dropped.getX() - pcx;
            float dy = dropped.getY() - pcy;
            if (dx * dx + dy * dy <= 48f * 48f) {
                player.getInventory().add(dropped.getMeat());
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Drop a piece of meat from the player's inventory at their feet. No-op if
     * they aren't carrying any. Drugged meat is preferred — drops only the
     * drugged stack first since that's what the player actually intends to use.
     */
    public boolean tryDropMeat() {
        if (!inInterior) return false;
        Inventory inv = player.getInventory();
        Meat toDrop = null;
        for (Item item : inv.getItems()) {
            if (item instanceof Meat) {
                Meat meat = (Meat) item;
                if (meat.isDrugged()) { toDrop = meat; break; }
                if (toDrop == null) toDrop = meat;
            }
        }
        if (toDrop == null) return false;
        inv.removeFirstMatching(toDrop);

        float dropX = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float dropY = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        interiorDroppedMeat.add(new DroppedMeat(toDrop, dropX, dropY));
        return true;
    }

    /** True if the player has at least one Meat item to drop. Used by HUD. */
    public boolean playerHasMeat() {
        for (Item item : player.getInventory().getItems()) {
            if (item.getType() == ItemType.CONSUMABLE && item instanceof Meat) return true;
        }
        return false;
    }

    /**
     * Swap to the interior map, lazy-loading it on first call. Saves the player's
     * current exterior position so {@link #returnToExterior()} can restore it,
     * then teleports the player to the interior's spawn point. Resets dropped
     * meat (a fresh entry should not see meat from a previous trip).
     */
    public void enterInterior() {
        if (inInterior) return;
        if (interior == null) {
            interior = factory.loadInteriorBundle();
            // First-time setup of the dog's room and brain. Builder gave us a
            // wander rect alongside the bundle; we own the brain so that it
            // can be reused across re-entries without rebuilding.
            dog.setPosition(interior.dogSpawnX, interior.dogSpawnY);
            dog.setVisible(true);
            dogBrain = new DogBrain(dog, balance, interior.dogWanderBounds, interior.collisionMap);
            interiorPickups.clear();
            interiorPickups.addAll(interior.meatPickups);
        }
        exteriorReturnX = player.getX();
        exteriorReturnY = player.getY();
        active = interior;
        inInterior = true;
        mapVersion++;
        player.setPosition(interior.spawnX, interior.spawnY);
        // Wipe any meat dropped on a previous interior visit; the dog should
        // not chase week-old chunks of meat across loads.
        interiorDroppedMeat.clear();
    }

    /**
     * Swap back to the exterior. The player reappears at the exact spot they
     * occupied when they entered the house — so the door doesn't appear to
     * teleport them across the lawn.
     */
    public void returnToExterior() {
        if (!inInterior) return;
        active = exterior;
        inInterior = false;
        mapVersion++;
        player.setPosition(exteriorReturnX, exteriorReturnY);
    }

    public Player getPlayer() { return player; }
    public HomeOwner getHomeOwner() { return homeOwner; }
    public Dog getDog() { return dog; }
    public RoomGraph getRoomGraph() { return roomGraph; }
    public WorldClock getClock() { return clock; }
    public ObjectiveTracker getObjectives() { return objectives; }
    public AlarmSystem getAlarmSystem() { return alarmSystem; }
    public TiledMap getTiledMap() { return active.tiledMap; }
    public CollisionMap getCollisionMap() { return active.collisionMap; }
    public List<Door> getDoors() { return active.doors == null ? Collections.<Door>emptyList() : active.doors; }
    public boolean isInInterior() { return inInterior; }
    public int getMapVersion() { return mapVersion; }
    public List<MeatPickup> getInteriorPickups() { return Collections.unmodifiableList(interiorPickups); }
    public List<DroppedMeat> getInteriorDroppedMeat() { return Collections.unmodifiableList(interiorDroppedMeat); }

    /** Releases every loaded TiledMap. Other fields are POJOs that the GC handles. */
    @Override
    public void dispose() {
        if (exterior != null && exterior.tiledMap != null) exterior.tiledMap.dispose();
        if (interior != null && interior.tiledMap != null) interior.tiledMap.dispose();
    }
}
