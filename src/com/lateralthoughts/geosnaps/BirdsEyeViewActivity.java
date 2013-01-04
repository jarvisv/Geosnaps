package com.lateralthoughts.geosnaps;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationListener;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.View;
import android.os.Handler;
import android.view.LayoutInflater;
import android.app.ProgressDialog;
import android.app.DialogFragment;
import android.app.Dialog;
import android.app.AlertDialog;
import android.provider.Settings;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;

//list and list adapter related imports
import android.view.Window;
import android.widget.*;
import android.view.ViewGroup;

//utilities
import android.util.Log;

import java.util.ArrayList;

//JSON utilities
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


//things to do & consider:
//1. Looks like the location API access in Instagram needs authentication. We can implement
//the OAuth functionality for this but what do we want to do with access token? Store it on
//the device? Or just let this be obtained every time the user opens app?

//BirdsEyeViewActivity should just contain "Controller" logic. Model is the data that needs to
//come from Instagram or any other service. View needs to be interface that aggregates data and shows
//it either as a maps view or a list view

//Questions:
//1. Android SDK target? Does it matter? I am going with ICS for now
//2. Need authentication with Instagram for the end points. Meaning, these will only work when logged in
//11/15: OK, clarified this on email today. We can store access tokens on the device for this user but how & where?
//Interestingly enough, Android now has an AccountManager API that uses pluggable authentication modules - in
//our case we need an Instagram account added to the account manager and an Instragram authenticator module. But
//we will come back to this later. For now, lets just store the access token in shared prefs - this is not really
//the way to ship but we can solve this problem later

//Architecture: Here is the BirdEyeView (pun intended) description of the big
//pieces that make up the app and the general "principles"
//Each of the Activities should be agnostic to the underlying provider of data. To this end,
//we use an aptly named interface called PicturesGateway, which provides APIs that don't
//really connect to any one particular service
//PicturesGateway is to be responsible for "choosing" the provider of information. In this
//implementation it is hard-wired to instagram but atleast it protects the Activity from this
//detail.
//There is a service class that actually does the donkey work of connecting to Instagram and getting
//the data. This code is nice and separate - all it knows to do it post a message, get a response
//and send it to the resultreceiver!
//We have a couple of other activities to show the GridView of images and also the full screen gallery
//like scroll. Both of these implement a custom JSON parser (because they need to look at different
//pieces of data from the response) and also have a custom adapter for their respective views
//Now, this was some interesting work: for the grid view, the adapter is the same as BirdEye activity
//but for images we just have a URI. So we set the URI and return the image view but also spawn a background
//bitmap loader task to load the bitmap from network and put it on the grid at the specified item.
//A couple of TODO here: First, check the number of background workers you are creating. We should share one
//worker task to function on behalf of all images. Secondly, time-permitting, implementing a image-loading
//gif/animation on each of the grid items. That should help a bit with user experience.
//For whatever reason, Gallery widget is slow. I can't see what's wrong but it's just not looking great now!

//Logic: Here is the approach to the activity logic
//Internally, we are going to use a handler that will be used as a single
//point of entry into making UI changes. This handler will detect the state - such as
//waiting for location, waiting for data etc and either show dialogs or update dialogs etc
//For the list, I am going to use a simple BaseAdapter and extend it to provide the
// list item view back to the list.
//TODO: if time permits consider this: right now the list item view is just "text" but I think
//at the very least we should add the number of pictures available as part of that tag to the list

//11/19: TODO: Big item. This is a relatively major refactor but I think we absolutely need to do this
//Right now, the activities don't share the PicturesGateway instance and are instead just creating their
//own. This is both wasteful and very ugly too. We want the PicturesGateway to be shared by the entire app
//so that we can implement pre fetching and so forth.
//Interesting: Android has a getLastKnownLocation() API that is supposed to return faster. Maybe we can use that
//to quickly display a display while we work on getting more accurate data?

//User Experience: (updated on-going basis)
//1. I am storing the authentication token in the prefs after the first time the user signs-in. But we should show
//some kind of "this is how this app works" flow before asking user to sign into instagram. Otherwise, creepy :-(
//2. In the title bar, we should probably show the current logged in user. And hopefully, we can click on it and sign out
//or login in as someone else
//3. Ok, this is annoying: location after location selected from the list has 0 pictures! As a user I am finding it annoying
//that I will select something from the list and only to be told after a navigation that there are no images? Why even
// bother adding these items to the list?
//4. Initially, we show modal dialogs to indicate we are getting location or getting data. But when location is updated why
//block with a modal? Instead, how about rendering a spinner progress on the title bar to indicate something is up

