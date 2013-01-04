package com.lateralthoughts.geosnaps;

import android.os.Parcel;
import android.os.Parcelable;

public class PicturesDetails implements Parcelable {
    String name;
    String id;
    String thumbUri;
    String standardResUri;

    public PicturesDetails(){}

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(name);
        out.writeString(id);
        out.writeString(thumbUri);
        out.writeString(standardResUri);
    }

    public static final Parcelable.Creator<PicturesDetails> CREATOR
            = new Parcelable.Creator<PicturesDetails>() {
        public PicturesDetails createFromParcel(Parcel in) {
            return new PicturesDetails(in);
        }

        public PicturesDetails[] newArray(int size) {
            return new PicturesDetails[size];
        }
    };
    private PicturesDetails(Parcel in) {
        name = in.readString();
        id = in.readString();
        thumbUri = in.readString();
        standardResUri = in.readString();
    }
}