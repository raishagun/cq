package com.pastcustoms.cq;

// TODO: add copyright statement
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class ComposeMessageActivity extends ActionBarActivity
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    static final int REQUEST_PICK_CONTACT = 1;  // The request code
    static final int REQUEST_RESOLVE_CONNECTION_ERROR = 2; // Request code
    private boolean mCurrentlyResolvingError = false;
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected LocationRequest mLocationRequest;
    protected boolean mRequestingLocationUpdates;
    protected TextView mRecipientPhoneNo;
    protected TextView mSmsMessage;
    protected Button mCopyUrlButton;
    protected Button mPickContactButton;
    protected Button mToggleUpdatesButton;
    protected Message mMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose_message);

        mPickContactButton = (Button) findViewById(R.id.pick_contact_button);
        mToggleUpdatesButton = (ToggleButton) findViewById(R.id.update_location_toggle);
        mRecipientPhoneNo = (TextView) findViewById(R.id.phone_no);
        mSmsMessage = (TextView) findViewById(R.id.full_message);

        // Create "Copy URL to clipboard" button if device supports this (SDK version 11 or higher)
        if (Build.VERSION.SDK_INT >= 11) {
            mCopyUrlButton = (Button) findViewById(R.id.copy_url_button);
        }

        mRequestingLocationUpdates = true;

        buildGoogleApiClient();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000); // update every 5 seconds
        mLocationRequest.setFastestInterval(500);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            mMessage = new Message(mLastLocation);
            updateUI();
        }
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    protected void updateUI() {
        mSmsMessage.setText(mMessage.mMessageText);
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        mMessage.update(location);
        updateUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_compose_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void toggleUpdatesHandler(View view) {
        boolean on = ((ToggleButton) view).isChecked();
        if (on) {
            startLocationUpdates();
            mRequestingLocationUpdates = true;
        } else {
            stopLocationUpdates();
            mRequestingLocationUpdates = false;
        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mCurrentlyResolvingError) {
            // currently attempting to resolve an error
            return;
        } else if (result.hasResolution()) {
            try {
                mCurrentlyResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_CONNECTION_ERROR);
            } catch (IntentSender.SendIntentException e) {
                mGoogleApiClient.connect();
            }
        } else {
            // TODO: implement proper error recovery
            Toast.makeText(this, "Error connecting to Google Location Services.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @TargetApi(11)
    public void copyUrl(View view) {
        String mapUrl = mMessage.mMapUrl;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("My location", mapUrl);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    public void chooseContact(View view) {
        pickContact();
    }

    // From tutorial
    private void pickContact() {
        Intent pickContactIntent = new Intent(Intent.ACTION_PICK, Uri.parse("content://contacts"));
        pickContactIntent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE); // Show user only contacts w/ phone numbers
        startActivityForResult(pickContactIntent, REQUEST_PICK_CONTACT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Switch to call appropriate function depending on the request code
        switch (requestCode) {
            case REQUEST_PICK_CONTACT:
                getContactPhoneNo(resultCode, data);
                break;
            case REQUEST_RESOLVE_CONNECTION_ERROR:
                resolveConnectionError(resultCode, data);
                break;
        }
    }

    private void resolveConnectionError(int resultCode, Intent data) {
        mCurrentlyResolvingError = false;
        if (resultCode == RESULT_OK) {
            if (!mGoogleApiClient.isConnecting() && !mGoogleApiClient.isConnected()) {
                mGoogleApiClient.connect();
            }
        }
    }

    // From tutorial
    private void getContactPhoneNo(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            // Get the URI that points to the selected contact
            Uri contactUri = data.getData();
            // We only need the NUMBER column, because there will be only one row in the result
            String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};

            // Perform the query on the contact to get the NUMBER column
            // We don't need a selection or sort order (there's only one result for the given URI)
            // CAUTION: The query() method should be called from a separate thread to avoid blocking
            // your app's UI thread. (For simplicity of the sample, this code doesn't do that.)
            // Consider using CursorLoader to perform the query.
            Cursor cursor = getContentResolver()
                    .query(contactUri, projection, null, null, null);
            cursor.moveToFirst();

            // Retrieve the phone number from the NUMBER column
            int column = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            String number = cursor.getString(column);

            // Do something with the phone number...

            // Display chosen number
            mRecipientPhoneNo.setText(number);
        }
    }

    public void sendLocationSMS(View view) {
        // Get phone number, Google Maps URL, and message to send via SMS
        String phoneNumber = mRecipientPhoneNo.getText().toString();
        String mapUrl = mMessage.mMapUrl;
        String messageText = mMessage.mMessageText;

        // Clean up phone number by removing dashes, spaces, and parentheses
        phoneNumber = phoneNumber.replace("-", "");
        phoneNumber = phoneNumber.replace(" ", "");
        phoneNumber = phoneNumber.replace("(", "");
        phoneNumber = phoneNumber.replace(")", "");

        // Perform basic validity check on URL and phone number before sending message
        boolean mapUrlInvalid = (mapUrl.isEmpty() || mapUrl.length() < 45);
        boolean phoneNumberInvalid = (phoneNumber.length() < 3
                || !phoneNumber.matches("[+]??[0-9]{3,15}"));

        if (mapUrlInvalid) {
            Toast.makeText(this, "Error: please update your location", Toast.LENGTH_LONG).show();
        } else if (phoneNumberInvalid) {
            Toast.makeText(this, "Error: please enter a valid phone number", Toast.LENGTH_LONG).show();
        } else {
            // Send message
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, messageText, null, null);
            Toast.makeText(this, "Message sent", Toast.LENGTH_LONG).show();
        }
        return;
    }

}
