package com.pastcustoms.cq;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class LegalInfoActivity extends ActionBarActivity {

    protected TextView legalInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal_info);

        legalInfo = (TextView) findViewById(R.id.legalText);
        openLicenceAgreement();
    }

    /**
     * Reads a license agreement from a text file and displays it.
     */
    private void openLicenceAgreement() {
        boolean readFailure = false;
        String line = "";
        InputStream is = getResources().openRawResource(R.raw.licence);
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        try {
            while ((line = in.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            // If file cannot be read, make a note of this. Will display simple legal info instead
            readFailure = true;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // Ignore this exception
            }
        }
        // Display legal statement if file was successfully read
        if (!readFailure) {
            legalInfo.setText(sb.toString());
        } else {
            // If there was an error reading the file, at least display this simple legal statement
            legalInfo.setText("CQ, (c) Scott Bassett, 2015. \n" +
                    "This software is licensed under the MIT license: \n" +
                    "http://www.opensource.org/licenses/mit-license.php");
        }
    }

}
