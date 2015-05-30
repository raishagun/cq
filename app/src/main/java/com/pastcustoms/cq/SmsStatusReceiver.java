package com.pastcustoms.cq;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

public class SmsStatusReceiver extends BroadcastReceiver {

    private SharedPreferences mSharedPrefs;

    // Check if ComposeMessageActivity is running. If so, show error dialog; if not,
    // send notification.

    @Override
    public void onReceive(Context context, Intent intent){

        // Check if ComposeMessageActivity is running in foreground.
        // Do this by checking Shared Preferences.
        mSharedPrefs = context.getSharedPreferences(
                context.getString(R.string.shared_prefs_file_key), Context.MODE_PRIVATE);
        boolean cqIsForeground = mSharedPrefs.getBoolean(
                context.getString(R.string.prefs_is_foreground_app), false);

        String actionName = intent.getAction();

        Bundle extras = intent.getExtras();
        String phoneNumOrName = extras.getString("PHONE_OR_NAME");

        int messageId = intent.getIntExtra("MSG_ID", 0);
        String errorMessage;

        if (actionName.equals("com.pastcustoms.cq.SMS_SENT")){
            switch(getResultCode()) {
                case Activity.RESULT_OK:
                    // Only display "SMS sent" toast if CQ is in foreground.
                    if (cqIsForeground) {
                        Toast.makeText(context, context.getString(R.string.toast_sms_sent_to_)
                                + phoneNumOrName, Toast.LENGTH_LONG).show();
                    }

                    if (DevOptions.LOG) {
                        Log.d("CQ SMS Status Receiver",
                                "phoneNumOrName: " + phoneNumOrName);
                        Log.d("CQ SMS Status Receiver",
                                "messageId: " + Integer.toString(messageId));
                    }

                    // FOR DEBUGGING: display fake notification that SMS was not sent
                    if (DevOptions.DEBUG) {
                        String debugErrorMessage = context.getString(R.string.toast_sms_to_)
                                + phoneNumOrName
                                + context.getString(R.string.toast__was_not_sent);
                        errorNotification(context, debugErrorMessage, messageId);
                    }
                    break;
                default:
                    errorMessage = context.getString(R.string.toast_sms_to_)
                            + phoneNumOrName
                            + context.getString(R.string.toast__was_not_sent);
                    if (cqIsForeground) {
                        errorDialog(context, errorMessage);
                    } else {
                        errorNotification(context, errorMessage, messageId);
                    }
            }
        }

        if (actionName.equals("com.pastcustoms.cq.SMS_DELIVERED")) {
            switch(getResultCode()) {
                case Activity.RESULT_OK:
                    if (cqIsForeground) {
                        Toast.makeText(context, context.getString(R.string.toast_sms_delivered_to_)
                                + phoneNumOrName, Toast.LENGTH_LONG).show();
                    }
                    break;
                default:
                    errorMessage = context.getString(R.string.toast_sms_to_)
                            + phoneNumOrName
                            + context.getString(R.string.toast__was_not_delivered);
                    if (cqIsForeground) {
                        errorDialog(context, errorMessage);
                    } else {
                        errorNotification(context, errorMessage, messageId);
                    }
            }
        }
    }

    private void errorNotification(Context context, String errorMessage, int messageId) {
        // Create pending intent to launch CQ directly from the notification
        Intent startCqIntent = new Intent(context, ComposeMessageActivity.class);
        PendingIntent startCqPendingIntent = PendingIntent.getActivity(
                context,
                0, // no need for a real request code here, since we just want to start CQ
                startCqIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                );

        // Build notification based on provided error message
        NotificationCompat.Builder mNotificationBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.alert)
                .setContentTitle(context.getString(R.string.notification_location_sms_not_sent))
                .setContentText(errorMessage)
                .setContentIntent(startCqPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Send notification to user (can modify later using messageId)
        NotificationManager mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(messageId, mNotificationBuilder.build());
    }

    private void errorDialog(Context context, String message) {
        Intent smsErrorDialogIntent = new Intent(context, SmsErrorDialogActivity.class);
        smsErrorDialogIntent.putExtra("error_message", message);
        smsErrorDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(smsErrorDialogIntent);
    }
}
