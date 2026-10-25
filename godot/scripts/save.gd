extends Node
## Your progress, kept on the phone: coins, riders, countries, stats.

const PATH := "user://save.cfg"
const XP_PER_LEVEL := 120

var coins := 0
var selected := 0
var unlocked: Array = [0]
## The country you're playing now, and the furthest one you've reached.
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


func _ready() -> void:
	var f := ConfigFile.new()
	if f.load(PATH) != OK:
		return
	for k in ["coins", "selected", "unlocked", "level", "best_level", "conquered", "nickname", "xp", "wins", "rounds", "kills", "best_streak", "sound"]:
		set(k, f.get_value("p", k, get(k)))


func save() -> void:
	var f := ConfigFile.new()
	for k in ["coins", "selected", "unlocked", "level", "best_level", "conquered", "nickname", "xp", "wins", "rounds", "kills", "best_streak", "sound"]:
		f.set_value("p", k, get(k))
	f.save(PATH)


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


## Records a finished round. Returns how much XP it earned.
func finish_round(won: bool, coins_won: int, ko: int, streak: int, percent: float) -> int:
	rounds += 1
	kills += ko
	best_streak = maxi(best_streak, streak)
	coins += coins_won
	var gained := 10 + int(percent) + ko * 15 + (50 if won else 0)
	xp += gained
	if won:
		wins += 1
		if not (level in conquered):
			conquered.append(level)
		if level == best_level and best_level < GameData.LEVELS.size() - 1:
			best_level += 1
	save()
	return gained
