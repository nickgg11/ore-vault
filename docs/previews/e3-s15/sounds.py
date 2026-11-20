"""Synthesize E3-S15 portal sound candidates with ffmpeg filters only (aevalsrc + filters)."""
import os, re, subprocess, sys

FF = r"C:/Users/itsni/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-7.1-full_build/bin/ffmpeg.exe"
OUT = r"E:/Github/Ore-Vault_2/docs/previews/e3-s15"
TMP = os.path.dirname(os.path.abspath(__file__))
SR = 44100
MEAN = -25.0  # loudness cap: below the vanilla portal trigger's -21.7 dB mean
PI2 = "2*PI"

def src(expr, d):
    return f"aevalsrc='{expr}':s={SR}:d={d}:c=mono"

# Each candidate: (name, duration, target peak dBFS, [ (expr, filterchain) ... ], master chain)
C = {}

# ---- OPEN A: stone tap + bell chime (G4 bell, inharmonic partials) ----
bell = "+".join(
    f"{a}*exp(-t*{k})*sin({PI2}*{392*r}*t)"
    for a, r, k in [(1, 1, 3.2), (0.45, 2.0, 4.5), (0.3, 2.76, 6.5), (0.12, 4.07, 9)]
)
C["open_a_chime"] = (1.3, -4.0, [
    (f"(2*random(0)-1)*exp(-t*28)", "lowpass=f=260,lowpass=f=260,volume=2.2"),
    (f"(1-exp(-t*400))*({bell})*0.35", "highpass=f=120"),
], "afade=t=out:st=1.1:d=0.2")

# ---- OPEN B: stone grind, clunk, low resonant fifth ----
C["open_b_mechanism"] = (1.45, -4.0, [
    ("if(lt(t,0.36),sqrt(t/0.36)*(0.55+0.45*sin(2*PI*21*t)),0)*(2*random(0)-1)",
     "bandpass=f=520:width_type=q:w=1.2,lowpass=f=1800,volume=1.6"),
    ("if(gte(t,0.36),sin(2*PI*82*(t-0.36))*exp(-(t-0.36)*20)+0.6*(2*random(0)-1)*exp(-(t-0.36)*45),0)",
     "lowpass=f=400"),
    ("if(gte(t,0.38),(1-exp(-(t-0.38)*30))*exp(-(t-0.38)*3)*(sin(2*PI*196*t)+0.55*sin(2*PI*294*t)+0.18*sin(2*PI*392*t))*0.35,0)",
     "anull"),
], "afade=t=out:st=1.25:d=0.2")

# ---- OPEN C: short glide up into a glassy resonant tone ----
glide = "(523*t+261*if(lt(t,0.12),t*t/0.24,0.06+(t-0.12)))"
C["open_c_resonant"] = (1.2, -4.0, [
    (f"(1-exp(-t*250))*exp(-t*3.6)*(sin({PI2}*{glide})+0.5*sin({PI2}*({glide}*1.003))+0.25*sin({PI2}*2*{glide})+0.08*sin({PI2}*3*{glide}))*0.4",
     "lowpass=f=4000"),
    ("(2*random(0)-1)*exp(-t*35)*0.5", "bandpass=f=2400:width_type=q:w=0.8"),
], "afade=t=out:st=1.0:d=0.2")

# ---- CHARGE A: rising hum, octave glide over 3.6 s, resolves to octave + fifth ping ----
T = 3.6
ph = f"if(lt(t,{T}),{110*T}/log(2)*(pow(2,t/{T})-1),{110*T}/log(2)+220*(t-{T}))"
trem = f"(1-0.28*(0.5+0.5*sin({PI2}*(4*t+10*t*t/{2*T}))))"
amp = f"if(lt(t,{T}),0.25+0.75*t/{T},exp(-(t-{T})*7))"
C["charge_a_rising_hum"] = (4.0, -6.0, [
    (f"{amp}*{trem}*(sin({PI2}*{ph})+0.5*sin(2*{PI2}*{ph})+0.25*sin(3*{PI2}*{ph})+0.12*sin(4*{PI2}*{ph}))*0.35",
     "lowpass=f=1800"),
    (f"if(gte(t,{T}),(1-exp(-(t-{T})*300))*exp(-(t-{T})*5)*(sin({PI2}*440*t)+0.6*sin({PI2}*660*t)+0.2*sin({PI2}*1214*t))*0.3,0)",
     "anull"),
], "afade=t=in:d=0.08,afade=t=out:st=3.85:d=0.15")

