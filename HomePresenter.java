package com.nesn.nesnplayer.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import com.bamtech.sdk.authentication.AuthenticationManager;
import com.bamtech.sdk.authorization.AuthorizationManager;
import com.nesn.nesnplayer.R;
import com.nesn.nesnplayer.core.DateFormatHelper;
import com.nesn.nesnplayer.core.ExceptionManager;
import com.nesn.nesnplayer.core.SharedPreferenceHelper;
import com.nesn.nesnplayer.playback.PlaybackData;
import com.nesn.nesnplayer.sdkclient.catalog.CatalogProvider;
import com.nesn.nesnplayer.sdkclient.catalog.MvpdProvider;
import com.nesn.nesnplayer.sdkclient.model.catalog.Airing;
import com.nesn.nesnplayer.sdkclient.model.catalog.MediaData;
import com.nesn.nesnplayer.sdkclient.model.catalog.MediaResult;
import com.nesn.nesnplayer.sdkclient.model.catalog.ScheduleChangeListener;
import com.nesn.nesnplayer.sdkclient.model.catalog.ScheduleHelper;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by DavidSE on 2/23/17.
 */

/**
 * HomePresenter
 */
public class HomePresenter {

    private final CatalogProvider catalogProvider;
    // CHECKSTYLE:OFF
    public HomeViewModel homeViewModel = new HomeViewModel();
    private HomeView homeView;
    private MediaData localNesnCache;
    private MediaData localPlusCache;
    private AuthenticationManager authenticationManager;
    private AuthorizationManager authorizationManager;
    private SharedPreferenceHelper sharedPreferenceHelper;
    private MvpdProvider mvpdProvider;
    private ExceptionManager exceptionManager;
    private boolean isAuthenticated;
    private String authProvider;
    private ScheduleChangeListener scheduleChangeListener;

    private String tileImageWidth;
    private String tileImageHeight;

    private HashMap<String, String> mvpdNavigationMap;

    private static final String NESN_SCREEN = "ScheduleView.NESN";
    private static final String NESNPLUS_SCREEN = "ScheduleView.NESNPlus";



    public HomePresenter(CatalogProvider catalogProvider, MvpdProvider mvpdProvider, SharedPreferenceHelper sharedPreferenceHelper,
                         AuthenticationManager authenticationManager, AuthorizationManager authorizationManager,
                         ExceptionManager exceptionManager, ScheduleChangeListener scheduleChangeListener,
                         HashMap<String, String> mvpdNavigationMap) {

        this.catalogProvider = catalogProvider;
        this.mvpdProvider = mvpdProvider;
        this.sharedPreferenceHelper = sharedPreferenceHelper;
        this.authenticationManager = authenticationManager;
        this.authorizationManager = authorizationManager;
        this.exceptionManager = exceptionManager;
        this.scheduleChangeListener = scheduleChangeListener;
        this.mvpdNavigationMap = mvpdNavigationMap;

    }

    public void init() {
        DateTime currentDate = new DateTime();
        homeViewModel.headerTitle.set(currentDate.toString(DateFormatHelper.FormatType.CURRENT_DATE_FULL));

        getMediaData(catalogProvider, currentDate.minusDays(1));

        setupMidnightRefreshTimer();
    }

    public void refreshScheduleData(){
        DateTime currentDate = new DateTime();
        getMediaData(catalogProvider, currentDate.minusDays(1));
    }

