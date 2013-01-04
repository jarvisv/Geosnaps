package com.lateralthoughts.geosnaps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.os.Bundle;

import android.util.Log;
import android.webkit.WebViewClient;

public class AuthenticateActivity extends Activity {
    private final String TAG = "AuthenticateActivity";
    private final String REDIRECT_URI = "http://warpedideas.com";
    private final String ACCESS_TOKEN_KEY = "access_token=";
    private static final String INSTAGRAM_OAUTH_URI = "https://instagram.com/oauth/authorize/?client_id=9a2b56c383694da0b0970e4c5d33539d&redirect_uri=http://warpedideas.com&response_type=token";

    @Override
    public void onCreate(Bundle icicle){
        super.onCreate(icicle);
        mPrefs = getSharedPreferences(PicturesGateway.IG_PREFS_AUTH_KEYS, MODE_PRIVATE);
        if(mPrefs.getString(PicturesGateway.IG_ACCESS_TOKEN_KEY, PicturesGateway.IG_ACCESS_TOKEN_DEFAULT) != PicturesGateway.IG_ACCESS_TOKEN_DEFAULT){
            //looks like we already have an access key
            finishAuthenticationProtocol();
            return;
        }

        mContentView = new WebView(this);
        mWebSettings = mContentView.getSettings();
        mWebSettings.setJavaScriptEnabled(true);
        mContentView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url){
                Log.e("Vinodh","shouldOverridecalled with url = " + url);
                if ( url.startsWith(REDIRECT_URI) ) {
                    String accessToken = null;

                    // extract OAuth2 access_token appended in url
                    if ( url.indexOf(ACCESS_TOKEN_KEY) != -1 ) {
                        accessToken = url.substring(url.indexOf(ACCESS_TOKEN_KEY)+ACCESS_TOKEN_KEY.length());
                        Log.e("Vinodh","accessToken = " + accessToken);

                        // store in default SharedPreferences
                        SharedPreferences.Editor e = mPrefs.edit();
                        //getSharedPreferences(PicturesGateway.IG_PREFS_AUTH_KEYS, Context.MODE_PRIVATE).edit();
                        e.putString(PicturesGateway.IG_ACCESS_TOKEN_KEY, accessToken);
                        e.commit();
                        finishAuthenticationProtocol();
                    }
                    // don't go to redirectUri
                    return true;
                }
                return false;
            }
        });
        setContentView(mContentView);

        mContentView.loadUrl(INSTAGRAM_OAUTH_URI);
    }

    @Override
    public void onStart(){
        super.onStart();
    }

    @Override
    public void onResume(){
        super.onResume();
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    private void finishAuthenticationProtocol(){
        Intent intent = new Intent(AuthenticateActivity.this, BirdsEyeViewActivity.class);
        startActivity(intent);
        finish();
    }

    //object privates
    private WebSettings mWebSettings;
    private WebView mContentView;
    private SharedPreferences mPrefs;
}
