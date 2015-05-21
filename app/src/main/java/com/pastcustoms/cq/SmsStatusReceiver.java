package com.pastcustoms.cq;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.telephony.SmsManager;
import android.widget.Toast;

public class SmsStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent){

        String actionName = intent.getAction();
        String errorDialogMessage;
        if (actionName.equals("com.pastcustoms.cq.SMS_SENT")){
            switch(getResultCode()) {
                case Activity.RESULT_OK:
                    Toast.makeText(context, "SMS sent", Toast.LENGTH_LONG).show();
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    errorDialogMessage = "SMS was not sent! Please make sure that your phone is not in flight mode and try again.";
                    errorDialog(context.getApplicationContext(), errorDialogMessage);
                    break;
                default:
                    errorDialogMessage = "SMS was not sent! Please try again.";
                    errorDialog(context, errorDialogMessage);
            }
        }

        if (actionName.equals("com.pastcustoms.cq.SMS_DELIVERED")) {
            switch(getResultCode()) {
                case Activity.RESULT_OK:
                    Toast.makeText(context, "SMS delivered", Toast.LENGTH_LONG).show();
                    break;
                default:
                    errorDialogMessage = "SMS was not successfully delivered! Please try again.";
                    errorDialog(context, errorDialogMessage);
            }
        }
    }

    private void errorDialog(Context context, String message) {
        Intent smsErrorDialogIntent = new Intent(context, SmsErrorDialogActivity.class);
        smsErrorDialogIntent.putExtra("error_message", message);
        smsErrorDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(smsErrorDialogIntent);
    }
}
