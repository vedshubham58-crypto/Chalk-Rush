package com.vedshubham.neonads;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.util.Set;

/**
 * Shows AdMob full-screen ads for the game. GDScript calls start(), then
 * showInterstitial() / showRewarded(), and hears back through signals:
 * interstitial_closed, and rewarded_closed(earned).
 */
public class NeonAds extends GodotPlugin {
    private volatile InterstitialAd interstitial;
    private volatile RewardedAd rewarded;
    private String interstitialId = "";
    private String rewardedId = "";
    private boolean loadingInterstitial;
    private boolean loadingRewarded;
    private boolean earned;

    public NeonAds(Godot godot) {
        super(godot);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "NeonAds";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();
        signals.add(new SignalInfo("interstitial_closed"));
        signals.add(new SignalInfo("rewarded_closed", Boolean.class));
        return signals;
    }

    @UsedByGodot
    public void start(String interstitialUnit, String rewardedUnit) {
        interstitialId = interstitialUnit;
        rewardedId = rewardedUnit;
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            MobileAds.initialize(activity, status -> { });
            loadInterstitial();
            loadRewarded();
        });
    }

    @UsedByGodot
    public boolean isInterstitialReady() {
        return interstitial != null;
    }

    @UsedByGodot
    public boolean isRewardedReady() {
        return rewarded != null;
    }

    /** Returns false (and asks for a new ad) if none is loaded yet. */
    @UsedByGodot
    public boolean showInterstitial() {
        Activity activity = getActivity();
        InterstitialAd ad = interstitial;
        if (activity == null || ad == null) {
            if (activity != null) activity.runOnUiThread(this::loadInterstitial);
            return false;
        }
        interstitial = null;
        activity.runOnUiThread(() -> {
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    send("interstitial_closed");
                    loadInterstitial();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    send("interstitial_closed");
                    loadInterstitial();
                }
            });
            ad.show(activity);
        });
        return true;
    }

    /** Returns false (and asks for a new ad) if none is loaded yet. */
    @UsedByGodot
    public boolean showRewarded() {
        Activity activity = getActivity();
        RewardedAd ad = rewarded;
        if (activity == null || ad == null) {
            if (activity != null) activity.runOnUiThread(this::loadRewarded);
            return false;
        }
        rewarded = null;
        earned = false;
        activity.runOnUiThread(() -> {
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    send("rewarded_closed", earned);
                    loadRewarded();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    send("rewarded_closed", false);
                    loadRewarded();
                }
            });
            ad.show(activity, reward -> earned = true);
        });
        return true;
    }

    private void send(String signal, Object... args) {
        runOnRenderThread(() -> emitSignal(signal, args));
    }

    private void loadInterstitial() {
        Activity activity = getActivity();
        if (activity == null || interstitialId.isEmpty() || interstitial != null || loadingInterstitial) return;
        loadingInterstitial = true;
        InterstitialAd.load(activity, interstitialId, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                interstitial = ad;
                loadingInterstitial = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                loadingInterstitial = false;
            }
        });
    }

    private void loadRewarded() {
        Activity activity = getActivity();
        if (activity == null || rewardedId.isEmpty() || rewarded != null || loadingRewarded) return;
        loadingRewarded = true;
        RewardedAd.load(activity, rewardedId, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewarded = ad;
                loadingRewarded = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                loadingRewarded = false;
            }
        });
    }
}
