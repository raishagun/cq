package com.pastcustoms.cq;

import android.location.Location;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.MessageFormat;

public class Message implements Serializable {

    private DateFormat mShortDateFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
    protected String mMapUrl;
    protected String mMessageText;
    protected float mAccuracy;
    protected long mTime;
    protected boolean mPreferMetric = true;

    protected Message() { }

    /**
     * Toggle whether the accuracy is expressed in meters or feet
     * @param preferMetric whether the user prefers meters.
     */
    protected void preferMetric(boolean preferMetric){
        if (preferMetric){
            mPreferMetric = true;
        } else {
            mPreferMetric = false;
        }
    }

    protected void update(Location mostRecentLocation) {
        // Get accuracy
        mAccuracy = mostRecentLocation.getAccuracy();
        String accuracyRounded = String.valueOf(Math.round(mAccuracy));

        // Get 'last updated' time
        mTime = mostRecentLocation.getTime();
        String lastUpdated = mShortDateFormat.format(mTime);

        // Get latlong
        String latitude = String.valueOf(mostRecentLocation.getLatitude());
        String longitude = String.valueOf(mostRecentLocation.getLongitude());
        String latLong = latitude + "," + longitude;

        // Get appropriate zoom level
        String zoomLevel = calculateZoomLevel(mAccuracy);

        // Build Google Maps URL
        mMapUrl = "http://maps.google.com/maps?q=" + latLong + "&z=" + zoomLevel;

        // Check user units preference. If user prefers feet, convert accuracy to feet.
        String units;
        if (mPreferMetric){
            units = "meters";
        } else {
            units = "feet";
            float feet = mAccuracy/0.3048f;
            accuracyRounded = String.valueOf(Math.round(feet));
        }

        // Build complete message
        mMessageText = MessageFormat.format(
                "I am within roughly {0} {1} of here:\n{2}\n(Updated {3} my time)",
                accuracyRounded, units, mMapUrl, lastUpdated);
    }


    /**
     * Returns a suitable map zoom level given the location's mAccuracy.
     * @param accuracy the location's accuracy, in meters
     * @return the zoom level to use in the Google Maps url
     */
    private String calculateZoomLevel(float accuracy) {
        String zoomLevel;
        if (accuracy <= 10){
            zoomLevel = "17";
        } else if (accuracy <= 20){
            zoomLevel = "16";
        } else if (accuracy <= 40){
            zoomLevel = "15";
        } else if (accuracy <= 80){
            zoomLevel = "14";
        } else if (accuracy <= 160){
            zoomLevel = "13";
        } else if (accuracy <= 320){
            zoomLevel = "12";
        } else if (accuracy <= 640){
            zoomLevel = "11";
        } else if (accuracy <= 1280){
            zoomLevel = "10";
        } else if (accuracy <= 2560){
            zoomLevel = "9";
        } else if (accuracy <= 5120){
            zoomLevel = "8";
        } else if (accuracy <= 10240){
            zoomLevel = "7";
        } else if (accuracy <= 20480){
            zoomLevel = "6";
        } else if (accuracy <= 40960){
            zoomLevel = "5";
        } else if (accuracy <= 81920){
            zoomLevel = "4";
        } else if (accuracy <= 163840){
            zoomLevel = "3";
        } else if (accuracy <= 327680){
            zoomLevel = "2";
        } else {
            zoomLevel = "1";
        }
        return zoomLevel;
    }
}
