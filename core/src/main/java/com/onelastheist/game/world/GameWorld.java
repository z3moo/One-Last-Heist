package com.onelastheist.game.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.onelastheist.game.ai.DogBrain;
import com.onelastheist.game.ai.HomeOwnerBrain;
import com.onelastheist.game.config.BalanceConfig;
import com.onelastheist.game.entity.npc.Dog;
import com.onelastheist.game.entity.npc.HomeOwner;
import com.onelastheist.game.entity.player.Player;
import com.onelastheist.game.environment.BodyPartPuzzle;
import com.onelastheist.game.environment.DroppedMeat;
import com.onelastheist.game.environment.KeyPickup;
import com.onelastheist.game.environment.MeatPickup;
import com.onelastheist.game.environment.MoneyPickup;
import com.onelastheist.game.environment.Newspaper;
import com.onelastheist.game.environment.PianoPuzzle;
import com.onelastheist.game.item.Inventory;
import com.onelastheist.game.item.Item;
import com.onelastheist.game.item.ItemType;
import com.onelastheist.game.item.Meat;
import com.onelastheist.game.item.MoneyItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Runtime model of the active heist. Bundles every long-lived gameplay object
 * — player, NPCs, clock, objectives, the active TiledMap,
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
    private final WorldClock clock;
    private final ObjectiveTracker objectives;

    private WorldFactory.MapBundle exterior;
    private WorldFactory.MapBundle interior;
    private WorldFactory.MapBundle sideHouse;
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
    /**
     * The homeowner's brain. Constructed on first use (when the clock
     * crosses {@link BalanceConfig#homeOwnerArrivalSecondsRemaining} and
     * the player has gone inside at least once). Stays alive after that
     * even across map swaps.
     */
    private HomeOwnerBrain homeOwnerBrain;
    /** True once the player has entered the main house at least once — gates homeowner arrival. */
    private boolean playerHasEnteredHouse;
    /** Pre-placed meat the player can pick up in the main house. */
    private final List<MeatPickup> interiorPickups = new ArrayList<>();
    /** Pre-placed keys the player can pick up in the active map. */
    private final List<KeyPickup> keyPickups = new ArrayList<>();
    /**
     * Money pickups per map, kept persistent across map transitions so a
     * coin picked up on one visit is still gone the next. Each list is
     * initialized once from its bundle's snapshot the first time the bundle
     * loads, then mutated by {@link #tryPickUpMoney()} as the player
     * collects items.
     */
    private final List<MoneyPickup> exteriorMoney = new ArrayList<>();
    private final List<MoneyPickup> interiorMoney = new ArrayList<>();
    private final List<MoneyPickup> sideHouseMoney = new ArrayList<>();
    /** Meat the player has dropped on the main-house floor. */
    private final List<DroppedMeat> interiorDroppedMeat = new ArrayList<>();
    /** Counts down each frame for the duration of the white-flash + "-30s" notification after a bite. */
    private float biteFlashTimer;

    public GameWorld(WorldFactory factory, BalanceConfig balance, Player player, HomeOwner homeOwner, Dog dog,
                     WorldClock clock, ObjectiveTracker objectives,
                     WorldFactory.MapBundle exterior) {
        this.factory = factory;
        this.balance = balance;
        this.player = player;
        this.homeOwner = homeOwner;
        this.dog = dog;
        this.clock = clock;
        this.objectives = objectives;
        this.exterior = exterior;
        this.active = exterior;
        // Money on the exterior is live from spawn so the player can grab a
        // few coins before entering the house. Interior/storage maps
        // populate their lists when their bundle first loads.
        this.exteriorMoney.addAll(exterior.moneyPickups);
    }

    /**
     * Per-frame tick. Advances the world clock, the dog AI (interior only),
     * the homeowner AI (when active), and purges any meat the dog has
     * consumed this frame.
     */
    public void update(float deltaSeconds) {
        clock.update(deltaSeconds);
        if (biteFlashTimer > 0f) biteFlashTimer = Math.max(0f, biteFlashTimer - deltaSeconds);
        // Homeowner arrival trigger. Idempotent — startApproach() is a no-op
        // after the first invocation. Requires the player to have stepped
        // inside the house at least once so a player who never goes in
        // doesn't summon him.
        if (homeOwnerBrain == null
            && playerHasEnteredHouse
            && clock.getRemainingSeconds() <= balance.homeOwnerArrivalSecondsRemaining) {
            initializeHomeOwnerBrain();
        }
        if (homeOwnerBrain != null) {
            // The brain operates on whichever map the homeowner currently
            // sits on. Player is on `active`; same-map detection only
            // makes sense when both stand on the same TiledMap.
            CollisionMap homeOwnerMap = homeOwner.isOnInterior()
                ? (interior == null ? null : interior.collisionMap)
                : exterior.collisionMap;
            boolean playerOnSameMap = homeOwner.isOnInterior() ? isInMainInterior() : !inInterior;
            homeOwnerBrain.update(deltaSeconds, player, homeOwnerMap, playerOnSameMap);
        }
        if (isInMainInterior() && dogBrain != null) {
            dogBrain.update(deltaSeconds, player, interiorDroppedMeat);
            for (Iterator<DroppedMeat> it = interiorDroppedMeat.iterator(); it.hasNext(); ) {
                if (it.next().isConsumed()) it.remove();
            }
        }
    }

    /**
     * Build the homeowner's brain on first arrival. Fixed approach target
     * (the front door of the main house) and interior spawn (the entry
     * carpet just inside that door) — the world layer is the only place
     * that knows those positions, so it owns the wiring.
     */
    private void initializeHomeOwnerBrain() {
        homeOwnerBrain = new HomeOwnerBrain(homeOwner, balance);
        // Approach target: a lawn cell two tiles south of the front door.
        // The door at TMX (1824, 1152, 96, 48) is registered as a solid via
        // registerDoorsAsSolids, AND a small "threshold" collision rect
        // (id=286 at world (1877, 1097, 39, 13)) sits one tile south of it.
        // Both are non-walkable for the homeowner's hitbox, so BFS rejected
        // anything closer. (1800, 1000) puts the hitbox center at (1872, 1030)
        // — door-centered horizontally, two tiles south on clean grass. The
        // DOOR_ENTER_RADIUS fires when he steps onto the south porch tile;
        // the timeout fallback covers any residual edge case.
        homeOwnerBrain.setApproachTarget(1800f, 1000f);
        // Interior spawn: north of the entry carpet so the player sees him
        // appear inside the house, not at the door. Earlier (1320, 1500)
        // sat with the homeowner's hitbox overlapping the door rect (which
        // is registered as a solid via registerDoorsAsSolids), so BFS in
        // HUNTING immediately rejected the start cell and the brain
        // produced no waypoints — he'd just stand there. (1320, 1620)
        // puts him a couple of tiles into the entry hall, comfortably
        // clear of the door, with a connected BFS region for wandering.
        homeOwnerBrain.setInteriorSpawn(1320f, 1620f);
        // Wander bounds: same as the dog's, the full playable interior.
        homeOwnerBrain.setInteriorWanderBounds(new Rectangle(432f, 864f, 3216f, 2304f));
        homeOwnerBrain.setOnCatch(this::applyCatch);
        homeOwnerBrain.setOnEnterInterior(this::homeOwnerStepInside);
        // Spawn pose: inside the garden, south of the houses but north of
        // the perimeter fence at world Y ~ 341. The player's exterior spawn
        // (520, 280) sits SOUTH of that fence — pathing from there would
        // need to thread the only gate at X 2059-2175, doable but flaky.
        // (1800, 600) is in the open garden corridor with a clean BFS line
        // straight up to the door. He'll appear visibly, not just teleport.
        homeOwner.setPosition(1800f, 600f);
        homeOwner.setOnInterior(false);
        homeOwnerBrain.startApproach(exterior.collisionMap);
    }

    /**
     * Hook fired by {@link HomeOwnerBrain} when its APPROACHING phase
     * reaches the door. Force-loads the interior bundle if the player
     * never entered (so the homeowner has a map + collision to walk),
     * then teleports the homeowner inside and flips his brain into HUNTING.
     */
    private void homeOwnerStepInside() {
        if (interior == null) {
            interior = factory.loadInteriorBundle();
            dog.setPosition(interior.dogSpawnX, interior.dogSpawnY);
            dog.setVisible(true);
            dogBrain = new DogBrain(dog, balance, interior.dogWanderBounds, interior.collisionMap);
            dogBrain.setOnBite(this::applyBitePenalty);
            interiorPickups.clear();
            interiorPickups.addAll(interior.meatPickups);
            keyPickups.clear();
            keyPickups.addAll(interior.keyPickups);
            interiorMoney.addAll(interior.moneyPickups);
        }
        homeOwner.setPosition(homeOwnerBrain.getInteriorSpawnX(), homeOwnerBrain.getInteriorSpawnY());
        homeOwnerBrain.enterInterior(interior.collisionMap);
    }

    /** Hook fired when the homeowner grabs the player. Flags player CAUGHT. */
    private void applyCatch() {
        player.catchPlayer();
    }

    /**
     * Apply the bite penalty: deduct {@link BalanceConfig#bitePenaltySeconds}
     * from the world clock and start the "-30s" / white-flash window. The
     * dog AI calls this via the {@code onBite} hook installed in
     * {@link #enterInterior()}.
     */
    private void applyBitePenalty() {
        clock.deduct(balance.bitePenaltySeconds);
        biteFlashTimer = balance.biteFlashSeconds;
    }

    /** True while the post-bite white flash + "-30s" floating text should display. */
    public boolean isBiteFlashActive() { return biteFlashTimer > 0f; }
    /** Normalized [0, 1] strength of the bite flash, 1 immediately after the bite, 0 when expired. */
    public float getBiteFlashStrength() {
        if (balance.biteFlashSeconds <= 0f) return 0f;
        return Math.max(0f, biteFlashTimer / balance.biteFlashSeconds);
    }
    /** Penalty value (positive seconds) shown in the "-30s" notification. */
    public float getBitePenaltySeconds() { return balance.bitePenaltySeconds; }

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
        if (!isInMainInterior()) return false;
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

    /** Pick up the closest key the player is standing on, if any. */
    public boolean tryPickUpKey() {
        if (keyPickups.isEmpty()) return false;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        for (Iterator<KeyPickup> it = keyPickups.iterator(); it.hasNext(); ) {
            KeyPickup pickup = it.next();
            float dx = pickup.getX() - pcx;
            float dy = pickup.getY() - pcy;
            if (dx * dx + dy * dy <= 48f * 48f) {
                pickup.collectInto(player.getInventory());
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Pick up the closest coin/diamond the player is standing on, if any.
     * Adds the underlying {@link MoneyItem}'s value to the {@link ObjectiveTracker}
     * so the HUD counter reflects the new total this frame. Same proximity
     * threshold as {@link #tryPickUpMeat()}.
     */
    public boolean tryPickUpMoney() {
        List<MoneyPickup> list = activeMoneyList();
        if (list.isEmpty()) return false;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        for (Iterator<MoneyPickup> it = list.iterator(); it.hasNext(); ) {
            MoneyPickup pickup = it.next();
            float dx = pickup.getX() - pcx;
            float dy = pickup.getY() - pcy;
            if (dx * dx + dy * dy <= 48f * 48f) {
                pickup.collectInto(player.getInventory());
                objectives.addMoney(pickup.getValue());
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Returns the mutable money list backing the currently-active map. */
    private List<MoneyPickup> activeMoneyList() {
        if (active == interior) return interiorMoney;
        if (active == sideHouse) return sideHouseMoney;
        return exteriorMoney;
    }

    /**
     * Drop a piece of meat from the player's inventory at their feet. No-op if
     * they aren't carrying any. Drugged meat is preferred — drops only the
     * drugged stack first since that's what the player actually intends to use.
     */
    public boolean tryDropMeat() {
        if (!isInMainInterior()) return false;
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
        boolean firstLoad = interior == null;
        if (firstLoad) {
            interior = factory.loadInteriorBundle();
            // First-time setup of the dog's room and brain. Builder gave us a
            // wander rect alongside the bundle; we own the brain so that it
            // can be reused across re-entries without rebuilding.
            dog.setPosition(interior.dogSpawnX, interior.dogSpawnY);
            dog.setVisible(true);
            dogBrain = new DogBrain(dog, balance, interior.dogWanderBounds, interior.collisionMap);
            // Bite -> bite penalty (clock deduction + flash). The dog AI is
            // intentionally agnostic of clock/HUD; this hook bridges the two.
            dogBrain.setOnBite(this::applyBitePenalty);
            interiorPickups.clear();
            interiorPickups.addAll(interior.meatPickups);
            keyPickups.clear();
            keyPickups.addAll(interior.keyPickups);
            interiorMoney.addAll(interior.moneyPickups);
        }
        exteriorReturnX = player.getX();
        exteriorReturnY = player.getY();
        active = interior;
        inInterior = true;
        playerHasEnteredHouse = true;
        mapVersion++;
        player.setPosition(interior.spawnX, interior.spawnY);
        // Wipe any meat dropped on a previous interior visit; the dog should
        // not chase week-old chunks of meat across loads.
        interiorDroppedMeat.clear();
    }

    /** Enter the small storage-house interior after its exterior door is unlocked. */
    public void enterSideHouse() {
        if (inInterior) return;
        boolean firstLoad = sideHouse == null;
        if (firstLoad) {
            sideHouse = factory.loadSideHouseBundle();
            sideHouseMoney.addAll(sideHouse.moneyPickups);
        }
        exteriorReturnX = player.getX();
        exteriorReturnY = player.getY();
        active = sideHouse;
        inInterior = true;
        mapVersion++;
        // Spawn the player just inside the entry door, then verify the cell
        // is actually walkable — the storage TMX has a few hand-authored
        // collisions in the entry that can clip the hitbox depending on the
        // exact spawn pixel. If the authored spawn fails the rect probe,
        // sweep outward in a small spiral until we land on a clear cell.
        float sx = sideHouse.spawnX;
        float sy = sideHouse.spawnY;
        if (isHitboxBlocked(sideHouse.collisionMap, sx, sy)) {
            float[] rescued = findClearSpawn(sideHouse.collisionMap, sx, sy);
            if (rescued != null) { sx = rescued[0]; sy = rescued[1]; }
        }
        player.setPosition(sx, sy);
    }

    /** True if the player's hitbox at this entity-origin overlaps any solid. */
    private boolean isHitboxBlocked(CollisionMap map, float entityX, float entityY) {
        return map.rectCollides(
            entityX + player.getHitboxOffsetX(),
            entityY + player.getHitboxOffsetY(),
            player.getHitboxWidth(),
            player.getHitboxHeight());
    }

    /**
     * Spiral outward from {@code (cx,cy)} looking for an entity origin where
     * the player's hitbox fits. Searches up to 6 tiles in each direction at
     * 24-wu (half-tile) granularity. Returns null if everything in range is
     * blocked, in which case the caller should leave the spawn alone and
     * accept whatever pinning happens — a known issue is better than a
     * silent teleport.
     */
    private float[] findClearSpawn(CollisionMap map, float cx, float cy) {
        float step = 24f;
        int maxRings = 12; // 12 * 24 = 288 wu = 6 tiles
        // Bound the candidate origin so the hitbox stays fully inside the
        // map — without this, a wide-radius search at the edge of a small
        // map could return a coord that puts the player in OOB territory
        // where rectCollides may report "clear" because no solid is there.
        float maxX = map.getWorldWidth() - player.getHitboxWidth() - player.getHitboxOffsetX();
        float maxY = map.getWorldHeight() - player.getHitboxHeight() - player.getHitboxOffsetY();
        float minX = -player.getHitboxOffsetX();
        float minY = -player.getHitboxOffsetY();
        for (int ring = 1; ring <= maxRings; ring++) {
            float r = ring * step;
            // 8 cardinal/diagonal directions per ring is plenty for a small room.
            float[][] offsets = {
                {0, r}, {0, -r}, {r, 0}, {-r, 0},
                {r, r}, {r, -r}, {-r, r}, {-r, -r}
            };
            for (float[] o : offsets) {
                float nx = cx + o[0];
                float ny = cy + o[1];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) continue;
                if (!isHitboxBlocked(map, nx, ny)) return new float[] {nx, ny};
            }
        }
        return null;
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
    public WorldClock getClock() { return clock; }
    public ObjectiveTracker getObjectives() { return objectives; }
    public TiledMap getTiledMap() { return active.tiledMap; }
    public CollisionMap getCollisionMap() { return active.collisionMap; }
    public List<Door> getDoors() { return active.doors == null ? Collections.<Door>emptyList() : active.doors; }
    public boolean isInInterior() { return inInterior; }
    public boolean isInMainInterior() { return inInterior && active == interior; }
    public boolean hasActiveDog() { return isInMainInterior() && dog.isVisible(); }
    public boolean hasSideHouseKey() { return player.getInventory().containsItem(WorldFactory.SIDE_HOUSE_KEY_ID); }
    public int getMapVersion() { return mapVersion; }
    public List<MeatPickup> getInteriorPickups() { return isInMainInterior() ? Collections.unmodifiableList(interiorPickups) : Collections.<MeatPickup>emptyList(); }
    public List<DroppedMeat> getInteriorDroppedMeat() { return isInMainInterior() ? Collections.unmodifiableList(interiorDroppedMeat) : Collections.<DroppedMeat>emptyList(); }
    public List<KeyPickup> getKeyPickups() { return isInMainInterior() ? Collections.unmodifiableList(keyPickups) : Collections.<KeyPickup>emptyList(); }
    /** Money pickups visible on the currently-active map. */
    public List<MoneyPickup> getMoneyPickups() { return Collections.unmodifiableList(activeMoneyList()); }
    /** Newspapers on the currently-active map (non-empty only on the main interior for now). */
    public List<Newspaper> getNewspapers() {
        return isInMainInterior() && interior != null
            ? interior.newspapers
            : Collections.<Newspaper>emptyList();
    }
    /** Piano puzzles on the currently-active map. Only the main interior has one. */
    public List<PianoPuzzle> getPianoPuzzles() {
        return isInMainInterior() && interior != null
            ? interior.pianoPuzzles
            : Collections.<PianoPuzzle>emptyList();
    }
    /**
     * Returns the first piano whose interact radius the player is inside,
     * or null. PlayScreen drives the prompt + overlay from this.
     */
    public PianoPuzzle findActivePiano() {
        List<PianoPuzzle> list = getPianoPuzzles();
        if (list.isEmpty()) return null;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        for (PianoPuzzle p : list) {
            if (p.playerInRange(pcx, pcy)) return p;
        }
        return null;
    }
    /** Body-part puzzles on the currently-active map. Only the side house has them. */
    public List<BodyPartPuzzle> getBodyPartPuzzles() {
        return active == sideHouse && sideHouse != null
            ? sideHouse.bodyPartPuzzles
            : Collections.<BodyPartPuzzle>emptyList();
    }
    /** First body-part puzzle in player's interact range, or null. */
    public BodyPartPuzzle findActiveBodyPart() {
        List<BodyPartPuzzle> list = getBodyPartPuzzles();
        if (list.isEmpty()) return null;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        for (BodyPartPuzzle p : list) {
            if (p.playerInRange(pcx, pcy)) return p;
        }
        return null;
    }
    /**
     * True when every body-part puzzle on the side-house map has been
     * solved correctly. Drives the hidden WIN1 trigger; the side-house
     * bundle is loaded lazily, so this is also false until the player has
     * stepped into the storage at least once.
     */
    public boolean areAllBodyPartsSolved() {
        if (sideHouse == null) return false;
        List<BodyPartPuzzle> list = sideHouse.bodyPartPuzzles;
        if (list.isEmpty()) return false;
        for (BodyPartPuzzle p : list) if (!p.isSolved()) return false;
        return true;
    }
    /**
     * Deduct {@code seconds} from the world clock and trigger the
     * white-flash + "-N seconds" notification. Reuses the bite penalty
     * timer so the existing PlayScreen overlay renders the float-up text
     * unchanged. Used by the body-part puzzle on a wrong answer.
     */
    public void applyTimePenalty(float seconds) {
        clock.deduct(seconds);
        biteFlashTimer = balance.biteFlashSeconds;
    }
    /**
     * Award the storage-house key. Called by PlayScreen the moment the
     * piano puzzle is solved. Idempotent — solving twice (which the puzzle
     * itself prevents) won't add duplicates because the key is unique.
     */
    public void awardStorageKey() {
        if (player.getInventory().containsItem(WorldFactory.SIDE_HOUSE_KEY_ID)) return;
        player.getInventory().add(factory.items().key(WorldFactory.SIDE_HOUSE_KEY_ID, "Storage Key"));
    }
    /**
     * Returns the first newspaper in range of the player, or {@code null}.
     * Mirrors {@link #findActiveDoor(float)} so {@link com.onelastheist.game.screen.PlayScreen}
     * can show an "E to Read" prompt with the same idiom.
     */
    public Newspaper findActiveNewspaper() {
        List<Newspaper> list = getNewspapers();
        if (list.isEmpty()) return null;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        for (Newspaper n : list) {
            if (n.playerInRange(pcx, pcy)) return n;
        }
        return null;
    }
    /** Live homeowner brain, or null if the homeowner hasn't been activated yet. */
    public HomeOwnerBrain getHomeOwnerBrain() { return homeOwnerBrain; }
    /** True if the player has ever stepped into the main house. Gates the homeowner arrival and the escape-win condition. */
    public boolean hasPlayerEnteredHouse() { return playerHasEnteredHouse; }
    /** True if the homeowner is on the same map as the player (used by the renderer to decide drawing). */
    public boolean isHomeOwnerVisibleHere() {
        if (homeOwnerBrain == null || !homeOwner.isVisible()) return false;
        return homeOwner.isOnInterior() ? isInMainInterior() : !inInterior;
    }
    /**
     * True when the player is on the exterior map and has walked outside
     * the fenced garden bounds — i.e. through the south-fence gap onto
     * the road. Used by PlayScreen as the "escape" trigger for the win
     * ending. Always false on interior maps.
     */
    public boolean isPlayerOutsideGarden() {
        if (inInterior) return false;
        float pcx = player.getX() + player.getHitboxOffsetX() + player.getHitboxWidth() / 2f;
        float pcy = player.getY() + player.getHitboxOffsetY() + player.getHitboxHeight() / 2f;
        return !WorldFactory.EXTERIOR_GARDEN_BOUNDS.contains(pcx, pcy);
    }

    /** Releases every loaded TiledMap. Other fields are POJOs that the GC handles. */
    @Override
    public void dispose() {
        if (exterior != null && exterior.tiledMap != null) exterior.tiledMap.dispose();
        if (interior != null && interior.tiledMap != null) interior.tiledMap.dispose();
        if (sideHouse != null && sideHouse.tiledMap != null) sideHouse.tiledMap.dispose();
    }
}