//Making things better:
//Using this app over the past couple of days probably the biggest take away is that without proper content this app is crap.
//Content needs to be of sufficient volume but just as importantly have some relevance. We definitely need to think through
//and iterate to understand what filters make sense. Do we filter by trying to see if any of the posts/tags are from people
//we know? Or we can be smart about knowing a user's "home base" and when away from home base find popular things in that place.
//for example, I live in Fremont, if we know that and the current location is SFO may be increasing the sweep radius and get me
//interesting/popular things in SFO? aka, maybe I am a tourist in SFO so show me stuff that will make sense. What else can we do?
//Everything so far is get - why not inspire users to contribute their own pictures to some of the tags they enjoy. This way
//we will also make the content side voluminous and considering it came from our user hopefully the relevance factor of the picture
//has more weight?

public class BirdsEyeViewActivity extends ListActivity implements LocationListener {
    private final String TAG = "BirdsEyeViewActivity";

    //constants that belong in controller logic:
    private final long MIN_TIME_BETWEEN_UPDATES = 10000;  //10 seconds
    private final float MIN_DISTANCE_THRESHOLD = 25.0f; //25 metres is the resolution we seek

    
    //Location related variables
    LocationManager mLocationManager;

    @Override
    public void onCreate(Bundle icicle){
        super.onCreate(icicle);
        mLocationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        setTitle(R.string.birds_eye_activity_title);
    }
    
    @Override
    public void onStart(){
        super.onStart();
        if(!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            //onStart is going to called everytime user returns to this activity
            //so its a good place to be sure that location settings are available
            //before continuing
            enableLocationSettings();
        }else
            mLocationSettingsEnabled = true;
    }

    private void enableLocationSettings() {
        EnableLocationSettingsDialog d = new EnableLocationSettingsDialog();
        d.show(getFragmentManager(),"Settings");
    }
    
