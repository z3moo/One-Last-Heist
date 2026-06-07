"""
Generate 7 piano-like single-note WAVs for the One Last Heist piano puzzle.
Each note is a clean 16-bit PCM stereo WAV that LibGDX's Sound class can
load. Timbre = fundamental + 2nd/3rd/4th harmonics with quick attack and
exponential decay so it reads as "plucked string / muted piano" rather than
a sine drone.
"""
import wave, struct, math, os

# Equal-temperament 4th-octave frequencies (Hz). Picked the 4th octave so
# the notes sit comfortably in the middle of human hearing without being
# either bass-rumble or piccolo-bright.
NOTES = {
    'C': 261.63,
    'D': 293.66,
    'E': 329.63,
    'F': 349.23,
    'G': 392.00,
    'A': 440.00,
    'B': 493.88,
}

SR = 44100
DURATION = 1.6      # seconds total — long enough to hear decay tail
DECAY = 2.5         # exp decay rate per second
ATTACK = 0.005      # 5 ms attack so the onset is sharp without clicking
AMP = 0.55          # peak amplitude (well under 0.99 to avoid clipping)
HARMONIC_MIX = (1.0, 0.5, 0.25, 0.12)  # fundamental + 3 overtones
HARMONIC_NORM = sum(HARMONIC_MIX)       # peak when all sines align

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
nsamples = int(SR * DURATION)

for letter, freq in NOTES.items():
    frames = bytearray()
    for i in range(nsamples):
        t = i / SR
        env = (t / ATTACK) if t < ATTACK else math.exp(-DECAY * (t - ATTACK))
        s = (
            HARMONIC_MIX[0] * math.sin(2 * math.pi * freq * t)
            + HARMONIC_MIX[1] * math.sin(2 * math.pi * freq * 2 * t)
            + HARMONIC_MIX[2] * math.sin(2 * math.pi * freq * 3 * t)
            + HARMONIC_MIX[3] * math.sin(2 * math.pi * freq * 4 * t)
        ) / HARMONIC_NORM
        sample = max(-32767, min(32767, int(env * AMP * s * 32767)))
        # stereo: identical L and R channels
        frames += struct.pack('<hh', sample, sample)

    path = os.path.join(OUT_DIR, f'Note_{letter}.wav')
    with wave.open(path, 'wb') as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(bytes(frames))
    print(f'  wrote {path}')

print('done')
