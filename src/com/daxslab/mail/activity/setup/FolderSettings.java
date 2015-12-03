
package com.daxslab.mail.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.Log;
import com.daxslab.mail.*;
import com.daxslab.mail.activity.FolderInfoHolder;
import com.daxslab.mail.activity.K9PreferenceActivity;
import com.daxslab.mail.mail.Folder;
import com.daxslab.mail.mail.Folder.FolderClass;

import com.daxslab.mail.mail.MessagingException;
import com.daxslab.mail.mail.Store;
import com.daxslab.mail.mail.store.LocalStore;
import com.daxslab.mail.mail.store.LocalStore.LocalFolder;
import com.daxslab.mail.service.MailService;

public class FolderSettings extends K9PreferenceActivity {

    private static final String EXTRA_FOLDER_NAME = "com.daxslab.mail.folderName";
    private static final String EXTRA_ACCOUNT = "com.daxslab.mail.account";

    private static final String PREFERENCE_TOP_CATERGORY = "folder_settings";
    private static final String PREFERENCE_DISPLAY_CLASS = "folder_settings_folder_display_mode";
    private static final String PREFERENCE_SYNC_CLASS = "folder_settings_folder_sync_mode";
    private static final String PREFERENCE_PUSH_CLASS = "folder_settings_folder_push_mode";
    private static final String PREFERENCE_NOTIFY_CLASS = "folder_settings_folder_notify_mode";
    private static final String PREFERENCE_IN_TOP_GROUP = "folder_settings_in_top_group";
    private static final String PREFERENCE_INTEGRATE = "folder_settings_include_in_integrated_inbox";

    private LocalFolder mFolder;

    private CheckBoxPreference mInTopGroup;
    private CheckBoxPreference mIntegrate;
    private ListPreference mDisplayClass;
    private ListPreference mSyncClass;
    private ListPreference mPushClass;
    private ListPreference mNotifyClass;

    public static void actionSettings(Context context, Account account, String folderName) {
        Intent i = new Intent(context, FolderSettings.class);
        i.putExtra(EXTRA_FOLDER_NAME, folderName);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        context.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String folderName = (String)getIntent().getSerializableExtra(EXTRA_FOLDER_NAME);
        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        Account mAccount = Preferences.getPreferences(this).getAccount(accountUuid);

        try {
            LocalStore localStore = mAccount.getLocalStore();
            mFolder = localStore.getFolder(folderName);
            mFolder.open(Folder.OPEN_MODE_RW);
        } catch (MessagingException me) {
            Log.e(K9.LOG_TAG, "Unable to edit folder " + folderName + " preferences", me);
            return;
        }

        boolean isPushCapable = false;
        Store store = null;
        try {
            store = mAccount.getRemoteStore();
            isPushCapable = store.isPushCapable();
        } catch (Exception e) {
            Log.e(K9.LOG_TAG, "Could not get remote store", e);
        }

        addPreferencesFromResource(R.xml.folder_settings_preferences);

        String displayName = FolderInfoHolder.getDisplayName(this, mAccount, mFolder.getName());
        Preference category = findPreference(PREFERENCE_TOP_CATERGORY);
        category.setTitle(displayName);


        mInTopGroup = (CheckBoxPreference)findPreference(PREFERENCE_IN_TOP_GROUP);
        mInTopGroup.setChecked(mFolder.isInTopGroup());
        mIntegrate = (CheckBoxPreference)findPreference(PREFERENCE_INTEGRATE);
        mIntegrate.setChecked(mFolder.isIntegrate());

        mDisplayClass = (ListPreference) findPreference(PREFERENCE_DISPLAY_CLASS);
        mDisplayClass.setValue(mFolder.getDisplayClass().name());
        mDisplayClass.setSummary(mDisplayClass.getEntry());
        mDisplayClass.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mDisplayClass.findIndexOfValue(summary);
                mDisplayClass.setSummary(mDisplayClass.getEntries()[index]);
                mDisplayClass.setValue(summary);
                return false;
            }
        });

        mSyncClass = (ListPreference) findPreference(PREFERENCE_SYNC_CLASS);
        mSyncClass.setValue(mFolder.getRawSyncClass().name());
        mSyncClass.setSummary(mSyncClass.getEntry());
        mSyncClass.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mSyncClass.findIndexOfValue(summary);
                mSyncClass.setSummary(mSyncClass.getEntries()[index]);
                mSyncClass.setValue(summary);
                return false;
            }
        });

        mPushClass = (ListPreference) findPreference(PREFERENCE_PUSH_CLASS);
        mPushClass.setEnabled(isPushCapable);
        mPushClass.setValue(mFolder.getRawPushClass().name());
        mPushClass.setSummary(mPushClass.getEntry());
        mPushClass.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mPushClass.findIndexOfValue(summary);
                mPushClass.setSummary(mPushClass.getEntries()[index]);
                mPushClass.setValue(summary);
                return false;
            }
        });

        mNotifyClass = (ListPreference) findPreference(PREFERENCE_NOTIFY_CLASS);
        mNotifyClass.setValue(mFolder.getRawNotifyClass().name());
        mNotifyClass.setSummary(mNotifyClass.getEntry());
        mNotifyClass.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mNotifyClass.findIndexOfValue(summary);
                mNotifyClass.setSummary(mNotifyClass.getEntries()[index]);
                mNotifyClass.setValue(summary);
                return false;
            }
        });
    }

    private void saveSettings() throws MessagingException {
        mFolder.setInTopGroup(mInTopGroup.isChecked());
        mFolder.setIntegrate(mIntegrate.isChecked());
        // We call getPushClass() because display class changes can affect push class when push class is set to inherit
        FolderClass oldPushClass = mFolder.getPushClass();
        FolderClass oldDisplayClass = mFolder.getDisplayClass();
        mFolder.setDisplayClass(FolderClass.valueOf(mDisplayClass.getValue()));
        mFolder.setSyncClass(FolderClass.valueOf(mSyncClass.getValue()));
        mFolder.setPushClass(FolderClass.valueOf(mPushClass.getValue()));
        mFolder.setNotifyClass(FolderClass.valueOf(mNotifyClass.getValue()));

        mFolder.save();

        FolderClass newPushClass = mFolder.getPushClass();
        FolderClass newDisplayClass = mFolder.getDisplayClass();

        if (oldPushClass != newPushClass
                || (newPushClass != FolderClass.NO_CLASS && oldDisplayClass != newDisplayClass)) {
            MailService.actionRestartPushers(getApplication(), null);
        }
    }

    @Override
    public void onPause() {
        try {
            saveSettings();
        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "Saving folder settings failed", e);
        }

        super.onPause();
    }
}
