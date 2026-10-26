# Records Neon Rush Shorts/Reels: a bot plays a round in Godot's Movie Maker mode,
# the best of a few tries is kept, and ffmpeg turns it into a vertical 1080x1920 MP4
# with a caption file next to it. Used by .github/workflows/shorts.yml.
#
# Run from the repo root (needs godot on PATH, ffmpeg, and a display, e.g. xvfb-run):
#   python3 tools/make_shorts.py --out shorts --count 1 --tries 3
# Each clip gives <out>/neon-rush-<date>-<n>.mp4 and .json (title, caption, hashtags, stats).
import argparse, datetime, json, os, random, subprocess, sys

LANDS = ["Neon Bay", "Pulse Island", "Volt Canyon", "Synth Delta", "Laser Lagoon",
         "Chrome Coast", "Star Reef", "Grid Peaks", "Nova Crater", "Core City"]
RIDERS = ["Sparky", "Pip", "Zippy", "Rocky", "Blaze", "Luna", "Frost", "King Kilowatt"]

# The gold line shown over the first seconds. Short, a question or a dare.
HOOKS = [
    "Can you take 30% in 25 seconds?",
    "Close the loop. Take the land.",
    "Cut their light trail = KO",
    "Only 1% of players get this",
    "Wait for the KO...",
    "25 seconds. 6 riders. 1 winner.",
    "Satisfying land grab",
    "Don't cross a trail...",
    "Rate this run 1-10",
    "Bet you can't beat this",
]

# Titles for YouTube Shorts (100 chars max) and captions for Reels.
TITLES = [
    "Neon light bike land grab in {map} 🏍️⚡ #shorts",
    "{rider} took {land}% of {map} in 25 seconds 😮 #shorts",
    "Light trail KO! Neon Rush gameplay #shorts",
    "Most satisfying neon land grab ⚡ #shorts",
    "Can you beat {land}%? Neon Rush #shorts",
]
CAPTIONS = [
    "Ride, draw a loop of light and take the land. Cut a rival's trail to knock them out. ⚡\n{land}% on {map} with {rider}. Can you beat it?\n\n{cta}",
    "25 seconds of pure neon chaos 🏍️💨\nLoop back to your land to claim everything inside. Hit a trail and you're out.\n\n{cta}",
    "{rider} vs 5 riders on {map} 🔥 {land}% land, {kos} KO.\nComment your best score 👇\n\n{cta}",
]
HASHTAGS = ["#neonrush", "#mobilegame", "#androidgames", "#indiegame", "#gaming", "#shorts",
            "#satisfying", "#games", "#gameplay", "#offlinegames", "#indiangamer"]


def render(godot_dir, out_avi, stats, seed, level, rider, seconds, hook, cta, size):
    cmd = ["godot", "--path", godot_dir, "--write-movie", out_avi, "--fixed-fps", "30",
           "--resolution", size, "--quit-after", str(int((seconds + 10) * 30)),
           "--", "--promo", f"--seed={seed}", f"--level={level}", f"--rider={rider}",
           f"--seconds={seconds}", f"--hook={hook}", f"--cta={cta}", f"--stats={stats}"]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
    with open(stats) as f:
        return json.load(f)


def score(s):
    # Big land and KOs make the best clip; getting knocked out is a weaker ending.
    return s["land"] + 6 * s["kos"] + 2 * s["loops"] - (15 if s["knocked_out"] else 0) - 2 * s["rank"]


def to_mp4(avi, mp4):
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-i", avi,
        "-vf", "scale=1080:1920:flags=lanczos,format=yuv420p",
        "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-r", "30",
        "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-af", "loudnorm=I=-14:TP=-1.5",
        "-movflags", "+faststart", mp4], check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="shorts")
    ap.add_argument("--godot", default="godot")
    ap.add_argument("--count", type=int, default=1)
    ap.add_argument("--tries", type=int, default=3)
    ap.add_argument("--seconds", type=float, default=25.0)
    ap.add_argument("--size", default="720x1280")
    ap.add_argument("--cta", default="Coming soon on Google Play")
    ap.add_argument("--date", default=datetime.date.today().isoformat())
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    day = datetime.date.fromisoformat(a.date).toordinal()
    rnd = random.Random(day)
    for n in range(1, a.count + 1):
        # Rotate through lands and riders day by day so the feed stays varied.
        level = (day * 3 + n) % len(LANDS)
        rider = (day + n * 3) % len(RIDERS)
        hook = HOOKS[(day + n) % len(HOOKS)]
        best = None
        for t in range(a.tries):
            seed = rnd.randrange(1, 1_000_000)
            base = os.path.join(a.out, f"try-{n}-{t}")
            s = render(a.godot, base + ".avi", base + ".json", seed, level, rider, a.seconds, hook, a.cta, a.size)
            print(f"clip {n} try {t}: {s} score {score(s):.1f}", flush=True)
            if best is None or score(s) > score(best[1]):
                best = (base, s)
        name = os.path.join(a.out, f"neon-rush-{a.date}-{n}")
        to_mp4(best[0] + ".avi", name + ".mp4")
        s = best[1]
        fill = {"land": round(s["land"]), "kos": s["kos"], "map": LANDS[level], "rider": RIDERS[rider], "cta": a.cta}
        title = rnd.choice(TITLES).format(**fill)
        tags = HASHTAGS[:4] + rnd.sample(HASHTAGS[4:], 5)
        caption = rnd.choice(CAPTIONS).format(**fill) + "\n\n" + " ".join(tags)
        with open(name + ".json", "w") as f:
            json.dump({"app": "Neon Rush", "title": title, "caption": caption, "hashtags": tags,
                       "hook": hook, "made_for_kids": False, "stats": s}, f, indent=2, ensure_ascii=False)
        for t in range(a.tries):
            for ext in (".avi", ".json"):
                p = os.path.join(a.out, f"try-{n}-{t}{ext}")
                if os.path.exists(p):
                    os.remove(p)
        print(f"wrote {name}.mp4: {title}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
