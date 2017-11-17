package com.nesn.nesnplayer.auth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.bamtech.sdk.activation.ActivationManager;
import com.bamtech.sdk.api.models.activation.MVPDRegistration;
import com.bamtech.sdk.authentication.AuthenticationManager;
import com.nesn.nesnplayer.NesnApplication;
import com.nesn.nesnplayer.R;
import com.nesn.nesnplayer.core.BaseActivity;
import com.nesn.nesnplayer.core.Constant;
import com.nesn.nesnplayer.core.ExceptionManager;
import com.nesn.nesnplayer.core.SharedPreferenceHelper;
import com.nesn.nesnplayer.playback.PlaybackData;

import javax.inject.Inject;

import okhttp3.Request;
import rx.CompletableSubscriber;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

//import com.nesn.nesnplayer.auth.dagger.DaggerAuthComponent;

/**
 * Created by DavidSE on 3/23/17.
 */

public class AuthenticationActivity extends BaseActivity {

    @Inject
    AuthenticationManager authenticationManager;

    @Inject
    ActivationManager activationManager;

    @Inject
    SharedPreferenceHelper sharedPreferenceHelper;

    @Inject
    ExceptionManager exceptionManager;

    private MVPDRegistration registration;

    private AuthenticationWebView authenticationWebView;

    private String activationBaseLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getSerializableExtra(Constant.PLAYPACK_DATA) != null) {
            PlaybackData playbackData = (PlaybackData) getIntent().getExtras().get("PlaybackData");
        }

        setContentView(R.layout.activity_auth);

        this.authenticationWebView = (AuthenticationWebView) findViewById(R.id.authenticationWebView);

        wireUpComponents();

        configureAuthenticationWebView();

        activationBaseLocation = this.getString(R.string.activation_base);

        sdkBeginRegistration(
                this.getString(R.string.activation_url)
        );
    }

    protected void sdkBeginRegistration(final String activationUrlTemplate) {

        activationManager.getMVPDRegistration()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<MVPDRegistration>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e(e + ": An error occured getting MVPD stuff");
                        // CHECKSTYLE:OFF
                        exceptionManager.handleError(AuthenticationActivity.this, e, true);
                        //navigateHome(false);
                    }

                    @Override
                    public void onNext(MVPDRegistration mvpdRegistration) {
                        registration = mvpdRegistration;
                        Timber.d("Got registration object.");

                        String authUrl = String.format(activationUrlTemplate,
                                            mvpdRegistration.getRegistrationCode());

                        authenticationWebView.loadUrl(authUrl);
                    }
                });

    }

    protected void sdkActivateUser(final String mvpdId) {
        activationManager.activateMVPDRegistration(mvpdId, AuthenticationActivity.this.registration)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableSubscriber() {
                    @Override
                    public void onCompleted() {
                        sharedPreferenceHelper.saveBooleanPreference(
                                SharedPreferenceHelper.IS_AUTH, true
                        );
                        sharedPreferenceHelper.saveStringPreference(
                                SharedPreferenceHelper.AUTH_EXP,
                                String.valueOf(AuthenticationActivity.this.registration.getExpiresAt().getMillis())
                        );
                        sharedPreferenceHelper.saveStringPreference(
                                SharedPreferenceHelper.AUTH_PROVIDER,
                                mvpdId
                        );
                        navigateHome(true);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e("Exception on final activation step: " + e);
                        // CHECKSTYLE:OFF
                        exceptionManager.handleError(AuthenticationActivity.this, e, true);
                        //navigateHome(false);
                    }

                    @Override
                    public void onSubscribe(Subscription d) {

                    }
                });

    }

    protected void configureAuthenticationWebView() {

        WebSettings browserSettings = authenticationWebView.getSettings();
        browserSettings.setJavaScriptEnabled(true);
        browserSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        browserSettings.setDomStorageEnabled(true);

        WebViewClient client = new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                Timber.d(url);
                if (url.contains(activationBaseLocation)
                        && url.contains("device_type=mobile")
                        && url.contains("success=true")) {

                    final Request build = new Request.Builder().url(url).build();
                    final String mvpdId = build.url().queryParameter("mso_id");

                    sdkActivateUser(mvpdId);

                }

            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Timber.d("Page started in webview: " + url);
            }
        };

        //NB: clear out cache before logging in
        authenticationWebView.clearCache(true);
        authenticationWebView.setWebViewClient(client);
    }

    public void navigateHome(final boolean authSuccess) {
        Intent returnIntent = new Intent();
        setResult(
                authSuccess ? Constant.AUTH_SUCCESS : Constant.AUTH_FAIL,
                returnIntent
        );
        finish();
    }

    @Override
    protected void wireUpComponents() {
        NesnApplication.component().inject(this);
    }

}
