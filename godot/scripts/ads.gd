extends Node
## Ads. On a phone they come from the NeonAds Android plugin (AdMob). Anywhere
## else there are no ads: interstitials are skipped and rewards are just given.

## Google's test ad units. Swap in real ones before launch.
const INTERSTITIAL_ID := "ca-app-pub-3940256099942544/1033173712"
const REWARDED_ID := "ca-app-pub-3940256099942544/5224354917"

var _ads: Object
var _after_interstitial := Callable()
var _on_reward := Callable()
var _after_rewarded := Callable()


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	if Engine.has_singleton("NeonAds"):
		_ads = Engine.get_singleton("NeonAds")
		_ads.interstitial_closed.connect(_interstitial_closed)
		_ads.rewarded_closed.connect(_rewarded_closed)
		_ads.start(INTERSTITIAL_ID, REWARDED_ID)


## Whether a reward video can be shown right now.
func rewarded_ready() -> bool:
	return _ads == null or _ads.isRewardedReady()


## Shows a full-screen ad if one is loaded, then calls [param done] either way.
func show_interstitial(done: Callable) -> void:
	if _ads and _ads.showInterstitial():
		_after_interstitial = done
		Audio.duck(true)
	else:
		done.call()


## Shows a reward video. [param reward] runs only if it was watched to the end;
## [param done] runs afterwards either way.
func show_rewarded(reward: Callable, done := Callable()) -> void:
	if _ads == null:
		reward.call()
		if done.is_valid():
			done.call()
		return
	if _ads.showRewarded():
		_on_reward = reward
		_after_rewarded = done
		Audio.duck(true)
	elif done.is_valid():
		done.call()


func _interstitial_closed() -> void:
	Audio.duck(false)
	var f := _after_interstitial
	_after_interstitial = Callable()
	if f.is_valid():
		f.call()


func _rewarded_closed(earned: bool) -> void:
	Audio.duck(false)
	if earned and _on_reward.is_valid():
		_on_reward.call()
	var f := _after_rewarded
	_on_reward = Callable()
	_after_rewarded = Callable()
	if f.is_valid():
		f.call()
