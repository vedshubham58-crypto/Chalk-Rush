@tool
extends EditorPlugin
## Adds the AdMob plugin (built from android-ads-plugin/ by CI) to Android exports.

## Google's test app id. Swap in the real AdMob app id before launch.
const ADMOB_APP_ID := "ca-app-pub-3940256099942544~3347511713"

var _export: EditorExportPlugin


func _enter_tree() -> void:
	_export = NeonAdsExport.new()
	add_export_plugin(_export)


func _exit_tree() -> void:
	remove_export_plugin(_export)
	_export = null


class NeonAdsExport extends EditorExportPlugin:
	func _get_name() -> String:
		return "NeonAds"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_libraries(platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		return PackedStringArray(["neon_ads/bin/neon-ads-release.aar"])

	func _get_android_dependencies(platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		return PackedStringArray(["com.google.android.gms:play-services-ads:23.6.0"])

	func _get_android_manifest_application_element_contents(platform: EditorExportPlatform, debug: bool) -> String:
		return '<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="%s"/>' % ADMOB_APP_ID
