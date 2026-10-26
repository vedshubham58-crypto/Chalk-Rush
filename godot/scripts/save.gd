extends Node
## Your progress, kept on the phone: coins, riders, lands, stats, upgrades,
## the daily reward ladder and today's missions.

const PATH := "user://save.cfg"
const XP_PER_LEVEL := 120
const KEYS := ["coins", "selected", "unlocked", "level", "best_level", "conquered", "nickname", "xp", "wins", "rounds",
	"kills", "best_streak", "sound", "seen_story", "engine", "armor", "win_streak", "best_win_streak",
	"daily_day", "daily_streak", "missions_day", "missions", "language"]

## Coins for each day of the daily reward ladder; it starts over after day 7.
const DAILY_REWARDS := [25, 40, 60, 80, 100, 150, 250]
const MAX_UPGRADE := 5
const UPGRADE_COSTS := [150, 300, 500, 800, 1200]
## Engine: 3% faster per level. Armor: 0.6 s more spawn shield per level.
const ENGINE_STEP := 0.03
const ARMOR_STEP := 0.6
## Coins for finishing all three of today's missions.
const MISSIONS_BONUS := 100

## The missions a day can draw from. stat is what a round adds to it (see
## [method finish_round]); "best" stats count the best single round instead.
const MISSION_POOL := [
	{"id": "rounds", "text": "Play %d rounds", "target": 3, "reward": 40},
	{"id": "kos", "text": "Knock out %d riders", "target": 3, "reward": 60},
	{"id": "land", "text": "Claim %d%% land in one round", "target": 15, "reward": 50, "best": true},
	{"id": "wins", "text": "Win %d round", "target": 1, "reward": 80},
	{"id": "powers", "text": "Grab %d power-ups", "target": 5, "reward": 40},
	{"id": "coins", "text": "Collect %d coins on the land", "target": 25, "reward": 40},
	{"id": "loops", "text": "Close %d loops", "target": 10, "reward": 50},
	{"id": "top3", "text": "Finish top 3 in %d rounds", "target": 2, "reward": 50},
]

var coins := 0
var selected := 0
var unlocked: Array = [0]
## The land you're playing now, and the furthest one you've reached.
var level := 0
var best_level := 0
var conquered: Array = []
var nickname := ""
var xp := 0
var wins := 0
var rounds := 0
var kills := 0
var best_streak := 0
var sound := true
## Whether the story intro has been shown once.
var seen_story := false
## Bike upgrade levels, 0..MAX_UPGRADE.
var engine := 0
var armor := 0
## Wins in a row, and the most ever.
var win_streak := 0
var best_win_streak := 0
## The day number of the last daily reward claim, and how many days in a row.
var daily_day := -1
var daily_streak := 0
## Today's missions: [{id, progress, claimed}], drawn fresh each day.
var missions_day := -1
var missions: Array = []
## The menu language code ("" = the phone's language).
var language := ""


func _ready() -> void:
	var f := ConfigFile.new()
	if f.load(PATH) == OK:
		for k in KEYS:
			set(k, f.get_value("p", k, get(k)))
	_refresh_missions()


func save() -> void:
	var f := ConfigFile.new()
	for k in KEYS:
		f.set_value("p", k, get(k))
	f.save(PATH)


## Days since 1970 in the phone's own time zone, so "a new day" starts at local midnight.
static func today() -> int:
	var bias: int = Time.get_time_zone_from_system().get("bias", 0)
	return int((Time.get_unix_time_from_system() + bias * 60) / 86400)


func player_level() -> int:
	return 1 + xp / XP_PER_LEVEL


func level_progress() -> float:
	return float(xp % XP_PER_LEVEL) / XP_PER_LEVEL


func buy(rider_id: int) -> bool:
	var cost: int = GameData.rider(rider_id).cost
	if rider_id in unlocked or coins < cost:
		return false
	coins -= cost
	unlocked.append(rider_id)
	selected = rider_id
	save()
	return true


# ---------------------------------------------------------------- upgrades

func upgrade_cost(current: int) -> int:
	return UPGRADE_COSTS[current] if current < MAX_UPGRADE else -1


