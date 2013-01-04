package com.lateralthoughts.geosnaps;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationListener;
import android.os.ResultReceiver;
import android.location.Location;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.app.AlertDialog;

import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import java.util.ArrayList;


public class PicturesGateway{
    private final String TAG = "PicturesGateway";
    
    private ArrayList<ParcelableNameValuePair> mHeaders;
    private ArrayList<ParcelableNameValuePair> mParams;
    
    //usually constants but we don't want to put it
    //in any one source file here
    private String mLocationSearchTag;
    private String mRecentByLocationTag;
    private String mLatTag;
    private String mLonTag;

    //lets point to instagram here...
    public static final String IG_API_BASE_URI = "https://api.instagram.com/v1";
    public static final String IG_PREFS_AUTH_KEYS = "GeoSnapsIGAuthKeys";

    //static shared strings
    //TODO: do these belong here?
    public static final String IG_ACCESS_TOKEN_DEFAULT = "UnknownAccessToken";
    public static final String IG_ACCESS_TOKEN_KEY = "ACCESS_TOKEN";

    //
    private void addHeaders(String name, String value){
        mHeaders.add(new ParcelableNameValuePair(name, value));
    }
    
    private void addParams(String name, String value){
        mParams.add(new ParcelableNameValuePair(name,value));
    }

    public PicturesGateway(Context context, ResultReceiver receiver){
        mContext = context;
        mReceiver = receiver;
        mLocationSearchTag = mContext.getResources().getString(R.string.ig_location_search);
        mRecentByLocationTag = mContext.getResources().getString(R.string.ig_recent_by_location);
        mLatTag = mContext.getResources().getString(R.string.ig_param_lat);
        mLonTag = mContext.getResources().getString(R.string.ig_param_lon);
        mPrefs = mContext.getSharedPreferences(IG_PREFS_AUTH_KEYS, Context.MODE_PRIVATE);
        
        initializeHttpFields();
    }

    public void getPicsByTag(String tag, String access_token){
        mParams.clear();

        addParams(mContext.getString(R.string.ig_id_tag), tag);
        addParams(mContext.getString(R.string.ig_access_token),access_token);
        String baseUri = IG_API_BASE_URI;
        baseUri += String.format(mContext.getResources().getString(R.string.ig_recent_by_location), tag);

        Intent intent = new Intent(mContext, InstagramRestClient.class);
        intent.putExtra("url",baseUri);
        intent.putParcelableArrayListExtra("headers", mHeaders);
        intent.putParcelableArrayListExtra("params",mParams);
        intent.putExtra("receiver", mReceiver);
        mContext.startService(intent);
    }

    public void getHighResImages(String tag, String access_token){
        mParams.clear();

        addParams(mContext.getString(R.string.ig_id_tag), tag);
        addParams(mContext.getString(R.string.ig_access_token),access_token);
        String baseUri = IG_API_BASE_URI;
        baseUri += String.format(mContext.getResources().getString(R.string.ig_recent_by_location), tag);

        Intent intent = new Intent(mContext, InstagramRestClient.class);
        intent.putExtra("url",baseUri);
        intent.putParcelableArrayListExtra("headers", mHeaders);
        intent.putParcelableArrayListExtra("params",mParams);
        intent.putExtra("receiver", mReceiver);
        mContext.startService(intent);
    }

    /**
     * Called by the app to get a list of interesting "tags" around a given
     * location. The location is usually the location of the device itself but need not be.
     *
     * @param location - standard android location structure with latitude and longitude set
     *                 correctly
     */
    public void getInterestingTagsByLocation(Location location, String access_token){
        mParams.clear();

        //ok, keep this simple enough. Parse the location object to extract latitude
        //and longitude which are essentially the params.
        //The access token is a param as well
        //SFO
        //lat = 37.8078421; //location.getLatitude();
        //lon = -122.4761012; //location.getLongitude();
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        addParams(mLatTag,String.valueOf(lat));
        addParams(mLonTag,String.valueOf(lon));
        addParams("access_token",access_token);
        String baseUri = IG_API_BASE_URI + mLocationSearchTag;

        Intent intent = new Intent(mContext, InstagramRestClient.class);
        intent.putExtra("url",baseUri);
        intent.putParcelableArrayListExtra("headers", mHeaders);
        intent.putParcelableArrayListExtra("params",mParams);
        intent.putExtra("receiver", mReceiver);
        mContext.startService(intent);
    }

    private void initializeHttpFields(){
        mHeaders = new ArrayList<ParcelableNameValuePair>();
        mParams = new ArrayList<ParcelableNameValuePair>();
        addHeaders("Content-Type","application/json");
    }
    
    public String getReadyStatus(){
        if(null == mPrefs){
            Log.e(TAG,"No handle to prefs! This shouldn't happen!");
            return null;
        }

        return mPrefs.getString(IG_ACCESS_TOKEN_KEY, IG_ACCESS_TOKEN_DEFAULT);
    }
    
    public String performAuthenticationProtocol(){
        //mContext.getResources()
        Intent intent = new Intent(mContext, AuthenticateActivity.class);
        mContext.startActivity(intent);
        return null;
    }
    
    private Context mContext;
    private ResultReceiver mReceiver;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEditor;
    private RelativeLayout mAuthenticateLayout;
}