    @Override
    public void onResume(){
        super.onResume();

        mLastKnownLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if(null == mPicturesGateway && mLocationSettingsEnabled){
            mLocationsAdapter = new LocationsAdapter(this);
            mPicturesGateway = new PicturesGateway(this, new ResultReceiver(mHandler){
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData){

                    //TODO: I think we should do the parsing outside the adapter and just submit a list
                    //of names
                    mLocationsAdapter.parseAndAddLocations(parseInterestingTagsResult(resultData.getString("result")));
                    setListAdapter(mLocationsAdapter);
                    mUIReadinessState = DISPLAY_STATE_READY;
                    mUIHandler.post(mUpdateUIElements);

                }
            });
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_THRESHOLD, this);
            if(null != mLastKnownLocation){
                mUIReadinessState = WAITING_FOR_LOCATION_TAGS;
            }else{
                mUIReadinessState = WAITING_FOR_LOCATION;
            }
            mUIHandler.post(mUpdateUIElements);
            mAccessToken = mPicturesGateway.getReadyStatus();
        }else if(DISPLAY_STATE_READY == mUIReadinessState && mLocationSettingsEnabled){
            //looks like we are just returning to a previous state. We will still get the new location (if any)
            //but there is no need to display a progress dialog
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_THRESHOLD, this);
        }
    }
    
    @Override
    public void onPause(){
        super.onPause();
        if(null != mLocationManager)
            mLocationManager.removeUpdates(this);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }
    
    public void onClick(View view){

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id){
        String selectedItemId = ((LocationDetails)mLocationsAdapter.getItem(position)).id;
        Intent intent = new Intent(this, PicsByLocation.class);
        intent.putExtra(PicsByLocation.ACCESS_TOKEN,mAccessToken);
        intent.putExtra(PicsByLocation.LOCATION_ID,selectedItemId);
        startActivity(intent);
        Log.e(TAG,"id = " + selectedItemId);
    }

    //APIs from the LocationListener interface
    public void onProviderDisabled(String str){

    }

    public void onProviderEnabled(String str){

    }

    public void onLocationChanged(Location location){
        if(null == location)
            return;

        if(location != mLastKnownLocation){
           mLastKnownLocation = location;
           mUIReadinessState = WAITING_FOR_LOCATION_TAGS;
           mHandler.post(mUpdateUIElements);
        }
    }

    public void onStatusChanged(String desc, int result, Bundle data){
        Log.e(TAG,"onStatusChanged is invoked. dec = " + desc + "result = " + result);

    }

    private final Handler mHandler = new Handler();
    
    private Runnable mUpdateUIElements = new Runnable() {
        @Override
        public void run() {
            switch(mUIReadinessState){
                case WAITING_FOR_LOCATION:
                    //let's whip out a dialog and let the user know we are waiting for location
                    //data to be available
                    if(null == mLocatingDialog)
                        mLocatingDialog = new LocatingUserDialogFragment();
                    
                    mLocatingDialog.show(getFragmentManager(),"locating...");
                    break;

                case WAITING_FOR_LOCATION_TAGS:
                    mPicturesGateway.getInterestingTagsByLocation(mLastKnownLocation,mAccessToken);
                    if(null == mFetchingContentDialog)
                        mFetchingContentDialog = new FetchingContentDialogFragment();

                    mFetchingContentDialog.show(getFragmentManager(),"fetching");
                    if(null != mLocatingDialog){
                        if(null != mLocatingDialog.getDialog())
                            mLocatingDialog.getDialog().dismiss();
                    }
                    break;

                case DISPLAY_STATE_READY:
                    if(null != mFetchingContentDialog && null != mFetchingContentDialog.getDialog())
                        mFetchingContentDialog.getDialog().dismiss();
                    break;

                case READY_STATE_UNKNOWN:
                    break;

                default:
                    break;
            }

        }
    };

    private class LocationsAdapter extends BaseAdapter {
        ArrayList<LocationDetails> locationsData;
        private Context mContext;

        LocationsAdapter(Context context) {
            super();
            mContext = context;
        }
        
        public void parseAndAddLocations(ArrayList<LocationDetails> locations){
            Log.e(TAG,"Looks like we are all set here..");
            this.locationsData = locations;
        }

        public int getCount() {
            return locationsData.size();
        }

        public Object getItem(int position) {
            return locationsData.get(position);
        }

        public long getItemId(int position) {
            return 0;
        }

        public View getView(int position, View convertView,ViewGroup parent) {
            View row = convertView;
            if (row==null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.locations_list_item, parent, false);
            }
            TextView rowItemText = (TextView)row.findViewById(R.id.location_name);
            String text = ((LocationDetails)getItem(position)).name;
            rowItemText.setText(text);
            return row;
        }
    }
    
    private ArrayList<LocationDetails> parseInterestingTagsResult(String resultString){
        ArrayList<LocationDetails> locations = new ArrayList<LocationDetails>();
        LocationDetails itemDetails = null;
        try{
            JSONObject json = new JSONObject(resultString);

            JSONArray nameArray = json.getJSONArray("data");

            for (int i = 0; i < nameArray.length(); i++) {
                itemDetails = new LocationDetails();
                JSONObject arr = nameArray.getJSONObject(i);

                //String id = arr.getString("id");
                itemDetails.id = arr.getString("id");
                itemDetails.name = arr.getString("name");
                locations.add(itemDetails);
            }
        }catch(JSONException ex1){

        }
        return locations;
    }

    public class EnableLocationSettingsDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.enable_settings_title);
            builder.setMessage(R.string.location_settings_needed)
                    .setPositiveButton(R.string.enable_it, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(settingsIntent);
                        }
                    })
                    .setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public class LocatingUserDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            ProgressDialog.Builder builder = new ProgressDialog.Builder(getActivity());
            builder.setTitle(R.string.please_wait_dialog_title);
            builder.setMessage("Identifying your location...");
            builder.setCancelable(false);
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public class FetchingContentDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            ProgressDialog.Builder builder = new ProgressDialog.Builder(getActivity());
            builder.setTitle(R.string.please_wait_dialog_title);
            builder.setMessage(R.string.getting_interesting_stuff_msg);
            builder.setCancelable(false);
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    private PicturesGateway mPicturesGateway;
    private Location mLastKnownLocation;
    private String mAccessToken;
    LocationsAdapter mLocationsAdapter;
    
    //we will use a handler to post messages to our ui updating routine
    private Handler mUIHandler = new Handler();

    //consts that identify current activity status
    public static final int WAITING_FOR_LOCATION = 101;
    public static final int WAITING_FOR_LOCATION_TAGS = 102;
    public static final int READY_STATE_UNKNOWN = 103;
    public static final int DISPLAY_STATE_READY = 104;
    
    private int mUIReadinessState;
    
    private class LocationDetails{
        String id;
        String name;
    }
    
    //
    LocatingUserDialogFragment mLocatingDialog;
    FetchingContentDialogFragment mFetchingContentDialog;
    private boolean mLocationSettingsEnabled = false;
}
