package com.daxslab.mail.activity;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.daxslab.mail.Account;
import com.daxslab.mail.K9;
import com.daxslab.mail.Preferences;
import com.daxslab.mail.R;
import com.daxslab.mail.service.NotificationActionService;

public class NotificationDeleteConfirmation extends Activity {
    private final static String EXTRA_ACCOUNT = "account";
    private final static String EXTRA_MESSAGE_LIST = "messages";

    private final static int DIALOG_CONFIRM = 1;

    private Account mAccount;
    private ArrayList<MessageReference> mMessageRefs;

    public static PendingIntent getIntent(Context context, final Account account, final ArrayList<MessageReference> refs) {
        Intent i = new Intent(context, NotificationDeleteConfirmation.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MESSAGE_LIST, refs);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(context, account.getAccountNumber(), i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setTheme(K9.getK9Theme() == K9.Theme.LIGHT ?
                R.style.Theme_K9_Dialog_Translucent_Light : R.style.Theme_K9_Dialog_Translucent_Dark);

        final Preferences preferences = Preferences.getPreferences(this);
        final Intent intent = getIntent();

        mAccount = preferences.getAccount(intent.getStringExtra(EXTRA_ACCOUNT));
        mMessageRefs = intent.getParcelableArrayListExtra(EXTRA_MESSAGE_LIST);

        if (mAccount == null || mMessageRefs == null || mMessageRefs.isEmpty()) {
            finish();
        } else if (!K9.confirmDeleteFromNotification()) {
            triggerDelete();
            finish();
        } else {
            showDialog(DIALOG_CONFIRM);
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_CONFIRM:
            return ConfirmationDialog.create(this, id,
                    R.string.dialog_confirm_delete_title, "",
                    R.string.dialog_confirm_delete_confirm_button,
                    R.string.dialog_confirm_delete_cancel_button,
                    new Runnable() {
                        @Override
                        public void run() {
                            triggerDelete();
                            finish();
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
        }

        return super.onCreateDialog(id);
    }

    @Override
    public void onPrepareDialog(int id, Dialog d) {
        AlertDialog alert = (AlertDialog) d;
        switch (id) {
        case DIALOG_CONFIRM:
            int messageCount = mMessageRefs.size();
            alert.setMessage(getResources().getQuantityString(
                    R.plurals.dialog_confirm_delete_messages, messageCount, messageCount));
            break;
        }

        super.onPrepareDialog(id, d);
    }

    private void triggerDelete() {
        Intent i = NotificationActionService.getDeleteAllMessagesIntent(this, mAccount, mMessageRefs);
        startService(i);
    }
}
