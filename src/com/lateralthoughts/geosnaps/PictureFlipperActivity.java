package com.lateralthoughts.geosnaps;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.*;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;


//
public class PictureFlipperActivity extends Activity implements ViewSwitcher.ViewFactory {
    public static final String IMAGE_SOURCES = "image_sources_object";

    @Override
    public void onCreate(Bundle icicle){
        super.onCreate(icicle);
        setContentView(R.layout.image_flipper);
        mFlipper = (Gallery)findViewById(R.id.flipper);

        Intent intent = getIntent();
        Bundle data = intent.getExtras();
        mImageDetails = (ArrayList<PicturesDetails>)data.get(IMAGE_SOURCES);
        mGalleryViewAdapter = new GalleryViewAdapter(this);
        mGalleryViewAdapter.setImageSources(mImageDetails);
        mFlipper.setAdapter(mGalleryViewAdapter);
        //loadImages();
    }

    @Override
    public void onResume(){
        super.onResume();
    }

    @Override
    public void onPause(){
        super.onPause();
    }
    
    @Override
    public View makeView(){
        ImageView i = new ImageView(this);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return i;
    }

    private class GalleryViewAdapter extends BaseAdapter{
        private Context mContext;
        private ArrayList<PicturesDetails> mContent;

        public GalleryViewAdapter(Context c) {
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
                String loc = mImageDetails.get(position).standardResUri;
                imageView.setImageURI(Uri.parse(loc));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setLayoutParams(new Gallery.LayoutParams(Gallery.LayoutParams.MATCH_PARENT, Gallery.LayoutParams.MATCH_PARENT));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setPadding(8, 8, 8, 8);
            } else {
                imageView = (ImageView) convertView;
            }
            loadBitmap(mImageDetails.get(position).standardResUri, imageView);
            return imageView;
        }

        public void loadBitmap(String loc, ImageView imageView) {
            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            task.execute(loc);
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

    private Gallery mFlipper;
    private ArrayList<PicturesDetails> mImageDetails;
    private GalleryViewAdapter mGalleryViewAdapter;
}
