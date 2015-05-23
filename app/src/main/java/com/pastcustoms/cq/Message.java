package com.pastcustoms.cq;

import android.location.Location;

import java.text.DateFormat;
import java.text.MessageFormat;

public class Message {

    private DateFormat mShortDateFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
    protected String mMapUrl;
    protected String mMessageText;
    protected float accuracy;
    protected long time;

    protected Message() { }

    protected void update(Location mostRecentLocation) {
        // Get accuracy
        accuracy = mostRecentLocation.getAccuracy();
        String accuracyRounded = String.valueOf(Math.round(accuracy));

        // Get 'last updated' time
        time = mostRecentLocation.getTime();
        String lastUpdated = mShortDateFormat.format(time);

        // Get latlong
        String latitude = String.valueOf(mostRecentLocation.getLatitude());
        String longitude = String.valueOf(mostRecentLocation.getLongitude());
        String latLong = latitude + "," + longitude;

        // Build Google Maps URL
        mMapUrl = "http://maps.google.com/maps?ll=" + latLong + "&q=" + latLong + "&z=15";

        // Build complete message
        mMessageText = MessageFormat.format(
                "I am within about {0} meters of here:\n{1}\n(Updated {2} my time)",
                accuracyRounded, mMapUrl, lastUpdated);
    }
}
