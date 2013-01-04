package com.lateralthoughts.geosnaps;

import android.app.*;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;

import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.View;
import android.os.Handler;
import android.widget.ViewSwitcher;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

//list and list adapter related imports
import android.widget.*;
import android.view.ViewGroup;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.content.res.Resources;


//utilities
import android.util.Log;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.net.URL;

//JSON utilities
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PictureSurfer extends Activity implements ViewSwitcher.ViewFactory {
    private static final String TAG = "PictureSurfer";

    //const values to communicate between birdeye activity and this
    public final static String LOCATION_ID = "location_id";
    public final static String ACCESS_TOKEN = "access_token";

    @Override
    public void onCreate(Bundle icicle){
        super.onCreate(icicle);
        Intent launchIntent = getIntent();
        mLocationId = launchIntent.getStringExtra(LOCATION_ID);
        mAccessToken = launchIntent.getStringExtra(ACCESS_TOKEN);
        if(null == mLocationId || null == mAccessToken){
            //TODO:this really shouldn't happen. Without either of this data
            //there is nothing for us to do.
            //Alert the user and exit
        }
        mUIReadinessState = UI_STATE_UNKNOWN;
    }

    @Override
    public void onResume(){
        super.onResume();
        mUIReadinessState = FETCHING_FULLSCREEN_IMAGE;
        mHandler.post(mUpdateUIElements);
    }
    
    @Override
    public View makeView(){
        return null;
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    private Runnable mUpdateUIElements = new Runnable() {
        @Override
        public void run() {
            switch(mUIReadinessState){
                case FETCHING_FULLSCREEN_IMAGE:
                    mPicturesGateway.getHighResImages(mLocationId, mAccessToken);
                    break;

                case IMAGE_READY:
                    break;

                default:
                    break;
            }

        }
    };

    ArrayList<PicturesDetails> parseLocationMediaSearch(String resultString){
        ArrayList<PicturesDetails> imageSources = new ArrayList<PicturesDetails>();
        PicturesDetails itemDetails = null;
        try{
            JSONObject json = new JSONObject(resultString);

            JSONArray nameArray = json.getJSONArray("data");

            for (int i = 0; i < nameArray.length(); i++) {
                itemDetails = new PicturesDetails();
                JSONObject arr = nameArray.getJSONObject(i);

                //String id = arr.getString("id");
                itemDetails.id = arr.getString("id");
                JSONObject imagesObj = arr.getJSONObject("images");
                JSONObject thumbObj = imagesObj.getJSONObject("thumbnail");
                itemDetails.uri = thumbObj.getString("url");
                //getString("images");
                imageSources.add(itemDetails);
            }
        }catch(JSONException ex1){

        }
        return imageSources;
    }

    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private String data = null;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            data = params[0];
            Bitmap bmp = null;
            URL url = null;
            try{
                url = new URL(data);
            }catch(MalformedURLException ex){

            }
            try{
                InputStream is = url.openStream();
                bmp = BitmapFactory.decodeStream(is);
            }catch(IOException ex2){

            }
            return bmp;
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }


    private class PicturesDetails{
        String name;
        String id;
        String uri;
    }

    Handler mUIHandler = new Handler();

    //we need access to the picturesgateway too
    PicturesGateway mPicturesGateway;
    Handler mHandler = new Handler();
    GridView mGridView;


    private int mUIReadinessState;
    private String mLocationId;
    private String mAccessToken;

    //consts to control UI state
    private final int UI_STATE_UNKNOWN = 301;
    private final int FETCHING_FULLSCREEN_IMAGE = 302;
    private final int IMAGE_READY = 303;
}
