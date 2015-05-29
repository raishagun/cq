package com.pastcustoms.cq;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class ComposeMessageActivity extends ActionBarActivity
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    // Request code for picking address book contact
    static final int REQUEST_PICK_CONTACT = 1;
    // Request code for resolving Google API connection error
    static final int REQUEST_RESOLVE_CONNECTION_ERROR = 2;
    static final int DESIRED_LOCATION_UPDATE_INTERVAL = 5000; // In milliseconds
    static final int FASTEST_LOCATION_UPDATE_INTERVAL = 1000; // In milliseconds
    static final String TAG = "CqApp"; // Tag for writing to the log
    // Used to decide if "Copy URL to clipboard" button should be shown in menu.
    // Copying to clipboard is only supported in SDK level >= 11
    static final boolean DEVICE_SUPPORTS_COPY_URL = (Build.VERSION.SDK_INT >= 11);
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected LocationRequest mLocationRequest;
    protected EditText mRecipientPhoneNo;
    protected TextView mContactDisplayName;
    protected TextView mSmsMessage;
    protected ImageButton mPickContactButton;
    protected Button mSendMessageButton;
    protected Message mMessage = new Message();
    private boolean mCurrentlyResolvingError = false;
    // UI disabled when sending SMS doesn't make sense
    private boolean mUiDisabled = false;
    // Some (possibly out of date) location data is available to display
    private boolean mHaveLastLocation = false;
    private boolean mRequestingLocationUpdates = true;
    private SharedPreferences mSharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose_message);

        mPickContactButton = (ImageButton) findViewById(R.id.pick_contact_button);
        mSendMessageButton = (Button) findViewById(R.id.send_message_button);
        mContactDisplayName = (TextView) findViewById(R.id.contact_name);
        mSmsMessage = (TextView) findViewById(R.id.full_message);
        mRecipientPhoneNo = (EditText) findViewById(R.id.phone_no);

        mSharedPrefs = getSharedPreferences(
                getString(R.string.shared_prefs_file_key), Context.MODE_PRIVATE);

        buildGoogleApiClient();
    }

    /**
     * Builds Google API client to connect to location services (provided by Google Play Services).
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    /**
     * Creates a location request, specifying the accuracy and frequency of location data
     * to request from the fused location provider.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(DESIRED_LOCATION_UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_LOCATION_UPDATE_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Get the last known location, if available
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        // If last known location available, create location message and update UI
        if (mLastLocation != null) {
            mMessage.update(mLastLocation);
            mHaveLastLocation = true;
            updateUI();
        }
        // If requesting location updates (i.e. updates are not paused), start location updates
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    /**
     * Disables the 'Send SMS' button and sets a boolean (mUiDisabled) that results in
     * the 'Resume/Pause updates' options being hidden from the action bar options menu.
     */
    private void disableUi() {
        mUiDisabled = true;
        mSendMessageButton.setEnabled(false);
    }

    /**
     * Enables the 'Send SMS' button and sets a boolean that allows the 'Resume/Pause updates'
     * options to be shown in the action bar options menu.
     */
    private void enableUi() {
        mUiDisabled = false;
        mSendMessageButton.setEnabled(true);
    }

    /**
     * Requests location updates from the Fused Location Provider.
     */
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    /**
     * Cancels request for location updates from the Fused Location Provider.
     */
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    /**
     * Updates the UI by displaying the most recent message (which is held within a Message object).
     */
    protected void updateUI() {
        mSmsMessage.setText(mMessage.mMessageText);
    }

    @Override
    public void onLocationChanged(Location location) {
        mMessage.update(location);
        updateUI();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // If currently attempting to resolve an error, do nothing
        if (mCurrentlyResolvingError) {
            return;
        }

        // If there is an automatic resolution to this error, attempt it
        if (result.hasResolution()) {
            try {
                mCurrentlyResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_CONNECTION_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // If there is an error with this resolution attempt, simply try re-connecting
                mGoogleApiClient.connect();
            }
        } else {
            // If there is no automatic resolution to the error, display an error dialog
            // to the user (e.g., prompting update of Google Play Services)
            int errorCode = result.getErrorCode();
            GooglePlayServicesUtil.getErrorDialog(errorCode, this,
                    REQUEST_RESOLVE_CONNECTION_ERROR).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // If Google Api Client is connected, stop location updates.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }

        // Record in shared preferences that CQ is no longer foreground app
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(getString(R.string.prefs_is_foreground_app), false);
        editor.commit();

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();

        // Record in shared preferences that CQ is foreground app
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(getString(R.string.prefs_is_foreground_app), true);
        editor.commit();

        // Provisionally enable UI. The UI will be disabled if subsequent check of phone settings
        // reveals any problems (e.g., airplane mode is on)
        enableUi();

        // Check if user has disabled location services or turned on Flight Mode
        checkPhoneSettings(this);

        // If connected to Google API client and requesting updates (i.e. user has not selected
        // the 'pause location updates' option from menu), then start location updates.
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            Log.d(TAG, "mGoogleApiClient connected and requesting updates");
            startLocationUpdates();
        }

        // Watch phone number EditText for changes (i.e., if user manually enters a different
        // telephone number). If phone number is changed, then remove any Contact Display Name
        // that was obtained from the address book for the previous telephone number.
        TextWatcher textWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                mContactDisplayName.setText("");
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Don't need this method
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Don't need this method
            }
        };
        mRecipientPhoneNo.addTextChangedListener(textWatcher);
    }

    /**
     * Checks the phone's settings and displays appropriate error dialogs. Also disables the UI
     * if necessary. Specifically:
     * - Checks to see if location services are turned off, and if so, displays error dialog.
     * - Checks to see if Flight Mode is on, and if so, displays an error dialog and disables UI.
     *
     * @param context the current context.
     */
    private void checkPhoneSettings(Context context) {
        // Check if location services are enabled
        boolean locationEnabled = isLocationEnabled(this);
        if (!locationEnabled) {
            // If location services disabled, display alert dialog with option to go to settings
            displaySettingsDialog(
                    getString(R.string.location_disabled_alert_title),
                    getString(R.string.location_disabled_alert_message),
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);

            // If location services disabled, display "location unavailable"
            // if there is no previous location to show
            if (!mHaveLastLocation) {
                mSmsMessage.setText(getText(R.string.location_unavailable));
            }
        }

        // Check if Flight Mode is on
        boolean airplaneModeOn = isAirplaneModeOn(this);
        if (airplaneModeOn) {
            // If Flight Mode is on, display alert dialog with option to go to settings
            displaySettingsDialog(
                    getString(R.string.airplane_mode_alert_title),
                    getString(R.string.airplane_mode_alert_message),
                    Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        }

        boolean phoneSettingsOk
                = (locationEnabled && !airplaneModeOn && mGoogleApiClient.isConnected());
        // If phone settings are OK, then enable UI if it had previously
        // been disabled for some reason.
        if (phoneSettingsOk && mUiDisabled) {
            enableUi();
        }
    }


    /**
     * Displays an alert dialog with the following options:
     * - Cancel (dismisses the dialog and disables the UI)
     * - Go to Settings (goes to appropriate phone settings page)
     * @param title the alert dialog title
     * @param message the alert dialog message
     * @param settingsActivity the settings activity to start when user clicks 'Go to Settings'
     *                         e.g., Settings.ACTION_AIRPLANE_MODE_SETTINGS
     */
    private void displaySettingsDialog(String title, String message, String settingsActivity) {
        final String settings = settingsActivity;
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.button_go_to_settings),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int x) {
                        dialog.dismiss();
                        Intent settingsIntent = new Intent(settings);
                        startActivity(settingsIntent);
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.button_cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int x) {
                        disableUi();
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }


    /**
     * Helper function to check if user has enabled location access.
     *
     * @param context the current context.
     * @return true if location access is enabled, false if disabled.
     */
    private boolean isLocationEnabled(Context context) {
        // By default, assume that location services are enabled.
        boolean isLocationEnabled = true;

        // If device API level < 19, read system setting LOCATION_PROVIDERS_ALLOWED (deprecated).
        if (Build.VERSION.SDK_INT < 19) {
            String locationProviders = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            if (locationProviders == null || locationProviders.isEmpty()) {
                isLocationEnabled = false;
            }
        } else {
            // If device API level >= 19, read system setting LOCATION_MODE to find out whether
            // user has disabled location services.
            int locationMode;
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                locationMode = -1; // In this unusual situation, don't assume location unavailable.
            }
            if (locationMode == Settings.Secure.LOCATION_MODE_OFF) {
                isLocationEnabled = false;
            }
        }

        return isLocationEnabled;
    }

    /**
     * Helper function to determine if Flight Mode (aka Airplane Mode) is enabled.
     *
     * @param context the current context.
     * @return true if Flight Mode/ Airplane Mode is on.
     */
    private boolean isAirplaneModeOn(Context context) {
        boolean airplaneModeOn;

        if (Build.VERSION.SDK_INT < 17) {
            airplaneModeOn = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            airplaneModeOn = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
        return airplaneModeOn;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu for the compose message activity (i.e. the main activity in this app)
        getMenuInflater().inflate(R.menu.menu_compose_message, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem resumeUpdates = menu.findItem(R.id.menu_resume_location_updates);
        MenuItem pauseUpdates = menu.findItem(R.id.menu_pause_location_updates);
        MenuItem copyUrl = menu.findItem(R.id.menu_copy_url);

        if (mUiDisabled) {
            // Hide both 'pause updates' and 'resume updates' buttons if UI disabled
            resumeUpdates.setVisible(false);
            pauseUpdates.setVisible(false);
            copyUrl.setVisible(false);
        } else if (mRequestingLocationUpdates) {
            // Hide 'resume updates' button if already updating
            resumeUpdates.setVisible(false);
            pauseUpdates.setVisible(true);
            copyUrl.setVisible(true);
        } else {
            // Hide 'pause updates' button if already paused
            resumeUpdates.setVisible(true);
            pauseUpdates.setVisible(false);
            copyUrl.setVisible(true);
        }

        // If device doesn't support copying URL to clipboard, hide this menu option
        if (!DEVICE_SUPPORTS_COPY_URL){
            copyUrl.setVisible(false);
        }

        // Must return true for the menu to be displayed (see Android API docs)
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.menu_pause_location_updates:
                stopLocationUpdates();
                mRequestingLocationUpdates = false;
                Toast.makeText(this, "Location updates are now paused", Toast.LENGTH_LONG).show();
                return true;
            case R.id.menu_resume_location_updates:
                startLocationUpdates();
                mRequestingLocationUpdates = true;
                Toast.makeText(this, "Updating your location", Toast.LENGTH_LONG).show();
                return true;
            case R.id.menu_about:
                simpleAlertDialog("About CQ", "Version 1.0\nDeveloped by Scott Bassett, 2015\ncq.pastcustoms.com", "OK");
                return true;
            case R.id.menu_copy_url:
                copyUrl();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    /**
     * Creates and shows a basic alert dialog based on the provided input.
     *
     * @param title      the title for the alert dialog
     * @param message    the alert dialog's message
     * @param buttonText the text for the single button that dismisses the dialog
     */
    private void simpleAlertDialog(String title, String message, String buttonText) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, buttonText,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int x) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }


    /**
     * Copies location URL to clipboard.
     */
    @TargetApi(11)
    public void copyUrl() {
        String mapUrl = mMessage.mMapUrl;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("My location", mapUrl);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    /**
     * Handler for choose contact button.
     */
    public void chooseContact(View view) {
        pickContact();
    }

    /**
     * Starts activity to choose contact telephone number from address book.
     */
    private void pickContact() {
        Intent pickContactIntent = new Intent(Intent.ACTION_PICK, Uri.parse("content://contacts"));
        // Only show contacts with phone numbers
        pickContactIntent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
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

    /**
     * Called when a response code is received from attempting to automatically resolve
     * Google API Client connection error.
     *
     * @param resultCode the result code indicating the status of the error resolution attempt
     * @param data       an intent containing additional data about the error resolution attempt
     */
    private void resolveConnectionError(int resultCode, Intent data) {
        mCurrentlyResolvingError = false;
        // If attempt to resolve error was successful, try again to connect
        // the client to Google Play Services.
        if (resultCode == RESULT_OK) {
            if (!mGoogleApiClient.isConnecting() && !mGoogleApiClient.isConnected()) {
                mGoogleApiClient.connect();
            }
        } else {
            // If still unable to resolve connection by this point, display dialog and disable UI.
            mSmsMessage.setText(getText(R.string.location_unavailable));
            disableUi();
            mSendMessageButton.setEnabled(false);
            simpleAlertDialog("Error connecting to Google Play Services", "For some reason, CQ cannot connect to Google Play Services. Unfortunately, this means CQ can't get your location!", "OK");
        }
    }


    /**
     * Gets and displays the phone number and 'display name' of a single chosen contact
     */
    private void getContactPhoneNo(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            // Get the URI that points to the selected contact
            Uri contactUri = data.getData();
            // We'll need the contact's phone number and display name
            String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.Contacts.DISPLAY_NAME};

            // Calling query on main thread (rather than via CursorLoader) because only getting
            // details for one contact.
            Cursor cursor = getContentResolver()
                    .query(contactUri, projection, null, null, null);
            cursor.moveToFirst();

            // Get contact's phone number and display name.
            int phoneColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            String number = cursor.getString(phoneColumn);

            int nameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            String name = cursor.getString(nameColumn);

            // Display contact's phone number
            mRecipientPhoneNo.setText(number);

            // Display contact's display name
            mContactDisplayName.setText(name);
        }
    }

    public void sendSmsBtnHandler(View view) {
        prepareLocationMessage();
    }

    /**
     * Prepares to send the location message. Validates the phone number and the location URL,
     * and passes the message to sendLocationMessage() if phone number and URL are OK.
     */
    private void prepareLocationMessage() {
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
            sendLocationMessage(phoneNumber, messageText);
            Toast.makeText(this, "Sending SMS...", Toast.LENGTH_SHORT).show();
        }
        return;
    }

    /**
     * Sends a location SMS to a phone number.
     *
     * @param phoneNumber the phone number of the SMS recipient
     * @param messageText the location message to be sent via SMS
     */
    private void sendLocationMessage(String phoneNumber, String messageText) {

        Log.d(TAG, "sending SMS");

        // Create a new message id
        int messageId = mSharedPrefs.getInt(getString(R.string.prefs_notification_id), 0);
        ++messageId; // new id is simply old id, incremented
        messageId = messageId % 100; // start re-using ids once messageId == 100.
        Log.d(TAG, "MessageId is: " + Integer.toString(messageId));

        // Save new message id in shared preferences
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putInt(getString(R.string.prefs_notification_id), messageId);
        editor.commit();

        // Create pending intents for the 'SmsStatusReceiver' broadcast receiver.
        // These will communicate whether the SMS was successfully sent and delivered.
        Intent smsSent = new Intent("com.pastcustoms.cq.SMS_SENT");
        Intent smsDelivery = new Intent("com.pastcustoms.cq.SMS_DELIVERED");

        // Report SMS status using contact name, if available (more readable than phone number)
        String contactName = mContactDisplayName.getText().toString();
        Log.d(TAG, "contact name is: " + contactName);

        if (contactName.equals("")) {
            smsSent.putExtra("PHONE_OR_NAME", phoneNumber);
            smsDelivery.putExtra("PHONE_OR_NAME", phoneNumber);
        } else {
            smsSent.putExtra("PHONE_OR_NAME", contactName);
            smsDelivery.putExtra("PHONE_OR_NAME", contactName);
        }

        smsSent.putExtra("MSG_ID", messageId);
        smsDelivery.putExtra("MSG_ID", messageId);

        PendingIntent smsSentIntent = PendingIntent.getBroadcast(
                this, messageId, smsSent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent smsDeliveryIntent = PendingIntent.getBroadcast(
                this, messageId, smsDelivery, PendingIntent.FLAG_UPDATE_CURRENT);

        // Send SMS
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, messageText,
                smsSentIntent, smsDeliveryIntent);
    }
}