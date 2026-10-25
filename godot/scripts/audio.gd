extends Node
## Music and sound effects.

var _music := AudioStreamPlayer.new()
var _tracks := {}
var _sfx := {}
var _players: Array[AudioStreamPlayer] = []
var _current := ""


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	add_child(_music)
	_music.volume_db = -6.0
	for t in ["menu", "race"]:
		var s: AudioStreamOggVorbis = load("res://audio/music_%s.ogg" % t)
		s.loop = true
		_tracks[t] = s
	for n in ["click", "count", "go", "coin", "capture", "ko", "power", "blocked", "win", "lose", "streak"]:
		_sfx[n] = load("res://audio/sfx_%s.wav" % n)
	for k in 8:
		var p := AudioStreamPlayer.new()
		add_child(p)
		_players.append(p)


func music(track: String) -> void:
	if track == _current:
		return
	_current = track
	_music.stream = _tracks[track]
	_music.pitch_scale = 1.0
	if Save.sound:
		_music.play()


## Speeds the race music up a little for the last seconds.
func intense(on: bool) -> void:
	_music.pitch_scale = 1.08 if on else 1.0


func set_sound(on: bool) -> void:
	Save.sound = on
	Save.save()
	if on:
		_music.play()
	else:
		_music.stop()


func play(name: String, volume_db := 0.0, pitch := 1.0) -> void:
	if not Save.sound:
		return
	for p in _players:
		if not p.playing:
			p.stream = _sfx[name]
			p.volume_db = volume_db
			p.pitch_scale = pitch
			p.play()
			return
