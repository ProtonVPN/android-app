/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.content.Context;
import android.os.AsyncTask;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import de.blinkt.openpvpn.R;

public class FaqViewAdapter extends RecyclerView.Adapter<FaqViewAdapter.FaqViewHolder> {
    private final FaqFragment.FAQEntry[] mFaqItems;
    private final Spanned[] mHtmlEntries;
    private final Spanned[] mHtmlEntriesTitle;
    private final Context mContext;
    private boolean loaded =false;

    public FaqViewAdapter(Context context, FaqFragment.FAQEntry[] faqItems) {
        mFaqItems = faqItems;
        mContext = context;

        mHtmlEntries = new Spanned[faqItems.length];
        mHtmlEntriesTitle = new Spanned[faqItems.length];

        new FetchStrings().execute(faqItems);

    }

    private class FetchStrings extends AsyncTask<FaqFragment.FAQEntry,Void,Void> {

        @Override
        protected void onPostExecute(Void aVoid) {
            loaded=true;
            notifyDataSetChanged();
        }

        @Override
        protected Void doInBackground(FaqFragment.FAQEntry... params) {
            fetchStrings(params);
            return null;
        }
    }

    private void fetchStrings(FaqFragment.FAQEntry[] faqItems) {
        for (int i =0; i < faqItems.length; i++) {
            String versionText = mFaqItems[i].getVersionsString(mContext);
            String title;
            String textColor="";

            if (mFaqItems[i].title==-1)
                title ="";
            else
                title = mContext.getString(faqItems[i].title);


            if (!mFaqItems[i].runningVersion())
                textColor= "<font color=\"gray\">";

            if (versionText != null) {

                mHtmlEntriesTitle[i] = (Spanned) TextUtils.concat(Html.fromHtml(textColor + title),
                        Html.fromHtml(textColor + "<br><small>" + versionText + "</small>"));
            } else {
                mHtmlEntriesTitle[i] = Html.fromHtml(title);
            }

            String content = mContext.getString(faqItems[i].description);
            mHtmlEntries[i] = Html.fromHtml(textColor + content);

            // Add hack R.string.faq_system_dialogs_title -> R.string.faq_system_dialog_xposed
            if (faqItems[i].title == R.string.faq_system_dialogs_title)
            {
                Spanned xPosedtext = Html.fromHtml(textColor + mContext.getString(R.string.faq_system_dialog_xposed));
                mHtmlEntries[i] = (Spanned) TextUtils.concat(mHtmlEntries[i], xPosedtext);
            }
        }
    }

    public static class FaqViewHolder extends RecyclerView.ViewHolder {

        private final CardView mView;
        private final TextView mBody;
        private final TextView mHead;

        public FaqViewHolder(View itemView) {
            super(itemView);

            mView = (CardView) itemView;
            mBody = (TextView)mView.findViewById(R.id.faq_body);
            mHead = (TextView)mView.findViewById(R.id.faq_head);
            mBody.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    public FaqViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.faqcard, viewGroup, false);
        return new FaqViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FaqViewHolder faqViewHolder, int i) {

        faqViewHolder.mHead.setText(mHtmlEntriesTitle[i]);
        faqViewHolder.mBody.setText(mHtmlEntries[i]);


    }

    @Override
    public int getItemCount() {
        if(loaded)
            return mFaqItems.length;
        else
            return 0;
    }


}
