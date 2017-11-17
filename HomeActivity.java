package com.nesn.nesnplayer.home;

import android.content.DialogInterface;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;

import com.nesn.nesnplayer.NesnApplication;
import com.nesn.nesnplayer.R;
import com.nesn.nesnplayer.analytics.Analytics;
import com.nesn.nesnplayer.auth.AuthenticationActivity;
import com.nesn.nesnplayer.core.BaseActivity;
import com.nesn.nesnplayer.core.Constant;
import com.nesn.nesnplayer.core.ExceptionManager;
import com.nesn.nesnplayer.core.NetworkManager;
import com.nesn.nesnplayer.core.SharedPreferenceHelper;
import com.nesn.nesnplayer.home.databinding.HomeActivityViewBinding;
import com.nesn.nesnplayer.playback.PlaybackActivity;
import com.nesn.nesnplayer.playback.PlaybackData;

import javax.inject.Inject;

import timber.log.Timber;

import static com.nesn.nesnplayer.R.style.AlertDialogStyle;

/**
 * Created by DavidSE on 2/19/17.
 */

/**
 * HomeActivity
 */
public class HomeActivity extends BaseActivity implements HomeView {

    private static final String SCREEN_NAME = "ScheduleView";
    protected PlaybackData playbackDataCache;
    // CHECKSTYLE:OFF
    @Inject
    HomePresenter homePresenter;

    @Inject
    SharedPreferenceHelper sharedPreferenceHelper;

    @Inject
    ExceptionManager exceptionManager;

    @Inject
    NetworkManager networkManager;

    @Inject
    Analytics analytics;

    private HomeActivityViewBinding viewDataBinding;

    private View.OnClickListener logoutListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("Sign Out From Provider")
                    .setMessage(String.format("You are signed in with %s. \n Would you like to sign out?",
                            homePresenter.getAuthProviderDisplayName()))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            homePresenter.signUserOut();
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            // toggle viewmodel
                        }
                    })
                    .create().show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewDataBinding = DataBindingUtil.setContentView(this, R.layout.activity_home);

        Timber.d("Start wire up");
        wireUpComponents();

        Timber.d("End wire up");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // databinding

        viewDataBinding.setPresenter(homePresenter);

        homePresenter.attachView(this);

        viewDataBinding.signIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // clear playback data
                HomeActivity.this.playbackDataCache = null;
                navigateAuth(null, true, false);
            }
        });

        viewDataBinding.providerText.setOnClickListener(logoutListener);
        // viewDataBinding.providerImage.setOnClickListener(logoutListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        homePresenter.paused();
    }

    @Override
    protected void wireUpComponents() {
        NesnApplication.component().inject(this);
    }

    @Override
    public HomeActivityViewBinding getHomeViewBinding() {
        return viewDataBinding;
    }

    @Override
    public void navigatePlayback(boolean isAuthenticated, PlaybackData playbackData) {

        if (networkManager.isNetworkConnected()) {

            analytics.trackEvent(Analytics.CATEGORY_SCHEDULE_VIEW, Analytics.ACTION_BUTTON_PRESS, Analytics.LABEL_PLAY);

            Intent mainIntent = new Intent(this, PlaybackActivity.class);
            mainIntent.putExtra(Constant.PLAYPACK_DATA, playbackData);

            if (isAuthenticated) {
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(mainIntent);
            } else {
                this.playbackDataCache = playbackData;
                navigateAuth(playbackData, false, true);
            }
        } else {
            exceptionManager.handleErrorNoNetwork(this, false);
        }
    }

    @Override
    public void navigateUri(String uri){
        String message = "You will be taken to an external app";

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialogStyle)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(uri));
                        startActivity(i);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.show();


    }


    public void navigateAuth(PlaybackData playbackData, boolean addFlags, boolean showDialog) {
        if (networkManager.isNetworkConnected()) {

            Intent authIntent = new Intent(HomeActivity.this, AuthenticationActivity.class);
            if (addFlags) {
                authIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }

            if (playbackData != null) {
                authIntent.putExtra(Constant.PLAYPACK_DATA, playbackData);
            }

            if (showDialog) {
                new AlertDialog.Builder(HomeActivity.this)
                        .setMessage(R.string.error_login_required)
                        .setPositiveButton(R.string.sign_in, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {

                                startActivityForResult(authIntent, Constant.REQUEST_AUTH);

                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                dialog.dismiss();
                            }
                        }).create().show();
            } else {
                startActivityForResult(authIntent, Constant.REQUEST_AUTH);
            }

        } else {
            exceptionManager.handleErrorNoNetwork(this, false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constant.REQUEST_AUTH && resultCode == Constant.AUTH_SUCCESS) {
            homePresenter.setAuthInfo();
            if (this.playbackDataCache != null) {
                homePresenter.paused();
                homePresenter.startPlayback();
            }
        }
    }


    @Override
    public Analytics analytics() {
        return analytics;
    }

}