## Buys the next engine (or armor) level. Returns true if it worked.
func upgrade(is_engine: bool) -> bool:
	var cur := engine if is_engine else armor
	var cost := upgrade_cost(cur)
	if cost < 0 or coins < cost:
		return false
	coins -= cost
	if is_engine:
		engine += 1
	else:
		armor += 1
	save()
	return true


# ---------------------------------------------------------------- daily reward

## The streak still counts if the last claim was today or yesterday.
func current_daily_streak() -> int:
	return daily_streak if daily_day >= today() - 1 else 0


func daily_ready() -> bool:
	return daily_day < today()


## Which day of the 7-day ladder today's claim is (1..7).
func daily_slot() -> int:
	return current_daily_streak() % DAILY_REWARDS.size() + 1


## Claims today's reward, times [param multiplier] for watching a video. Returns the coins.
func claim_daily(multiplier := 1) -> int:
	if not daily_ready():
		return 0
	var got: int = DAILY_REWARDS[daily_slot() - 1] * multiplier
	daily_streak = current_daily_streak() + 1
	daily_day = today()
	coins += got
	save()
	return got


# ---------------------------------------------------------------- missions

## Draws three new missions when the day has changed.
func _refresh_missions() -> void:
	if missions_day == today() and missions.size() == 3:
		return
	var rng := RandomNumberGenerator.new()
	rng.seed = today() * 7919 + 13
	var pool := range(MISSION_POOL.size())
	missions = []
	while missions.size() < 3:
		var k: int = pool.pop_at(rng.randi() % pool.size())
		missions.append({"id": MISSION_POOL[k].id, "progress": 0, "claimed": false})
	missions_day = today()
	save()


static func mission_info(id: String) -> Dictionary:
	for m in MISSION_POOL:
		if m.id == id:
			return m
	return MISSION_POOL[0]


func mission_done(m: Dictionary) -> bool:
	return m.progress >= mission_info(m.id).target


## Missions finished but not claimed yet, for the badge on the lobby button.
func missions_to_claim() -> int:
	_refresh_missions()
	return missions.filter(func(m): return mission_done(m) and not m.claimed).size()


func claim_mission(k: int) -> int:
	var m: Dictionary = missions[k]
	if m.claimed or not mission_done(m):
		return 0
	m.claimed = true
	var got: int = mission_info(m.id).reward
	if missions.all(func(x): return x.claimed):
		got += MISSIONS_BONUS
	coins += got
	save()
	return got


## Adds a round's numbers to today's missions. Returns the missions this round finished.
func _track(stats: Dictionary) -> Array:
	_refresh_missions()
	var finished := []
	for m in missions:
		if m.claimed or mission_done(m):
			continue
		var info := mission_info(m.id)
		var v: int = stats.get(m.id, 0)
		m.progress = maxi(m.progress, v) if info.get("best", false) else m.progress + v
		m.progress = mini(m.progress, info.target)
		if mission_done(m):
			finished.append(m)
	return finished


# ---------------------------------------------------------------- rounds

## Records a finished round. [param stats] has the round's mission numbers
## (kos, land, powers, coins, loops, rank). Returns {xp, streak_bonus, missions}.
func finish_round(won: bool, coins_won: int, ko: int, streak: int, percent: float, stats := {}) -> Dictionary:
	rounds += 1
	kills += ko
	best_streak = maxi(best_streak, streak)
	win_streak = win_streak + 1 if won else 0
	best_win_streak = maxi(best_win_streak, win_streak)
	# A bonus that grows with your win streak, up to 10 wins.
	var streak_bonus := 10 * mini(win_streak, 10) if won and win_streak > 1 else 0
	coins += coins_won + streak_bonus
	var gained := 10 + int(percent) + ko * 15 + (50 if won else 0)
	xp += gained
	if won:
		wins += 1
		if not (level in conquered):
			conquered.append(level)
		if level == best_level and best_level < GameData.LEVELS.size() - 1:
			best_level += 1
	var s := stats.duplicate()
	s["rounds"] = 1
	s["wins"] = 1 if won else 0
	s["top3"] = 1 if stats.get("rank", 99) <= 3 else 0
	var done := _track(s)
	save()
	return {"xp": gained, "streak_bonus": streak_bonus, "missions": done}