# ---- CHARGE B: detuned drone whose beating slows to unison, wind rising, soft lock chime ----
det = f"if(lt(t,{T}),98*t+3*(t-t*t/{2*T}),98*t+{3*T/2})"
det5 = f"if(lt(t,{T}),147*t+2*(t-t*t/{2*T}),147*t+{2*T/2})"
C["charge_b_beating_drone"] = (4.0, -6.0, [
    (f"(0.35+0.65*min(t/{T},1))*(sin({PI2}*98*t)+sin({PI2}*{det})+0.6*sin({PI2}*147*t)+0.6*sin({PI2}*{det5})+0.2*sin({PI2}*294*t))*0.22*if(lt(t,{T}),1,exp(-(t-{T})*5))",
     "lowpass=f=1200"),
    (f"min(t/{T},1)*min(t/{T},1)*(2*random(0)-1)*0.35*if(lt(t,{T}),1,exp(-(t-{T})*10))",
     "bandpass=f=900:width_type=q:w=0.7,lowpass=f=2000"),
    (f"if(gte(t,{T}),(1-exp(-(t-{T})*300))*exp(-(t-{T})*5.5)*(sin({PI2}*588*t)+0.5*sin({PI2}*882*t))*0.3,0)",
     "anull"),
], "afade=t=in:d=0.1,afade=t=out:st=3.85:d=0.15")

# ---- CHARGE B2 (Nick's pick, 2026-11-08): charge B without the chime; the drone holds the settled unison and fades ----
C["charge_b2_no_chime"] = (4.0, -6.0, [
    (f"(0.35+0.65*min(t/{T},1))*(sin({PI2}*98*t)+sin({PI2}*{det})+0.6*sin({PI2}*147*t)+0.6*sin({PI2}*{det5})+0.2*sin({PI2}*294*t))*0.22",
     "lowpass=f=1200"),
    (f"min(t/{T},1)*min(t/{T},1)*(2*random(0)-1)*0.35*if(lt(t,{T}),1,exp(-(t-{T})*10))",
     "bandpass=f=900:width_type=q:w=0.7,lowpass=f=2000"),
], "afade=t=in:d=0.1,afade=t=out:st=3.75:d=0.25")

# ---- CHARGE C: mechanism winding up in four rising steps, resolves to G major ----
steps = [(0.0, 196), (0.9, 247), (1.8, 294), (2.7, 370)]
tones = "+".join(
    f"if(gte(t,{tk}),(1-exp(-(t-{tk})*60))*exp(-(t-{tk})*1.6)*(sin({PI2}*{f}*t)+0.3*sin({PI2}*{2*f}*t))*{0.55+0.15*i},0)"
    for i, (tk, f) in enumerate(steps)
)
clicks = "+".join(f"if(gte(t,{tk}),exp(-(t-{tk})*70),0)" for tk, _ in steps)
C["charge_c_wind_up"] = (4.0, -6.0, [
    (f"({tones})*0.18*if(lt(t,{T}),1,exp(-(t-{T})*8))", "lowpass=f=2500"),
    (f"({clicks})*(2*random(0)-1)*0.8", "bandpass=f=700:width_type=q:w=1.5"),
    (f"(0.2+0.8*min(t/{T},1))*sin({PI2}*98*t)*0.2*if(lt(t,{T}),1,exp(-(t-{T})*6))", "anull"),
    (f"if(gte(t,{T}),(1-exp(-(t-{T})*300))*exp(-(t-{T})*5)*(sin({PI2}*392*t)+0.7*sin({PI2}*494*t)+0.6*sin({PI2}*588*t))*0.22,0)",
     "anull"),
], "afade=t=out:st=3.85:d=0.15")


# ---- TRAVEL (played on the teleport, after the charge). Same family as open C and charge B2. ----
def win(t0, t1, k):
    """Envelope that rises fast at t0 and decays at rate k, zero before t0."""
    return f"if(gte(t,{t0}),(1-exp(-(t-{t0})*40))*exp(-(t-{t0})*{k}),0)"

# A: the settled unison drone swells, a whoosh falls through four noise bands, a glassy shimmer rings out
whoosh = [(f"(2*random(0)-1)*{win(0.6 + 0.12 * i, 0, 9)}*0.9", f"bandpass=f={fc}:width_type=q:w=1.4")
          for i, fc in enumerate([3200, 1900, 1100, 600])]
