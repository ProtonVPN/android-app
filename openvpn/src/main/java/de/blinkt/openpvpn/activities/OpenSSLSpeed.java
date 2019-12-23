/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.activities;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Vector;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.core.NativeUtils;
import de.blinkt.openpvpn.core.OpenVPNService;

public class OpenSSLSpeed extends Activity {

    private static SpeeedTest runTestAlgorithms;
    private EditText mCipher;
    private SpeedArrayAdapter mAdapter;
    private ListView mListView;


    static class SpeedArrayAdapter extends ArrayAdapter<SpeedResult> {

        private final Context mContext;
        private final LayoutInflater mInflater;

        public SpeedArrayAdapter(@NonNull Context context) {
            super(context, 0);
            mContext = context;
            mInflater = LayoutInflater.from(context);

        }

        class ViewHolder {
            TextView ciphername;
            TextView blocksize;
            TextView blocksInTime;
            TextView speed;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View view, @NonNull ViewGroup parent) {
            SpeedResult res = getItem(position);
            if (view == null) {
                view = mInflater.inflate(R.layout.speedviewitem, parent, false);
                ViewHolder holder = new ViewHolder();
                holder.ciphername = view.findViewById(R.id.ciphername);
                holder.speed = view.findViewById(R.id.speed);
                holder.blocksize = view.findViewById(R.id.blocksize);
                holder.blocksInTime = view.findViewById(R.id.blocksintime);
                view.setTag(holder);
            }

            ViewHolder holder = (ViewHolder) view.getTag();

            double total = res.count * res.length;
            String size = OpenVPNService.humanReadableByteCount((long) res.length, false, mContext.getResources());

            holder.blocksize.setText(size);
            holder.ciphername.setText(res.algorithm);

            if (res.failed) {
                holder.blocksInTime.setText(R.string.openssl_error);
                holder.speed.setText("-");
            } else if (res.running) {
                holder.blocksInTime.setText(R.string.running_test);
                holder.speed.setText("-");
            } else {
                String totalBytes = OpenVPNService.humanReadableByteCount((long) total, false, mContext.getResources());
                // TODO: Fix localisation here
                String blockPerSec = OpenVPNService.humanReadableByteCount((long) (total / res.time), false, mContext.getResources()) + "/s";
                holder.speed.setText(blockPerSec);
                holder.blocksInTime.setText(String.format(Locale.ENGLISH, "%d blocks (%s) in %2.1fs", (long) res.count, totalBytes, res.time));
            }

            return view;

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.openssl_speed);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.testSpecific).setOnClickListener((view) -> {
            runAlgorithms(mCipher.getText().toString());
        });
        mCipher = (EditText) findViewById(R.id.ciphername);

        mListView = findViewById(R.id.results);

        mAdapter = new SpeedArrayAdapter(this);
        mListView.setAdapter(mAdapter);

    }

    private void runAlgorithms(String algorithms) {
        if (runTestAlgorithms != null)
            runTestAlgorithms.cancel(true);
        runTestAlgorithms = new SpeeedTest();
        runTestAlgorithms.execute(algorithms.split(" "));
    }


    static class SpeedResult {
        String algorithm;
        boolean failed = false;

        double count;
        double time;
        int length;
        public boolean running=true;

        SpeedResult(String algorithm) {
            this.algorithm = algorithm;
        }
    }


    private class SpeeedTest extends AsyncTask<String, SpeedResult, SpeedResult[]> {


        private boolean mCancel = false;

        @Override
        protected SpeedResult[] doInBackground(String... strings) {
            Vector<SpeedResult> mResult = new Vector<>();

            for (String algorithm : strings) {

                // Skip 16b and 16k as they are not relevevant for VPN
                for (int i = 1; i < NativeUtils.openSSLlengths.length -1 && !mCancel; i++) {
                    SpeedResult result = new SpeedResult(algorithm);
                    result.length = NativeUtils.openSSLlengths[i];
                    mResult.add(result);
                    publishProgress(result);
                    double[] resi = NativeUtils.getOpenSSLSpeed(algorithm, i);
                    if (resi == null) {
                        result.failed = true;
                    } else {
                        result.count = resi[1];
                        result.time = resi[2];
                    }
                    result.running = false;
                    publishProgress(result);
                }
            }

            return mResult.toArray(new SpeedResult[mResult.size()]);

        }

        @Override
        protected void onProgressUpdate(SpeedResult... values) {
            for (SpeedResult r : values) {
                if (r.running)
                    mAdapter.add(r);
                mAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void onPostExecute(SpeedResult[] speedResult) {

        }

        @Override
        protected void onCancelled(SpeedResult[] speedResults) {
            mCancel = true;
        }
    }


}
