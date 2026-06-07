package com.onelastheist.game.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Process-wide audio mixer. Owns one backend per {@link SfxId} (either a
 * fully-loaded {@link Sound} or a streamed {@link Music}) and one
 * {@link Music} per {@link MusicId}, both lazily loaded on first use.
 *
 * <p>Two playback modes for SFX:
 * <ul>
 *   <li>{@link #playSfx(SfxId)} fires a one-shot. With the Sound backend,
 *       multiple instances overlap. With the Music backend, the previous
 *       instance is interrupted (Music tracks one playback at a time);
 *       acceptable for the long footstep-style clips that fall back here.</li>
 *   <li>{@link #setLoop(SfxId, boolean)} maintains exactly one looping
 *       instance per id. Caller flips the boolean each frame; the service
 *       de-dupes redundant on/off calls.</li>
 * </ul>
 *
 * <p>Music switching is exclusive — picking a new track stops the previous one.
 *
 * <p><b>Long WAV support.</b> LibGDX's {@code Sound} class fully decodes the
 * file into RAM and rejects WAVs that are too long (or in encodings it can't
 * fast-decode). When that happens the loader falls back to {@code Music}
 * (streamed from disk), so a long SFX like {@code Footsteps_Thief.wav}
 * still plays — just through a streamed pipeline. Failures are cached so a
 * file that fails both backends doesn't spam its error log every frame.
 *
 * <p><b>Per-SFX volume scaling.</b> Loop SFX (footsteps) play continuously
 * and are scaled down so they don't crowd out one-shots. See {@code perSfxScale}.
 */
public class AudioService implements Disposable {
    private static final String TAG = "AudioService";
    private static final String SOUND_DIR = "sounds/";

    /**
     * Holds whichever backend successfully loaded for a given SFX. Exactly
     * one of {@link #sound} / {@link #music} is non-null.
     */
    private static final class SfxBackend {
        final Sound sound;
        final Music music;
        SfxBackend(Sound s) { this.sound = s; this.music = null; }
        SfxBackend(Music m) { this.sound = null; this.music = m; }
    }

    private final EnumMap<SfxId, SfxBackend> sfxBackends = new EnumMap<>(SfxId.class);
    private final EnumMap<SfxId, Long> activeLoops = new EnumMap<>(SfxId.class);
    /** Ids whose load failed on both backends. Re-loading them is silent and instant. */
    private final EnumSet<SfxId> failedSounds = EnumSet.noneOf(SfxId.class);

    private final EnumMap<MusicId, Music> musicTracks = new EnumMap<>(MusicId.class);
    private MusicId currentMusic;

    /**
     * Per-id volume scaling applied on top of the global SFX volume. Use to
     * pull constantly-playing loops (footsteps) below one-shots so the mix
     * doesn't fatigue. Defaults to 1.0 for unmapped ids.
     */
    private final EnumMap<SfxId, Float> perSfxScale = new EnumMap<>(SfxId.class);

    private float sfxVolume = 0.30f;
    private float musicVolume = 0.30f;

    public AudioService() {
        // Loops play whenever the actor moves; tame them well below the
        // one-shot tier so a walking thief doesn't drown out coin pickups.
        perSfxScale.put(SfxId.FOOTSTEPS_THIEF, 0.45f);
        perSfxScale.put(SfxId.FOOTSTEPS_HOMEOWNER, 0.55f);
        perSfxScale.put(SfxId.DOG, 0.80f);
        perSfxScale.put(SfxId.CAR_ARRIVE, 0.85f);
        // Piano notes peak around 0.55 in the synth pass; pull down so a
        // 5-note sequence at default volume doesn't dominate the mix.
        perSfxScale.put(SfxId.NOTE_C, 0.70f);
        perSfxScale.put(SfxId.NOTE_D, 0.70f);
        perSfxScale.put(SfxId.NOTE_E, 0.70f);
        perSfxScale.put(SfxId.NOTE_F, 0.70f);
        perSfxScale.put(SfxId.NOTE_G, 0.70f);
        perSfxScale.put(SfxId.NOTE_A, 0.70f);
        perSfxScale.put(SfxId.NOTE_B, 0.70f);
    }

    /** Plays a one-shot SFX. Safe to call every frame. Sound-backed instances overlap. */
    public void playSfx(SfxId sfxId) {
        SfxBackend b = loadSfx(sfxId);
        if (b == null) return;
        float vol = sfxVolumeFor(sfxId);
        if (b.sound != null) {
            b.sound.play(vol);
        } else {
            // Music backend can only have one playback at a time. Restart from
            // the beginning so successive calls feel responsive even though
            // the previous instance is interrupted.
            b.music.stop();
            b.music.setLooping(false);
            b.music.setVolume(vol);
            b.music.play();
        }
    }

    /**
     * Toggle a single looping instance of {@code sfxId} on or off. Safe to
     * call every frame in either direction; the service de-dupes so a
     * looping clip never restarts mid-stride and an off-call on something
     * already silent is a no-op.
     */
    public void setLoop(SfxId sfxId, boolean shouldLoop) {
        SfxBackend b = loadSfx(sfxId);
        if (b == null) return;
        float vol = sfxVolumeFor(sfxId);
        if (b.sound != null) {
            Long handle = activeLoops.get(sfxId);
            if (shouldLoop) {
                if (handle != null) return;
                long h = b.sound.loop(vol);
                if (h != -1L) activeLoops.put(sfxId, h);
            } else {
                if (handle == null) return;
                b.sound.stop(handle);
                activeLoops.remove(sfxId);
            }
        } else {
            // Music backend: looping flag + play()/stop() drive the loop.
            Music m = b.music;
            if (shouldLoop) {
                if (m.isPlaying() && m.isLooping()) {
                    m.setVolume(vol);
                    return;
                }
                m.setLooping(true);
                m.setVolume(vol);
                m.play();
            } else {
                if (!m.isPlaying()) return;
                m.stop();
            }
        }
    }

    /** Stop every active SFX loop — call on screen transitions or pause. */
    public void stopAllLoops() {
        // Sound-backed loops: stop by handle.
        for (Map.Entry<SfxId, Long> e : new HashMap<>(activeLoops).entrySet()) {
            SfxBackend b = sfxBackends.get(e.getKey());
            if (b != null && b.sound != null) b.sound.stop(e.getValue());
        }
        activeLoops.clear();
        // Music-backed loops: stop the underlying Music instance. Also halts
        // any currently-playing one-shot through this backend, which is the
        // intended behaviour for pause/dispose.
        for (SfxBackend b : sfxBackends.values()) {
            if (b.music != null && b.music.isPlaying()) b.music.stop();
        }
    }

    /**
     * Switch the current music track. Restarting the same track is a no-op.
     * Call from a screen's render loop without worrying about stutter.
     */
    public void playMusic(MusicId musicId) {
        if (musicId == currentMusic) {
            Music existing = musicTracks.get(musicId);
            if (existing != null && !existing.isPlaying()) existing.play();
            return;
        }
        stopMusic();
        Music m = getMusic(musicId);
        if (m == null) return;
        m.setLooping(true);
        m.setVolume(musicVolume);
        m.play();
        currentMusic = musicId;
    }

    public void pauseMusic() {
        if (currentMusic == null) return;
        Music m = musicTracks.get(currentMusic);
        if (m != null && m.isPlaying()) m.pause();
    }

    public void resumeMusic() {
        if (currentMusic == null) return;
        Music m = musicTracks.get(currentMusic);
        if (m != null && !m.isPlaying()) m.play();
    }

    public void stopMusic() {
        if (currentMusic == null) return;
        Music m = musicTracks.get(currentMusic);
        if (m != null) m.stop();
        currentMusic = null;
    }

    public void setSfxVolume(float v) { this.sfxVolume = clamp01(v); }

    public void setMusicVolume(float v) {
        this.musicVolume = clamp01(v);
        if (currentMusic != null) {
            Music m = musicTracks.get(currentMusic);
            if (m != null) m.setVolume(musicVolume);
        }
    }

    private float sfxVolumeFor(SfxId id) {
        Float scale = perSfxScale.get(id);
        return sfxVolume * (scale == null ? 1.0f : scale);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /**
     * Resolve {@code id} to a backend, loading lazily on first use. Caches
     * both successes and failures so repeat calls are O(1) — failed loads
     * never retry, which is what stops the per-frame "Failed to load SFX"
     * spam when a file can't be decoded by either backend.
     */
    private SfxBackend loadSfx(SfxId id) {
        SfxBackend b = sfxBackends.get(id);
        if (b != null) return b;
        if (failedSounds.contains(id)) return null;
        String path = SOUND_DIR + id.getFileName();
        // First try Sound — the cheap, fully-loaded path that supports
        // overlapping playback. Most short clips fit here.
        try {
            Sound s = Gdx.audio.newSound(Gdx.files.internal(path));
            b = new SfxBackend(s);
            sfxBackends.put(id, b);
            return b;
        } catch (Exception soundEx) {
            // Fallback: stream through Music. Common reason to land here is
            // the WAV is too long for Sound's full-decode path.
            try {
                Music m = Gdx.audio.newMusic(Gdx.files.internal(path));
                b = new SfxBackend(m);
                sfxBackends.put(id, b);
                Gdx.app.log(TAG, "SFX " + id + " using streamed Music backend (" + soundEx.getMessage() + ")");
                return b;
            } catch (Exception musicEx) {
                Gdx.app.error(TAG, "Failed to load SFX " + id + " (" + id.getFileName() + ") via either backend; "
                    + "sound=" + soundEx.getMessage() + " music=" + musicEx.getMessage());
                failedSounds.add(id);
                return null;
            }
        }
    }

    private Music getMusic(MusicId id) {
        Music m = musicTracks.get(id);
        if (m != null) return m;
        try {
            m = Gdx.audio.newMusic(Gdx.files.internal(SOUND_DIR + id.getFileName()));
            musicTracks.put(id, m);
            return m;
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to load music " + id + " (" + id.getFileName() + "): " + e.getMessage());
            return null;
        }
    }

    @Override
    public void dispose() {
        stopMusic();
        stopAllLoops();
        for (SfxBackend b : sfxBackends.values()) {
            if (b.sound != null) b.sound.dispose();
            if (b.music != null) b.music.dispose();
        }
        sfxBackends.clear();
        for (Music m : musicTracks.values()) m.dispose();
        musicTracks.clear();
    }
}