C["travel_a_swell_shimmer"] = (2.6, -6.0, [
    (f"min(t/0.7,1)*if(lt(t,0.8),1,exp(-(t-0.8)*5))*(sin({PI2}*98*t)+sin({PI2}*98*t)+0.6*sin({PI2}*147*t)+0.6*sin({PI2}*147*t)+0.25*sin({PI2}*294*t))*0.25",
     "lowpass=f=1200"),
    *whoosh,
    (f"{win(1.0, 0, 2.2)}*(1-0.35*(0.5+0.5*sin({PI2}*9*t)))*(sin({PI2}*784*t)+0.6*sin({PI2}*784*1.003*t)+0.4*sin({PI2}*1568*t)+0.15*sin({PI2}*2352*t))*0.22",
     "lowpass=f=6000"),
], "afade=t=in:d=0.05,afade=t=out:st=2.35:d=0.25")

# B: a short rising sweep (262 to 1568 Hz over 0.35 s) that resolves on the open C tone an octave up
Tg, f0, f1 = 0.35, 262, 1568
swp = f"if(lt(t,{Tg}),{f0 * Tg}/log({f1 / f0})*(pow({f1 / f0},t/{Tg})-1),{f0 * Tg}/log({f1 / f0})*({f1 / f0}-1)+{f1}*(t-{Tg}))"
C["travel_b_sweep_resolve"] = (1.9, -6.0, [
    (f"(1-exp(-t*30))*if(lt(t,{Tg}),0.5+0.5*t/{Tg},exp(-(t-{Tg})*2.6))*(sin({PI2}*{swp})+0.5*sin({PI2}*1.003*{swp})+0.2*sin(2*{PI2}*{swp}))*0.3",
     "lowpass=f=5000"),
    (f"if(lt(t,{Tg}+0.1),(t/({Tg}+0.1))*(t/({Tg}+0.1)),exp(-(t-{Tg}-0.1)*14))*(2*random(0)-1)*0.4",
     "highpass=f=1500,lowpass=f=6000"),
    (f"{win(Tg, 0, 3.2)}*sin({PI2}*1046*t)*0.12", "anull"),
], "afade=t=out:st=1.65:d=0.25")

# C: a soft low thump of displaced air, then the resonant tone (G5 over C5) ringing out
C["travel_c_air_thump"] = (2.3, -6.0, [
    ("sin(2*PI*(45*t+45*(1-exp(-t*12))/12))*exp(-t*11)", "lowpass=f=300"),
    ("(2*random(0)-1)*(1-exp(-t*200))*exp(-t*16)*0.8", "lowpass=f=220,lowpass=f=220,volume=2"),
    (f"{win(0.06, 0, 1.9)}*(sin({PI2}*784*t)+0.5*sin({PI2}*784*1.003*t)+0.45*sin({PI2}*523*t)+0.1*sin({PI2}*1568*t))*0.2",
     "lowpass=f=4000"),
], "afade=t=out:st=2.05:d=0.25")


def run(args):
    r = subprocess.run([FF, "-nostdin", "-hide_banner", "-y"] + args, capture_output=True, text=True)
    if r.returncode:
        print(r.stderr[-3000:]); sys.exit(1)
    return r.stderr

def stats(path):
    e = run(["-i", path, "-af", "volumedetect", "-f", "null", "-"])
    mx = float(re.search(r"max_volume: (-?[\d.]+)", e).group(1))
    mn = float(re.search(r"mean_volume: (-?[\d.]+)", e).group(1))
    return mx, mn

ONLY = sys.argv[1:]
for name, (d, peak, layers, master) in C.items():
    if ONLY and name not in ONLY:
        continue
    parts, labels = [], []
    for i, (expr, chain) in enumerate(layers):
        parts.append(f"{src(expr, d)},{chain}[l{i}]")
        labels.append(f"[l{i}]")
    mix = f"{''.join(labels)}amix=inputs={len(labels)}:normalize=0,{master}[out]" if len(labels) > 1 else f"{labels[0]}{master}[out]"
    graph = ";\n".join(parts + [mix])
    gfile = os.path.join(TMP, name + ".graph")
    open(gfile, "w").write(graph)
    raw = os.path.join(TMP, name + ".wav")
    run(["-/filter_complex", gfile, "-map", "[out]", "-ar", str(SR), "-ac", "1", "-c:a", "pcm_f32le", raw])
    mx, mn = stats(raw)
    gain = min(peak - mx, MEAN - mn)
    ogg = os.path.join(OUT, name + ".ogg")
    mp3 = os.path.join(OUT, name + ".mp3")
    run(["-i", raw, "-af", f"volume={gain:.2f}dB", "-ar", str(SR), "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", ogg])
    run(["-i", raw, "-af", f"volume={gain:.2f}dB", "-ar", str(SR), "-ac", "1", "-c:a", "libmp3lame", "-q:a", "2", mp3])
    mx2, mn2 = stats(ogg)
    print(f"{name}: {d}s peak {mx2} dBFS mean {mn2} dBFS")
