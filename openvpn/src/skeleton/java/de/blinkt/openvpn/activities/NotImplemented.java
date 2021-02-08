package de.blinkt.openvpn.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class NotImplemented extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, "Not implemented", Toast.LENGTH_LONG).show();
        finish();
    }
}