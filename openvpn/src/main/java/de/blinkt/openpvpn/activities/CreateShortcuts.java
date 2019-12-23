/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.activities;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.blinkt.openpvpn.LaunchVPN;
import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.core.ProfileManager;

import java.util.Collection;
import java.util.Vector;

/**
 * This Activity actually handles two stages of a launcher shortcut's life cycle.
 *
 * 1. Your application offers to provide shortcuts to the launcher.  When
 *    the user installs a shortcut, an activity within your application
 *    generates the actual shortcut and returns it to the launcher, where it
 *    is shown to the user as an icon.
 *
 * 2. Any time the user clicks on an installed shortcut, an intent is sent.
 *    Typically this would then be handled as necessary by an activity within
 *    your application.
 *
 * We handle stage 1 (creating a shortcut) by simply sending back the information (in the form
 * of an {@link android.content.Intent} that the launcher will use to create the shortcut.
 *
 * You can also implement this in an interactive way, by having your activity actually present
 * UI for the user to select the specific nature of the shortcut, such as a contact, picture, URL,
 * media item, or action.
 *
 * We handle stage 2 (responding to a shortcut) in this sample by simply displaying the contents
 * of the incoming {@link android.content.Intent}.
 *
 * In a real application, you would probably use the shortcut intent to display specific content
 * or start a particular operation.
 */
public class CreateShortcuts extends ListActivity implements OnItemClickListener {


    private static final int START_VPN_PROFILE= 70;


    private ProfileManager mPM;
    private VpnProfile mSelectedProfile;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPM =ProfileManager.getInstance(this);

    }

    @Override
    protected void onStart() {
        super.onStart();
        // Resolve the intent

            createListView();
    }

    private void createListView() {
        ListView lv = getListView();
        //lv.setTextFilterEnabled(true);

        Collection<VpnProfile> vpnList = mPM.getProfiles();

        Vector<String> vpnNames=new Vector<String>();
        for (VpnProfile vpnProfile : vpnList) {
            vpnNames.add(vpnProfile.mName);
        }



        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,vpnNames);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener(this);
    }

    /**
     * This function creates a shortcut and returns it to the caller.  There are actually two
     * intents that you will send back.
     *
     * The first intent serves as a container for the shortcut and is returned to the launcher by
     * setResult().  This intent must contain three fields:
     *
     * <ul>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_INTENT} The shortcut intent.</li>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_NAME} The text that will be displayed with
     * the shortcut.</li>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_ICON} The shortcut's icon, if provided as a
     * bitmap, <i>or</i> {@link android.content.Intent#EXTRA_SHORTCUT_ICON_RESOURCE} if provided as
     * a drawable resource.</li>
     * </ul>
     *
     * If you use a simple drawable resource, note that you must wrapper it using
     * {@link android.content.Intent.ShortcutIconResource}, as shown below.  This is required so
     * that the launcher can access resources that are stored in your application's .apk file.  If
     * you return a bitmap, such as a thumbnail, you can simply put the bitmap into the extras
     * bundle using {@link android.content.Intent#EXTRA_SHORTCUT_ICON}.
     *
     * The shortcut intent can be any intent that you wish the launcher to send, when the user
     * clicks on the shortcut.  Typically this will be {@link android.content.Intent#ACTION_VIEW}
     * with an appropriate Uri for your content, but any Intent will work here as long as it
     * triggers the desired action within your Activity.
     * @param profile
     */
    private void setupShortcut(VpnProfile profile) {
        // First, set up the shortcut intent.  For this example, we simply create an intent that
        // will bring us directly back to this activity.  A more typical implementation would use a
        // data Uri in order to display a more specific result, or a custom action in order to
        // launch a specific operation.

        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClass(this, LaunchVPN.class);
        shortcutIntent.putExtra(LaunchVPN.EXTRA_KEY,profile.getUUID().toString());

        // Then, set up the container intent (the response to the caller)

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, profile.getName());
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(
                this, R.mipmap.ic_launcher);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher

        setResult(RESULT_OK, intent);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        String profileName = ((TextView) view).getText().toString();

        VpnProfile profile = mPM.getProfileByName(profileName);

        setupShortcut(profile);
        finish();
    }

}
