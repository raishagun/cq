package com.pastcustoms.cq;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

public class SmsErrorDialogActivity extends Activity {
    /**
     * Simply creates an error alertDialog, using the error message provided in the intent.
     * Works in tandem with SmsStatusReceiver, which cannot create its own alertDialogs and thus
     * must use this invisible activity to create them.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String message = intent.getStringExtra("error_message");

        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(getString(R.string.error));
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int x) {
                        dialog.dismiss();
                        finish();
                    }
                });
        alertDialog.show();
    }

}
