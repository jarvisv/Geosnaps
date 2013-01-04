package com.lateralthoughts.geosnaps;

import android.app.IntentService;
import android.content.Intent;
import android.os.ResultReceiver;
import android.os.Bundle;

import java.io.*;

//android utilities
import org.apache.http.message.BasicNameValuePair;
import android.util.Log;
import java.util.ArrayList;

//http related object imports
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import java.net.URLEncoder;

public class InstagramRestClient extends IntentService {
    //TODO:these need to be removed to a better location
    private final String INSTA_CLIENT_ID = "9a2b56c383694da0b0970e4c5d33539d";

    private final String INSTA_CLIENT_SECRET= "664e2e8768f84c9fbd1dfd0ecfb77e0b";

    public InstagramRestClient(){
        super("InstagramRestClient");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    protected void onHandleIntent(Intent intent){
        mParams = intent.getParcelableArrayListExtra("params");
        mHeaders = intent.getParcelableArrayListExtra("headers");
        mUrl = intent.getStringExtra("url");
        mReceiver = (ResultReceiver) intent.getParcelableExtra("receiver");

        try {
            go();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void go(){
        String consolidatedParams = "";
        if(!mParams.isEmpty()){
            consolidatedParams += "?";
            for(ParcelableNameValuePair p : mParams)
            {
                String paramString = null;
                try{
                    paramString = p.getName() + "=" + URLEncoder.encode(p.getValue(),"UTF-8");
                }   catch(UnsupportedEncodingException encode_ex){
                    encode_ex.printStackTrace();
                }
                if(consolidatedParams.length() > 1)
                {
                    consolidatedParams  +=  "&" + paramString;
                }
                else
                {
                    consolidatedParams += paramString;
                }
            }
        }

        mRequest = new HttpGet(mUrl + consolidatedParams);

        //add headers
        for(ParcelableNameValuePair h : mHeaders)
        {
            mRequest.addHeader(h.getName(), h.getValue());
        }
        executeRequest();
    }


    private void executeRequest(){
        HttpClient client = new DefaultHttpClient();

        HttpResponse httpResponse;

        try {
            httpResponse = client.execute(mRequest);
            int responseCode = httpResponse.getStatusLine().getStatusCode();
            String message = httpResponse.getStatusLine().getReasonPhrase();

            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {

                InputStream instream = entity.getContent();
                String response = convertStreamToString(instream);
                Bundle responseBundle = new Bundle();
                responseBundle.putString("result", response);
                mReceiver.send(1, responseBundle);
                instream.close();

            }

        } catch (ClientProtocolException e)  {
            client.getConnectionManager().shutdown();
            e.printStackTrace();
        } catch (IOException e) {
            client.getConnectionManager().shutdown();
            e.printStackTrace();
        }

    }
    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
    ArrayList <ParcelableNameValuePair> mParams;
    ArrayList <ParcelableNameValuePair> mHeaders;
    private String mUrl;
    private ResultReceiver mReceiver;

    //http related objects that we need for the service
    HttpRequestBase mRequest;

}
