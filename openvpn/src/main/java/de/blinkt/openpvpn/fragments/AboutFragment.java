/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.Fragment;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;

import de.blinkt.openpvpn.core.NativeUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.core.VpnStatus;

public class AboutFragment extends Fragment implements View.OnClickListener {

    public static final String INAPPITEM_TYPE_INAPP = "inapp";
    public static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final int DONATION_CODE = 12;
    private static final int BILLING_RESPONSE_RESULT_OK = 0;
    private static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    private static final String[] donationSkus = { "donation1eur", "donation2eur", "donation5eur", "donation10eur",
            "donation1337eur","donation23eur","donation25eur",};
    IInAppBillingService mService;
    Hashtable<View, String> viewToProduct = new Hashtable<>();
    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            initGooglePlayDonation();

        }
    };

    private void initGooglePlayDonation() {
        new Thread("queryGMSInApp") {
            @Override
            public void run() {
                initGMSDonateOptions();
            }
        }.start();
    }

    private TextView gmsTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
        getActivity().bindService(new
                Intent("com.android.vending.billing.InAppBillingService.BIND"),
                mServiceConn, Context.BIND_AUTO_CREATE);
        */
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            getActivity().unbindService(mServiceConn);
        }

    }

    private void initGMSDonateOptions() {
        try {
            int billingSupported = mService.isBillingSupported(3, getActivity().getPackageName(), INAPPITEM_TYPE_INAPP);
            if (billingSupported != BILLING_RESPONSE_RESULT_OK) {
                Log.i("OpenVPN", "Play store billing not supported");
                return;
            }

            ArrayList skuList = new ArrayList();
            Collections.addAll(skuList, donationSkus);
            Bundle querySkus = new Bundle();
            querySkus.putStringArrayList("ITEM_ID_LIST", skuList);

            Bundle ownedItems = mService.getPurchases(3, getActivity().getPackageName(), INAPPITEM_TYPE_INAPP, null);


            if (ownedItems.getInt(RESPONSE_CODE) != BILLING_RESPONSE_RESULT_OK)
                return;

            final ArrayList<String> ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");

            Bundle skuDetails = mService.getSkuDetails(3, getActivity().getPackageName(), INAPPITEM_TYPE_INAPP, querySkus);


            if (skuDetails.getInt(RESPONSE_CODE) != BILLING_RESPONSE_RESULT_OK)
                return;

            final ArrayList<String> responseList = skuDetails.getStringArrayList("DETAILS_LIST");

            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        createPlayBuyOptions(ownedSkus, responseList);

                    }
                });
            }

        } catch (RemoteException e) {
            VpnStatus.logException(e);
        }
    }

    private static class SkuResponse {
        String title;
        String price;

        SkuResponse(String p, String t)
        {
            title=t;
            price=p;
        }
    }



    private void createPlayBuyOptions(ArrayList<String> ownedSkus, ArrayList<String> responseList) {
        try {
            Vector<Pair<String,String>> gdonation = new Vector<Pair<String, String>>();

            gdonation.add(new Pair<String, String>(getString(R.string.donatePlayStore),null));
            HashMap<String, SkuResponse> responseMap = new HashMap<String, SkuResponse>();
            for (String thisResponse : responseList) {
                JSONObject object = new JSONObject(thisResponse);
                responseMap.put(
                        object.getString("productId"),
                        new SkuResponse(
                                object.getString("price"),
                                object.getString("title")));

            }
            for (String sku: donationSkus)
                if (responseMap.containsKey(sku))
                    gdonation.add(getSkuTitle(sku,
                            responseMap.get(sku).title, responseMap.get(sku).price, ownedSkus));

            String gmsTextString="";
            for(int i=0;i<gdonation.size();i++) {
                if(i==1)
                    gmsTextString+= "  ";
                else if(i>1)
                    gmsTextString+= ", ";
                gmsTextString+=gdonation.elementAt(i).first;
            }
            SpannableString gmsText = new SpannableString(gmsTextString);


            int lStart = 0;
            int lEnd=0;
            for(Pair<String, String> item:gdonation){
                lEnd = lStart + item.first.length();
                if (item.second!=null) {
                    final String mSku = item.second;
                    ClickableSpan cspan = new ClickableSpan()
                    {
                        @Override
                        public void onClick(View widget) {
                            triggerBuy(mSku);
                        }
                    };
                    gmsText.setSpan(cspan,lStart,lEnd,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                lStart = lEnd+2; // Account for ", " between items
            }

            if(gmsTextView !=null) {
                gmsTextView.setText(gmsText);
                gmsTextView.setMovementMethod(LinkMovementMethod.getInstance());
                gmsTextView.setVisibility(View.VISIBLE);
            }

        } catch (JSONException e) {
            VpnStatus.logException("Parsing Play Store IAP",e);
        }

    }

    private Pair<String,String> getSkuTitle(final String sku, String title, String price, ArrayList<String> ownedSkus) {
        String text;
        if (ownedSkus.contains(sku))
            return new Pair<String,String>(getString(R.string.thanks_for_donation, price),null);

        if (price.contains("€")|| price.contains("\u20ac")) {
            text= title;
        } else {
            text = String.format(Locale.getDefault(), "%s (%s)", title, price);
        }
        //return text;
        return new Pair<String,String>(price, sku);

    }

    private void triggerBuy(String sku) {
        try {
            Bundle buyBundle
                    = mService.getBuyIntent(3, getActivity().getPackageName(),
                    sku, INAPPITEM_TYPE_INAPP, "Thanks for the donation! :)");


            if (buyBundle.getInt(RESPONSE_CODE) == BILLING_RESPONSE_RESULT_OK) {
                PendingIntent buyIntent = buyBundle.getParcelable(RESPONSE_BUY_INTENT);
                getActivity().startIntentSenderForResult(buyIntent.getIntentSender(), DONATION_CODE, new Intent(),
                        0, 0, 0);
            }

        } catch (RemoteException e) {
            VpnStatus.logException(e);
        } catch (IntentSender.SendIntentException e) {
            VpnStatus.logException(e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.about, container, false);
        TextView ver = (TextView) v.findViewById(R.id.version);

        String version;
        String name = "Openvpn";
        try {
            PackageInfo packageinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            version = packageinfo.versionName;
            name = getString(R.string.app);
        } catch (NameNotFoundException e) {
            version = "error fetching version";
        }

        ver.setText(getString(R.string.version_info, name, version));

        TextView verO2 = v.findViewById(R.id.version_ovpn2);
        TextView verO3 = v.findViewById(R.id.version_ovpn3);

        verO2.setText(String.format(Locale.US, "OpenVPN version: %s", NativeUtils.getOpenVPN2GitVersion()));
        verO3.setText(String.format(Locale.US, "OpenVPN3 core version: %s", NativeUtils.getOpenVPN3GitVersion()));

        gmsTextView = (TextView) v.findViewById(R.id.donategms);
        /* recreating view without onCreate/onDestroy cycle */

        // Disable GMS for now
        if (mService!=null)
            initGooglePlayDonation();

        TextView translation = (TextView) v.findViewById(R.id.translation);

        // Don't print a text for myself
        if (getString(R.string.translationby).contains("Arne Schwabe"))
            translation.setText("");
        else
            translation.setText(R.string.translationby);

        TextView wv = (TextView) v.findViewById(R.id.full_licenses);
        wv.setText(Html.fromHtml(readHtmlFromAssets()));
        return v;
    }

    String readHtmlFromAssets()
    {
        InputStream mvpn;

        try {
            mvpn = getActivity().getAssets().open("full_licenses.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(mvpn));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException errabi) {
            return "full_licenses.html not found";
        }
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mService!=null)
            initGooglePlayDonation();
    }


    @Override
    public void onClick(View v) {

    }
}