    private void setupMidnightRefreshTimer() {
        DateTime midnight = new DateTime().plusDays(1).withTimeAtStartOfDay();
        long duration = midnight.getMillis() - System.currentTimeMillis();
        homeView.addSubscription(
                Observable.timer(duration, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e(e);
                    }

                    @Override
                    public void onNext(Long aLong) {
                        Timber.d("onNext for setupRefreshTimer");
                        getMediaData(catalogProvider, new DateTime().minusDays(1));
                        setupMidnightRefreshTimer();
                    }
                }));
    }

    private void getMediaData(CatalogProvider catalogProvider, DateTime currentDate) {
        homeView.addSubscription(catalogProvider
                .getCatalog(currentDate, currentDate.plusDays(CatalogProvider.DEFAULT_SCHEDULE_DAYS_RANGE))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<MediaResult>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("onError");

                        dismissLoader();
                        homeViewModel.scheduleNotAvailable.set(true);
                        sharedPreferenceHelper.saveBooleanPreference(SharedPreferenceHelper.ERROR_ON_LAST_REFRESH, true);
                    }

                    @Override
                    public void onNext(MediaResult mediaResult) {
                        if (mediaResult != null) {
                            localNesnCache = mediaResult.getData();
                            homeViewModel.scheduleNotAvailable.set(false);

                            if (homeViewModel.selectedChannel.get() != null && homeViewModel.selectedChannel.get().equals(CatalogProvider.NESN_PLUS)) {
                                mapSchedule(homeViewModel, localNesnCache, CatalogProvider.NESN);
                                showNESNplusSchedule();
                            } else {
                                mapSchedule(homeViewModel, localPlusCache, CatalogProvider.NESN_PLUS);
                                showNESNSchedule();
                            }

                            sharedPreferenceHelper.saveBooleanPreference(SharedPreferenceHelper.ERROR_ON_LAST_REFRESH, false);

                        }
                        unsubscribe();
                        dismissLoader();
                    }
                }));


        homeView.addSubscription(catalogProvider
                .getNesnPlusCatalog(currentDate, currentDate.plusDays(CatalogProvider.PLUS_SCHEDULE_DAYS_RANGE))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<MediaResult>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e(e, " Error retrieving NESN plus data");
                    }

                    @Override
                    public void onNext(MediaResult mediaResult) {
                        localPlusCache = mediaResult.getData();
                        mapSchedule(homeViewModel, localPlusCache, CatalogProvider.NESN_PLUS);
                        unsubscribe();
                    }
                }));

        showLoader();
    }

    private void dismissLoader() {
        final View view = homeView.getHomeViewBinding().loadingScreen;
        view.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setAlpha(1);
                view.setVisibility(View.GONE);
            }
        });
    }

    private void showLoader() {
        final View view = homeView.getHomeViewBinding().loadingScreen;
        view.setVisibility(View.VISIBLE);
    }

    public void attachView(HomeView homeView) {
        this.homeView = homeView;
        tileImageWidth = homeView.getHomeViewBinding().appBarLayout.getContext().getResources().getString(R.string.tile_image_width);
        tileImageHeight = homeView.getHomeViewBinding().appBarLayout.getContext().getResources().getString(R.string.tile_image_height);

        // Add rules for refresh
        if (localNesnCache == null || !lastViewedToday() || sharedPreferenceHelper.getBooleanPreference(SharedPreferenceHelper.ERROR_ON_LAST_REFRESH)) {
            Timber.d("Initial load from catalog");
            init();
        } else {
            refreshScheduleData();
            Timber.d("Init on screen refresh");
            if (homeViewModel.selectedChannel.get() != null && homeViewModel.selectedChannel.get().equals(CatalogProvider.NESN_PLUS)) {
                mapSchedule(homeViewModel, localNesnCache, CatalogProvider.NESN);
                showNESNplusSchedule();
            } else {
                mapSchedule(homeViewModel, localPlusCache, CatalogProvider.NESN_PLUS);
                showNESNSchedule();
            }

            setupMidnightRefreshTimer();
        }

        setAuthInfo();

    }

    public void showNESNSchedule() {

        homeViewModel.selectedChannel.set(CatalogProvider.NESN);
        homeViewModel.headerTitle.set(new DateTime().toString(DateFormatHelper.FormatType.CURRENT_DATE_FULL));
        mapSchedule(homeViewModel, localNesnCache, CatalogProvider.NESN);

        homeView.analytics().trackScreen(NESN_SCREEN);
    }

    public void showNESNplusSchedule() {

        homeViewModel.selectedChannel.set(CatalogProvider.NESN_PLUS);
        homeViewModel.headerTitle.set("NESNplus Schedule");

        mapSchedule(
                homeViewModel,
                (localPlusCache != null)
                        ? localPlusCache
                        : localNesnCache,
                CatalogProvider.NESN_PLUS);

        homeView.analytics().trackScreen(NESNPLUS_SCREEN);
    }

    public void startPlayback() {

        if (homeViewModel.currentProgramPlaybackUrl.get() != null && !homeViewModel.currentProgramPlaybackUrl.get().isEmpty()) {

            // reset in case of quick logoff/login
            this.isAuthenticated = sharedPreferenceHelper.getBooleanPreference(SharedPreferenceHelper.IS_AUTH);

            PlaybackData playbackData = PlaybackData.create(
                    homeViewModel.currentProgramContentId.get(),
                    homeViewModel.currentProgramTitle.get(),
                    homeViewModel.currentProgramPlaybackUrl.get(),
                    homeViewModel.selectedChannel.get()
            );

            homeView.navigatePlayback(isAuthenticated, playbackData);

        }
    }

    public void mapSchedule(HomeViewModel homeViewModel, MediaData catalogData, String channel) {

        boolean selectedChannelState = false;

        scheduleChangeListener.setMediaData(getSelectedLocalCache());

        // Update Current Schedule
        List<Airing> scheduledAirings = null;
        if (catalogData != null
                && (scheduledAirings = catalogData.getAiringsByChannel(channel)) != null
                && scheduledAirings.size() > 0) {

            Airing airing;
            if ((airing = ScheduleHelper.getAiringByTime(scheduledAirings, DateTime.now().withZone(DateTimeZone.UTC))) != null) {
                updateOnNow(airing, scheduledAirings);
                updateProgram(homeViewModel, airing, true, channel);
                setupProgramTimer(homeViewModel, airing.getEndDate(), channel, catalogData);
                updatePreview(homeViewModel, airing, channel, "ON NOW - ");
                selectedChannelState = true;

                if (channel.equals(CatalogProvider.NESN_PLUS)) {
                    homeViewModel.nesnPlusOnNow.set(true);
                }

            } else if ((airing = ScheduleHelper.getNextAiring(scheduledAirings, DateTime.now().withZone(DateTimeZone.UTC))) != null) {
                // find Up Next Airing
                updateUpNext(airing, scheduledAirings);
                updateProgram(homeViewModel, airing, false, channel);
                setupProgramTimer(homeViewModel, airing.getStartDate(), channel, catalogData);
                updatePreview(homeViewModel, airing, channel, airing.getStartDate().toString(DateFormatHelper.FormatType.PREVIEW_TEXT));
                selectedChannelState = false;

                if (channel.equals(CatalogProvider.NESN_PLUS)) {
                    homeViewModel.nesnPlusOnNow.set(false);
                }
            }
        } else {

            selectedChannelState = false;

            // Update only for selected channel
            if (channel.equals(homeViewModel.selectedChannel.get())) {
                resetViewModel(homeViewModel);
            }

            if (channel.equals(CatalogProvider.NESN_PLUS)) {
                homeViewModel.nesnPlusOnNow.set(false);
            }
        }

        // Update only for selected channel
        if (channel.equals(homeViewModel.selectedChannel.get())) {
            updateSchedule(homeViewModel, scheduledAirings);
            setSelectedChannelState(homeViewModel, selectedChannelState);
        }
    }

    private void setSelectedChannelState(HomeViewModel homeViewModel, boolean playBackEnabled) {

        // Update only for selected channel
        if (homeViewModel.currentProgramPlaybackUrl.get() != null && !homeViewModel.currentProgramPlaybackUrl.get().isEmpty()) {
            homeViewModel.playbackEnabled.set(playBackEnabled);
        } else {
            homeViewModel.playbackEnabled.set(false);
        }
    }

    private void updateSchedule(HomeViewModel viewModel, List<Airing> scheduledAirings) {

        if (scheduledAirings != null && scheduledAirings.size() > 0) {
            homeViewModel.scheduleNotAvailable.set(false);
            viewModel.currentSchedule.clear();
            viewModel.currentSchedule.addAll(scheduledAirings);
        } else {
            homeViewModel.scheduleNotAvailable.set(true);
        }
    }

    private void updatePreview(HomeViewModel homeViewModel, Airing airing, String channel, String prefix) {
        if (channel.equals(CatalogProvider.NESN)) {
            homeViewModel.nesnPreviewText.set(prefix + airing.getLocaleTitle());
        } else if (channel.equals(CatalogProvider.NESN_PLUS)) {
            homeViewModel.nesnPlusPreviewText.set(prefix + airing.getLocaleTitle());
        }
    }

    private void resetViewModel(HomeViewModel homeViewModel) {
        homeViewModel.currentProgramImageUrl.set(null);
        homeViewModel.currentProgramTitle.set(null);
        homeViewModel.currentSchedule.clear();
        homeViewModel.nesnPlusOnNow.set(false);
    }

    private void updateProgram(HomeViewModel viewModel, Airing currentAiring, boolean loadImage, String channel) {
        if (currentAiring != null && channel.equals(viewModel.selectedChannel.get())) {

            viewModel.currentProgramContentId.set(
                    currentAiring.getContentId()
            );

            // pass language from device
            viewModel.currentProgramTitle.set(
                    currentAiring.getLocaleTitle()
            );

            if (loadImage) {
                viewModel.currentProgramImageUrl.set(
                        currentAiring.getPhotoUriDimens(tileImageWidth, tileImageHeight)
                );
            } else {
                viewModel.currentProgramImageUrl.set(null);
            }

            viewModel.currentProgramPlaybackUrl.set(
                    currentAiring.getPlaybackUrl()
            );
        }
    }

    private void setupProgramTimer(HomeViewModel homeViewModel, DateTime airingDate, final String channel, final MediaData mediaData) {

        long duration = airingDate.getMillis() - System.currentTimeMillis();
        homeView.addSubscription(Observable.timer(duration, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e(e);
                    }

                    @Override
                    public void onNext(Long aLong) {

                        Timber.d("onNext for setupProgramTimer for " + channel);
                        mapSchedule(homeViewModel, mediaData, channel);
                    }
                }));
    }

    private void updateOnNow(Airing currentAiring, List<Airing> scheduledAirings) {
        for (int i = 0; i < scheduledAirings.size(); i++) {
            scheduledAirings.get(i).setFlagType(Airing.FLAG_NONE);
        }
        currentAiring.setFlagType(Airing.FLAG_ON_NOW);
    }

    private void updateUpNext(Airing currentAiring, List<Airing> scheduledAirings) {
        for (int i = 0; i < scheduledAirings.size(); i++) {
            scheduledAirings.get(i).setFlagType(Airing.FLAG_NONE);
        }
        currentAiring.setFlagType(Airing.FLAG_UP_NEXT);
    }

    public HomeViewModel getHomeViewModel() {
        return homeViewModel;
    }

    public void paused() {
        sharedPreferenceHelper.saveLongPreference(SharedPreferenceHelper.LAST_PAUSED_TIME, System.currentTimeMillis());
    }

    public boolean lastViewedToday() {
        return LocalDate.now()
                .compareTo(new LocalDate(new DateTime()
                        .withMillis(sharedPreferenceHelper
                                .getLongPreference(SharedPreferenceHelper.LAST_PAUSED_TIME)))) == 0;
    }

    public void setAuthInfo() {
        this.isAuthenticated = sharedPreferenceHelper.getBooleanPreference(SharedPreferenceHelper.IS_AUTH);
        homeViewModel.userAuthenticated.set(this.isAuthenticated);

        this.authProvider = this.isAuthenticated
                ? sharedPreferenceHelper.getStringPreference(SharedPreferenceHelper.AUTH_PROVIDER)
                : "";
        homeViewModel.loginProvider.set(this.authProvider);

        String providerDisplayName = mvpdProvider.getProviderNameAndLogoUrl(this.authProvider).first;
        String providerLogoUrl = mvpdProvider.getProviderNameAndLogoUrl(this.authProvider).second;

        homeViewModel.providerLogoUrl.set(providerLogoUrl);
        homeViewModel.providerDisplayName.set(providerDisplayName);


        if(this.isAuthenticated && this.mvpdNavigationMap.containsKey(this.authProvider) && homeView != null){
            String navUri = this.mvpdNavigationMap.get(this.authProvider);
            homeView.getHomeViewBinding().providerImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {


                    homeView.navigateUri(navUri);
                }
            });
        }
    }


    public void signUserOut() {
        authorizationManager.deauthorize();
        sharedPreferenceHelper.saveBooleanPreference(SharedPreferenceHelper.IS_AUTH, false);
        homeViewModel.userAuthenticated.set(false);
        homeViewModel.loginProvider.set("");
    }

    private MediaData getSelectedLocalCache() {
        if (homeViewModel.selectedChannel.get() != null && homeViewModel.selectedChannel.get().equals(CatalogProvider.NESN_PLUS)) {
            return localPlusCache;
        } else {
            return localNesnCache;
        }
    }

    public String getAuthProvider() {
        return this.authProvider;
    }

    public String getAuthProviderDisplayName(){
        return this.homeViewModel.providerDisplayName.get();
    }

}