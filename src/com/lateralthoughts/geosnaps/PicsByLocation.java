package com.lateralthoughts.geosnaps;

import android.app.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;

import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Handler;
import android.view.View.OnClickListener;
import android.os.Parcel;
import android.os.Parcelable;
import android.app.ProgressDialog;

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
import android.widget.AdapterView.OnItemClickListener;

//utilities
import android.util.Log;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.net.URL;

//JSON utilities
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PicsByLocation extends Activity {
    private static final String TAG = "PicsByLocation";

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
        setContentView(R.layout.image_grid);
        mGridView = (GridView) findViewById(R.id.gridview);
        mUIReadinessState = UI_STATE_UNKNOWN;

        mGridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Intent intent = new Intent(PicsByLocation.this, PictureFlipperActivity.class);
                intent.putExtra(PictureFlipperActivity.IMAGE_SOURCES, mImagesAdapter.getImageSources());
                startActivity(intent);
            }
        });
    }
    
    @Override
    public void onResume(){
        super.onResume();
        mImagesAdapter = new PicturesAdapter(this);
        if(null == mPicturesGateway){
            mPicturesGateway = new PicturesGateway(this, new ResultReceiver(mHandler){
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData){
                    //TODO: I think we should do the parsing outside the adapter and just submit a list
                    //of names
                    mImagesAdapter.setImageSources(parseLocationMediaSearch(resultData.getString("result")));
                    mUIReadinessState = IMAGE_DATA_READY;
                    mUIHandler.post(mUpdateUIElements);
                    mGridView.setAdapter(mImagesAdapter);
                }
            });
        }
        if (UI_STATE_UNKNOWN == mUIReadinessState)
            mUIReadinessState = FETCHING_IMAGE_DATA;
        mHandler.post(mUpdateUIElements);
    }
    
    @Override
    public void onPause(){
        super.onPause();
    }

    private Runnable mUpdateUIElements = new Runnable() {
        @Override
        public void run() {
            switch(mUIReadinessState){
                case FETCHING_IMAGE_DATA:
                    //let's whip out a dialog and let the user know we are waiting for location
                    //data to be available
                    mGettingPicturesDialog = new LocationPicsDialog();
                    if(null == mGettingPicturesDialog)
                        mGettingPicturesDialog = new LocationPicsDialog();
                    
                    mGettingPicturesDialog.setStyle(ProgressDialog.STYLE_SPINNER, android.R.style.Theme_Holo);
                    mGettingPicturesDialog.show(getFragmentManager(),"locating...");
                    mPicturesGateway.getPicsByTag(mLocationId, mAccessToken);
                    break;

                case IMAGE_DATA_READY:
                    if(null != mGettingPicturesDialog && (null != mGettingPicturesDialog.getDialog()))
                        mGettingPicturesDialog.getDialog().cancel();
                    break;

                default:
                    break;
            }

        }
    };

    public class LocationPicsDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            ProgressDialog.Builder builder = new ProgressDialog.Builder(getActivity());
            builder.setTitle(R.string.please_wait_dialog_title);
            builder.setMessage(R.string.getting_pictures_dialog_text);
            builder.setCancelable(true);
            return builder.create();
        }
    }

    public class PicturesAdapter extends BaseAdapter {
        private Context mContext;
        private ArrayList<PicturesDetails> mContent;

        public PicturesAdapter(Context c) {
            mContext = c;
        }

        public int getCount() {
            return mContent.size();
        }

        public Object getItem(int position) {
            return mContent.get(position);
        }

        public long getItemId(int position) {
            return 0; //mContent;
        }
        
        public void setImageSources(ArrayList<PicturesDetails> content){
            mContent = content;
        }

        public ArrayList<PicturesDetails> getImageSources(){
            return mContent;
        }

        // create a new ImageView for each item referenced by the Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {  // if it's not recycled, initialize some attributes
                imageView = new ImageView(mContext);
                imageView.setLayoutParams(new GridView.LayoutParams(85, 85));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setPadding(8, 8, 8, 8);
            } else {
                imageView = (ImageView) convertView;
            }
            
            String loc = ((PicturesDetails)getItem(position)).thumbUri;

            imageView.setImageURI(Uri.parse(loc));
            loadBitmap(loc,imageView);
            return imageView;
        }

        public void loadBitmap(String loc, ImageView imageView) {
            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            task.execute(loc);
        }
    }

    ArrayList<PicturesDetails> parseLocationMediaSearch(String resultString){
        ArrayList<PicturesDetails> imageSources = new ArrayList<PicturesDetails>();
        PicturesDetails itemDetails = null;
        try{
            JSONObject json = new JSONObject(resultString);

            JSONArray nameArray = json.getJSONArray("data");
            if(0 == nameArray.length()){
                //looks like there are no pictures...this is actually annoying
                NoPicturesDialog d = new NoPicturesDialog();
                d.setCancelable(false);
                d.show(getFragmentManager(), "NoPictures");
            }

            for (int i = 0; i < nameArray.length(); i++) {
                itemDetails = new PicturesDetails();
                JSONObject arr = nameArray.getJSONObject(i);

                //String id = arr.getString("id");
                itemDetails.id = arr.getString("id");
                JSONObject imagesObj = arr.getJSONObject("images");
                JSONObject thumbObj = imagesObj.getJSONObject("thumbnail");
                itemDetails.thumbUri = thumbObj.getString("url");

                JSONObject standardResObj = imagesObj.getJSONObject("standard_resolution");
                itemDetails.standardResUri = standardResObj.getString("url");
                //getString("images");
                imageSources.add(itemDetails);
            }
        }catch(JSONException ex1){

        }
        return imageSources;
    }

    public class NoPicturesDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.no_pics_title);
            builder.setCancelable(false);
            builder.setMessage(R.string.ack_no_pics)
                    .setPositiveButton(R.string.ok_no_pics, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
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

    Handler mUIHandler = new Handler();
    
    //we need access to the picturesgateway too
    PicturesGateway mPicturesGateway;
    public PicturesAdapter mImagesAdapter;
    Handler mHandler = new Handler();
    GridView mGridView;
    

    private int mUIReadinessState;
    private String mLocationId;
    private String mAccessToken;

    //consts to control UI state
    private final int UI_STATE_UNKNOWN = 201;
    private final int FETCHING_IMAGE_DATA = 202;
    private final int IMAGE_DATA_READY = 203;
    
    //consts for dialog types
    private final int LOADING_DIALOG = 1;
    LocationPicsDialog mGettingPicturesDialog;
    NoPicturesDialog mNoPicsDialog;
}


