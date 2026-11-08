# E3-S15 (#407) previews: portal sounds and charging overlay

Candidates for Nick's yes.

Nick, 2026-11-08: the opening sound is C (`open_c_resonant`). The charge sound is B without its closing chime, made as `charge_b2_no_chime`. The overlay
looked good but needed a higher resolution, so it was redrawn at four times the texel density (below). Nothing here is in `src/` yet. All audio was synthesised with ffmpeg's `aevalsrc` and
filters (`sounds.py`); no recorded or downloaded audio was used. The overlay was drawn in code (`overlay.py`, plain
Python, no libraries). Running either script again rebuilds its files.

## Sounds

Each candidate comes as mono 44.1 kHz Ogg Vorbis (the file the mod would ship) and as an .mp3 to play easily.
Levels were measured against vanilla: the portal trigger has a mean of -21.7 dB, stone dig peaks at -3 to -4 dB,
and end portal eye placing and beacon activate peak at -9 to -13 dB. Every candidate is set to a mean of -25 dB,
quieter than the trigger, which puts the peaks at -7.5 to -9.5 dB for the open sounds and -12 to -14 dB for the
charge sounds, in the range of the vanilla block sounds above.

The travel candidates replace vanilla's nether portal travel sound on the teleport, after the charge. Vanilla's
`portal/travel.ogg` is 9.5 s long with a mean of -17.3 dB and a peak of -0.6 dB, measured from the asset index in the
Gradle cache (the same file hash appears in NeoForm's 26.1.1 index). The travel candidates follow the same -25 dB
mean, so they are about 8 dB quieter than vanilla's and much shorter; their peaks are -6 to -10 dB.

- `open_a_chime`: 1.3 s. A soft stone tap under a bell on G4 with inharmonic partials, dying away.
- `open_b_mechanism`: 1.45 s. A short stone grind, a low clunk, then a low G and D swelling and fading, like a
  mechanism unlocking.
- `open_c_resonant`: 1.2 s, picked by Nick on 2026-11-08. A glassy tone that glides up a fifth in the first eighth of a second and rings out.
- `charge_a_rising_hum`: 4.0 s. A hum that rises one octave over 3.6 s with a quickening tremolo, then resolves
  to a short octave-and-fifth ping as the charge completes.
- `charge_b_beating_drone`: 4.0 s. A low drone of two slightly detuned pairs whose beating slows to a steady unison
  while a breath of noise grows, then a quiet chime as it locks in.
- `charge_b2_no_chime`: 4.0 s, picked by Nick on 2026-11-08. The same drone as B, with its beating slowing to
  unison over the charge, but no chime: the settled unison tone holds and fades out over the last quarter second.
  Mean -25 dB, peak -15 dB, like the others.
- `charge_c_wind_up`: 4.0 s. Four clicks a second apart, each adding a rising note (G, B, D, F sharp), resolving to
  a G major chord at 3.6 s.
- `travel_a_swell_shimmer`: 2.6 s. The settled unison drone from B2 swells, a whoosh falls through four noise bands
  from high to low, and a glassy shimmer on G5 rings out with a slow tremolo.
- `travel_b_sweep_resolve`: 1.9 s. A rising sweep from C4 to G6 in a third of a second, with a breath of high noise,
  resolving on open C's G an octave up and ringing out.
- `travel_c_air_thump`: 2.3 s. A soft low thump of displaced air (a falling 90 to 45 Hz pulse and a muffled noise
  burst), then open C's resonant G5 over C5 rings out.
- `sound-spectrograms.png`: waveform and spectrogram of the first six candidates, open sounds on the left (A, B, C
  top to bottom), charge sounds on the right. It does not include B2 or the travel candidates.

The charge sounds resolve at 3.6 s (B2 settles there and fades from 3.75 s) and end at 4.0 s, matching the 80-tick charge. If the player steps out early
the sound must be stopped, which is the developer's part.

## Overlay

One design in each tier colour (T1 #0D7026, T2 #316CFF, T3 #A038D0, T4 #FF8000). The texture is near-white and
tinted by the tier colour in code, as the interim overlay already does. Instead of a swirl, a veil closes in from
the screen edges in stepped, dithered bands, behind a thin line with rune studs every 8 texels; a fixed inlay with
knots every 16 texels runs just inside the screen edge, echoing the Vault Frame's interlace. The centre of the
screen always stays clear.

- `overlay_candidate_strip_640x2880.png`: the candidate texture, 8 frames of 640x360 stacked vertically. It
  replaces the first 160x720 draft (frames of 160x90), which Nick found too low in resolution. The frame is
  picked by charge progress (frame = progress times 8), so the veil reaches further in as the charge fills; no shader
  is needed. Proposed alpha is 0.35 + 0.55 times progress. At 1080p one texel covers 3 screen pixels, and at 1440p
  4, so the veil steps, the two-texel front line and the rune studs (each a diamond with a darker core) stay crisp.
  The strip is 56 KB on disk and about 7.4 MB as RGBA in memory.
- `overlay_candidate_contact_sheet.png`: all 8 frames at half size on a checker, then frames 4 and 8 at full size.
- `overlay_preview_grid.png`: the overlay over a plain in-game backdrop (sky, hills, grass, stone, hotbar,
  crosshair). Rows are T1 to T4 from the top; the left column is mid-charge (frame 4, alpha 0.63), the right is
  near-full (frame 8, alpha 0.87).
- `overlay_preview_grid_greyscale.png`: the same grid in greyscale. The tiers differ by hue only here, so T1 and T4
  are close for a red-green colour-blind player; the portal block itself carries the tier, and the overlay is a
  second signal.
- `overlay_preview_t1_near_full.png` to `overlay_preview_t4_near_full.png`: each tier near-full at 1920x1080.
- `sounds.py`, `overlay.py`: the scripts that made these files.
