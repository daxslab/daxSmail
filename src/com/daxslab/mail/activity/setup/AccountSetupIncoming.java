
package com.daxslab.mail.activity.setup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.daxslab.mail.*;
import com.daxslab.mail.Account.FolderMode;
import com.daxslab.mail.activity.K9Activity;
import com.daxslab.mail.activity.setup.AccountSetupCheckSettings.CheckDirection;
import com.daxslab.mail.helper.Utility;
import com.daxslab.mail.mail.AuthType;
import com.daxslab.mail.mail.ConnectionSecurity;
import com.daxslab.mail.mail.ServerSettings;
import com.daxslab.mail.mail.Store;
import com.daxslab.mail.mail.Transport;
import com.daxslab.mail.mail.store.ImapStore;
import com.daxslab.mail.mail.store.Pop3Store;
import com.daxslab.mail.mail.store.WebDavStore;
import com.daxslab.mail.mail.store.ImapStore.ImapStoreSettings;
import com.daxslab.mail.mail.store.WebDavStore.WebDavStoreSettings;
import com.daxslab.mail.mail.transport.SmtpTransport;
import com.daxslab.mail.service.MailService;
import com.daxslab.mail.view.ClientCertificateSpinner;
import com.daxslab.mail.view.ClientCertificateSpinner.OnClientCertificateChangedListener;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class AccountSetupIncoming extends K9Activity implements OnClickListener {
    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_MAKE_DEFAULT = "makeDefault";
    private static final String STATE_SECURITY_TYPE_POSITION = "stateSecurityTypePosition";
    private static final String STATE_AUTH_TYPE_POSITION = "authTypePosition";

    private static final String POP3_PORT = "110";
    private static final String POP3_SSL_PORT = "995";
    private static final String IMAP_PORT = "143";
    private static final String IMAP_SSL_PORT = "993";
    private static final String WEBDAV_PORT = "80";
    private static final String WEBDAV_SSL_PORT = "443";

    private String mStoreType;
    private EditText mUsernameView;
    private EditText mPasswordView;
    private ClientCertificateSpinner mClientCertificateSpinner;
    private TextView mClientCertificateLabelView;
    private TextView mPasswordLabelView;
    private EditText mServerView;
    private EditText mPortView;
    private String mCurrentPortViewSetting;
    private Spinner mSecurityTypeView;
    private int mCurrentSecurityTypeViewPosition;
    private Spinner mAuthTypeView;
    private int mCurrentAuthTypeViewPosition;
    private CheckBox mImapAutoDetectNamespaceView;
    private EditText mImapPathPrefixView;
    private EditText mWebdavPathPrefixView;
    private EditText mWebdavAuthPathView;
    private EditText mWebdavMailboxPathView;
    private Button mNextButton;
    private Account mAccount;
    private boolean mMakeDefault;
    private CheckBox mCompressionMobile;
    private CheckBox mCompressionWifi;
    private CheckBox mCompressionOther;
    private CheckBox mSubscribedFoldersOnly;
    private ArrayAdapter<AuthType> mAuthTypeAdapter;
    private String mDefaultPort = "";
    private String mDefaultSslPort = "";
    private ConnectionSecurity[] mConnectionSecurityChoices = ConnectionSecurity.values();

    public static void actionIncomingSettings(Activity context, Account account, boolean makeDefault) {
        Intent i = new Intent(context, AccountSetupIncoming.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MAKE_DEFAULT, makeDefault);
        context.startActivity(i);
    }

    public static void actionEditIncomingSettings(Activity context, Account account) {
        context.startActivity(intentActionEditIncomingSettings(context, account));
    }

    public static Intent intentActionEditIncomingSettings(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupIncoming.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_incoming);

        mUsernameView = (EditText)findViewById(R.id.account_username);
        mPasswordView = (EditText)findViewById(R.id.account_password);
        mClientCertificateSpinner = (ClientCertificateSpinner)findViewById(R.id.account_client_certificate_spinner);
        mClientCertificateLabelView = (TextView)findViewById(R.id.account_client_certificate_label);
        mPasswordLabelView = (TextView)findViewById(R.id.account_password_label);
        TextView serverLabelView = (TextView) findViewById(R.id.account_server_label);
        mServerView = (EditText)findViewById(R.id.account_server);
        mPortView = (EditText)findViewById(R.id.account_port);
        mSecurityTypeView = (Spinner)findViewById(R.id.account_security_type);
        mAuthTypeView = (Spinner)findViewById(R.id.account_auth_type);
        mImapAutoDetectNamespaceView = (CheckBox)findViewById(R.id.imap_autodetect_namespace);
        mImapPathPrefixView = (EditText)findViewById(R.id.imap_path_prefix);
        mWebdavPathPrefixView = (EditText)findViewById(R.id.webdav_path_prefix);
        mWebdavAuthPathView = (EditText)findViewById(R.id.webdav_auth_path);
        mWebdavMailboxPathView = (EditText)findViewById(R.id.webdav_mailbox_path);
        mNextButton = (Button)findViewById(R.id.next);
        mCompressionMobile = (CheckBox)findViewById(R.id.compression_mobile);
        mCompressionWifi = (CheckBox)findViewById(R.id.compression_wifi);
        mCompressionOther = (CheckBox)findViewById(R.id.compression_other);
        mSubscribedFoldersOnly = (CheckBox)findViewById(R.id.subscribed_folders_only);

        mNextButton.setOnClickListener(this);

        mImapAutoDetectNamespaceView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mImapPathPrefixView.setEnabled(!isChecked);
                if (isChecked && mImapPathPrefixView.hasFocus()) {
                    mImapPathPrefixView.focusSearch(View.FOCUS_UP).requestFocus();
                } else if (!isChecked) {
                    mImapPathPrefixView.requestFocus();
                }
            }
        });

        mAuthTypeAdapter = AuthType.getArrayAdapter(this);
        mAuthTypeView.setAdapter(mAuthTypeAdapter);

        /*
         * Only allow digits in the port field.
         */
        mPortView.setKeyListener(DigitsKeyListener.getInstance("0123456789"));

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        mMakeDefault = getIntent().getBooleanExtra(EXTRA_MAKE_DEFAULT, false);

        /*
         * If we're being reloaded we override the original account with the one
         * we saved
         */
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_ACCOUNT)) {
            accountUuid = savedInstanceState.getString(EXTRA_ACCOUNT);
            mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        }

        try {
            ServerSettings settings = Store.decodeStoreUri(mAccount.getStoreUri());

            if (savedInstanceState == null) {
                // The first item is selected if settings.authenticationType is null or is not in mAuthTypeAdapter
                mCurrentAuthTypeViewPosition = mAuthTypeAdapter.getPosition(settings.authenticationType);
            } else {
                mCurrentAuthTypeViewPosition = savedInstanceState.getInt(STATE_AUTH_TYPE_POSITION);
            }
            mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
            updateViewFromAuthType();

            if (settings.username != null) {
                mUsernameView.setText(settings.username);
            }

            if (settings.password != null) {
                mPasswordView.setText(settings.password);
            }

            if (settings.clientCertificateAlias != null) {
                mClientCertificateSpinner.setAlias(settings.clientCertificateAlias);
            }

            mStoreType = settings.type;
            if (Pop3Store.STORE_TYPE.equals(settings.type)) {
                serverLabelView.setText(R.string.account_setup_incoming_pop_server_label);
                mDefaultPort = POP3_PORT;
                mDefaultSslPort = POP3_SSL_PORT;
                findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
                findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);
                findViewById(R.id.compression_section).setVisibility(View.GONE);
                findViewById(R.id.compression_label).setVisibility(View.GONE);
                mSubscribedFoldersOnly.setVisibility(View.GONE);
                mAccount.setDeletePolicy(Account.DELETE_POLICY_NEVER);
            } else if (ImapStore.STORE_TYPE.equals(settings.type)) {
                serverLabelView.setText(R.string.account_setup_incoming_imap_server_label);
                mDefaultPort = IMAP_PORT;
                mDefaultSslPort = IMAP_SSL_PORT;

                ImapStoreSettings imapSettings = (ImapStoreSettings) settings;

                mImapAutoDetectNamespaceView.setChecked(imapSettings.autoDetectNamespace);
                if (imapSettings.pathPrefix != null) {
                    mImapPathPrefixView.setText(imapSettings.pathPrefix);
                }

                findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
                findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);
                mAccount.setDeletePolicy(Account.DELETE_POLICY_ON_DELETE);

                if (!Intent.ACTION_EDIT.equals(getIntent().getAction())) {
                    findViewById(R.id.imap_folder_setup_section).setVisibility(View.GONE);
                }
            } else if (WebDavStore.STORE_TYPE.equals(settings.type)) {
                serverLabelView.setText(R.string.account_setup_incoming_webdav_server_label);
                mDefaultPort = WEBDAV_PORT;
                mDefaultSslPort = WEBDAV_SSL_PORT;
                mConnectionSecurityChoices = new ConnectionSecurity[] {
                        ConnectionSecurity.NONE,
                        ConnectionSecurity.SSL_TLS_REQUIRED };

                // Hide the unnecessary fields
                findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
                findViewById(R.id.account_auth_type_label).setVisibility(View.GONE);
                findViewById(R.id.account_auth_type).setVisibility(View.GONE);
                findViewById(R.id.compression_section).setVisibility(View.GONE);
                findViewById(R.id.compression_label).setVisibility(View.GONE);
                mSubscribedFoldersOnly.setVisibility(View.GONE);

                WebDavStoreSettings webDavSettings = (WebDavStoreSettings) settings;

                if (webDavSettings.path != null) {
                    mWebdavPathPrefixView.setText(webDavSettings.path);
                }

                if (webDavSettings.authPath != null) {
                    mWebdavAuthPathView.setText(webDavSettings.authPath);
                }

                if (webDavSettings.mailboxPath != null) {
                    mWebdavMailboxPathView.setText(webDavSettings.mailboxPath);
                }
                mAccount.setDeletePolicy(Account.DELETE_POLICY_ON_DELETE);
            } else {
                throw new Exception("Unknown account type: " + mAccount.getStoreUri());
            }

            // Note that mConnectionSecurityChoices is configured above based on server type
            ArrayAdapter<ConnectionSecurity> securityTypesAdapter =
                    ConnectionSecurity.getArrayAdapter(this, mConnectionSecurityChoices);
            mSecurityTypeView.setAdapter(securityTypesAdapter);

            // Select currently configured security type
            if (savedInstanceState == null) {
                mCurrentSecurityTypeViewPosition = securityTypesAdapter.getPosition(settings.connectionSecurity);
            } else {

                /*
                 * Restore the spinner state now, before calling
                 * setOnItemSelectedListener(), thus avoiding a call to
                 * onItemSelected(). Then, when the system restores the state
                 * (again) in onRestoreInstanceState(), The system will see that
                 * the new state is the same as the current state (set here), so
                 * once again onItemSelected() will not be called.
                 */
                mCurrentSecurityTypeViewPosition = savedInstanceState.getInt(STATE_SECURITY_TYPE_POSITION);
            }
            mSecurityTypeView.setSelection(mCurrentSecurityTypeViewPosition, false);

            updateAuthPlainTextFromSecurityType(settings.connectionSecurity);

            mCompressionMobile.setChecked(mAccount.useCompression(Account.TYPE_MOBILE));
            mCompressionWifi.setChecked(mAccount.useCompression(Account.TYPE_WIFI));
            mCompressionOther.setChecked(mAccount.useCompression(Account.TYPE_OTHER));

            if (settings.host != null) {
                mServerView.setText(settings.host);
            }

            if (settings.port != -1) {
                mPortView.setText(Integer.toString(settings.port));
            } else {
                updatePortFromSecurityType();
            }
            mCurrentPortViewSetting = mPortView.getText().toString();

            mSubscribedFoldersOnly.setChecked(mAccount.subscribedFoldersOnly());
        } catch (Exception e) {
            failure(e);
        }
    }

    /**
     * Called at the end of either {@code onCreate()} or
     * {@code onRestoreInstanceState()}, after the views have been initialized,
     * so that the listeners are not triggered during the view initialization.
     * This avoids needless calls to {@code validateFields()} which is called
     * immediately after this is called.
     */
    private void initializeViewListeners() {

        /*
         * Updates the port when the user changes the security type. This allows
         * us to show a reasonable default which the user can change.
         */
        mSecurityTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {

                /*
                 * We keep our own record of the spinner state so we
                 * know for sure that onItemSelected() was called
                 * because of user input, not because of spinner
                 * state initialization. This assures that the port
                 * will not be replaced with a default value except
                 * on user input.
                 */
                if (mCurrentSecurityTypeViewPosition != position) {
                    updatePortFromSecurityType();
                    validateFields();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        mAuthTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                if (mCurrentAuthTypeViewPosition == position) {
                    return;
                }

                updateViewFromAuthType();
                validateFields();
                AuthType selection = (AuthType) mAuthTypeView.getSelectedItem();

                // Have the user select (or confirm) the client certificate
                if (AuthType.EXTERNAL == selection) {

                    // This may again invoke validateFields()
                    mClientCertificateSpinner.chooseCertificate();
                } else {
                    mPasswordView.requestFocus();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        mClientCertificateSpinner.setOnClientCertificateChangedListener(clientCertificateChangedListener);
        mUsernameView.addTextChangedListener(validationTextWatcher);
        mPasswordView.addTextChangedListener(validationTextWatcher);
        mServerView.addTextChangedListener(validationTextWatcher);
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_ACCOUNT, mAccount.getUuid());
        outState.putInt(STATE_SECURITY_TYPE_POSITION, mCurrentSecurityTypeViewPosition);
        outState.putInt(STATE_AUTH_TYPE_POSITION, mCurrentAuthTypeViewPosition);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        /*
         * We didn't want the listeners active while the state was being restored
         * because they could overwrite the restored port with a default port when
         * the security type was restored.
         */
        initializeViewListeners();
        validateFields();
    }

    /**
     * Shows/hides password field and client certificate spinner
     */
    private void updateViewFromAuthType() {
        AuthType authType = (AuthType) mAuthTypeView.getSelectedItem();
        boolean isAuthTypeExternal = (AuthType.EXTERNAL == authType);

        if (isAuthTypeExternal) {

            // hide password fields, show client certificate fields
            mPasswordView.setVisibility(View.GONE);
            mPasswordLabelView.setVisibility(View.GONE);
            mClientCertificateLabelView.setVisibility(View.VISIBLE);
            mClientCertificateSpinner.setVisibility(View.VISIBLE);
        } else {

            // show password fields, hide client certificate fields
            mPasswordView.setVisibility(View.VISIBLE);
            mPasswordLabelView.setVisibility(View.VISIBLE);
            mClientCertificateLabelView.setVisibility(View.GONE);
            mClientCertificateSpinner.setVisibility(View.GONE);
        }
    }

    /**
     * This is invoked only when the user makes changes to a widget, not when
     * widgets are changed programmatically.  (The logic is simpler when you know
     * that this is the last thing called after an input change.)
     */
    private void validateFields() {
        AuthType authType = (AuthType) mAuthTypeView.getSelectedItem();
        boolean isAuthTypeExternal = (AuthType.EXTERNAL == authType);

        ConnectionSecurity connectionSecurity = (ConnectionSecurity) mSecurityTypeView.getSelectedItem();
        boolean hasConnectionSecurity = (connectionSecurity != ConnectionSecurity.NONE);

        if (isAuthTypeExternal && !hasConnectionSecurity) {

            // Notify user of an invalid combination of AuthType.EXTERNAL & ConnectionSecurity.NONE
            String toastText = getString(R.string.account_setup_incoming_invalid_setting_combo_notice,
                    getString(R.string.account_setup_incoming_auth_type_label),
                    AuthType.EXTERNAL.toString(),
                    getString(R.string.account_setup_incoming_security_label),
                    ConnectionSecurity.NONE.toString());
            Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();

            // Reset the views back to their previous settings without recursing through here again
            OnItemSelectedListener onItemSelectedListener = mAuthTypeView.getOnItemSelectedListener();
            mAuthTypeView.setOnItemSelectedListener(null);
            mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
            mAuthTypeView.setOnItemSelectedListener(onItemSelectedListener);
            updateViewFromAuthType();

            onItemSelectedListener = mSecurityTypeView.getOnItemSelectedListener();
            mSecurityTypeView.setOnItemSelectedListener(null);
            mSecurityTypeView.setSelection(mCurrentSecurityTypeViewPosition, false);
            mSecurityTypeView.setOnItemSelectedListener(onItemSelectedListener);
            updateAuthPlainTextFromSecurityType((ConnectionSecurity) mSecurityTypeView.getSelectedItem());

            mPortView.removeTextChangedListener(validationTextWatcher);
            mPortView.setText(mCurrentPortViewSetting);
            mPortView.addTextChangedListener(validationTextWatcher);

            authType = (AuthType) mAuthTypeView.getSelectedItem();
            isAuthTypeExternal = (AuthType.EXTERNAL == authType);

            connectionSecurity = (ConnectionSecurity) mSecurityTypeView.getSelectedItem();
            hasConnectionSecurity = (connectionSecurity != ConnectionSecurity.NONE);
        } else {
            mCurrentAuthTypeViewPosition = mAuthTypeView.getSelectedItemPosition();
            mCurrentSecurityTypeViewPosition = mSecurityTypeView.getSelectedItemPosition();
            mCurrentPortViewSetting = mPortView.getText().toString();
        }

        boolean hasValidCertificateAlias = mClientCertificateSpinner.getAlias() != null;
        boolean hasValidUserName = Utility.requiredFieldValid(mUsernameView);

        boolean hasValidPasswordSettings = hasValidUserName
                && !isAuthTypeExternal
                && Utility.requiredFieldValid(mPasswordView);

        boolean hasValidExternalAuthSettings = hasValidUserName
                && isAuthTypeExternal
                && hasConnectionSecurity
                && hasValidCertificateAlias;

        mNextButton.setEnabled(Utility.domainFieldValid(mServerView)
                && Utility.requiredFieldValid(mPortView)
                && (hasValidPasswordSettings || hasValidExternalAuthSettings));
        Utility.setCompoundDrawablesAlpha(mNextButton, mNextButton.isEnabled() ? 255 : 128);
    }

    private void updatePortFromSecurityType() {
        ConnectionSecurity securityType = (ConnectionSecurity) mSecurityTypeView.getSelectedItem();
        updateAuthPlainTextFromSecurityType(securityType);

        // Remove listener so as not to trigger validateFields() which is called
        // elsewhere as a result of user interaction.
        mPortView.removeTextChangedListener(validationTextWatcher);
        mPortView.setText(getDefaultPort(securityType));
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    private String getDefaultPort(ConnectionSecurity securityType) {
        String port;
        switch (securityType) {
        case NONE:
        case STARTTLS_REQUIRED:
            port = mDefaultPort;
            break;
        case SSL_TLS_REQUIRED:
            port = mDefaultSslPort;
            break;
        default:
            Log.e(K9.LOG_TAG, "Unhandled ConnectionSecurity type encountered");
            port = "";
        }
        return port;
    }

    private void updateAuthPlainTextFromSecurityType(ConnectionSecurity securityType) {
        switch (securityType) {
        case NONE:
            AuthType.PLAIN.useInsecureText(true, mAuthTypeAdapter);
            break;
        default:
            AuthType.PLAIN.useInsecureText(false, mAuthTypeAdapter);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (Intent.ACTION_EDIT.equals(getIntent().getAction())) {
                boolean isPushCapable = false;
                try {
                    Store store = mAccount.getRemoteStore();
                    isPushCapable = store.isPushCapable();
                } catch (Exception e) {
                    Log.e(K9.LOG_TAG, "Could not get remote store", e);
                }
                if (isPushCapable && mAccount.getFolderPushMode() != FolderMode.NONE) {
                    MailService.actionRestartPushers(this, null);
                }
                mAccount.save(Preferences.getPreferences(this));
                finish();
            } else {
                /*
                 * Set the username and password for the outgoing settings to the username and
                 * password the user just set for incoming.
                 */
                try {
                    String username = mUsernameView.getText().toString();

                    String password = null;
                    String clientCertificateAlias = null;
                    AuthType authType = (AuthType) mAuthTypeView.getSelectedItem();
                    if (AuthType.EXTERNAL == authType) {
                        clientCertificateAlias = mClientCertificateSpinner.getAlias();
                    } else {
                        password = mPasswordView.getText().toString();
                    }

                    URI oldUri = new URI(mAccount.getTransportUri());
                    ServerSettings transportServer = new ServerSettings(SmtpTransport.TRANSPORT_TYPE, oldUri.getHost(), oldUri.getPort(),
                            ConnectionSecurity.SSL_TLS_REQUIRED, authType, username, password, clientCertificateAlias);
                    String transportUri = Transport.createTransportUri(transportServer);
                    mAccount.setTransportUri(transportUri);
                } catch (URISyntaxException use) {
                    /*
                     * If we can't set up the URL we just continue. It's only for
                     * convenience.
                     */
                }


                AccountSetupOutgoing.actionOutgoingSettings(this, mAccount, mMakeDefault);
                finish();
            }
        }
    }

    protected void onNext() {
        try {
            ConnectionSecurity connectionSecurity = (ConnectionSecurity) mSecurityTypeView.getSelectedItem();

            String username = mUsernameView.getText().toString();
            String password = null;
            String clientCertificateAlias = null;

            AuthType authType = (AuthType) mAuthTypeView.getSelectedItem();
            if (authType == AuthType.EXTERNAL) {
                clientCertificateAlias = mClientCertificateSpinner.getAlias();
            } else {
                password = mPasswordView.getText().toString();
            }
            String host = mServerView.getText().toString();
            int port = Integer.parseInt(mPortView.getText().toString());

            Map<String, String> extra = null;
            if (ImapStore.STORE_TYPE.equals(mStoreType)) {
                extra = new HashMap<String, String>();
                extra.put(ImapStoreSettings.AUTODETECT_NAMESPACE_KEY,
                        Boolean.toString(mImapAutoDetectNamespaceView.isChecked()));
                extra.put(ImapStoreSettings.PATH_PREFIX_KEY,
                        mImapPathPrefixView.getText().toString());
            } else if (WebDavStore.STORE_TYPE.equals(mStoreType)) {
                extra = new HashMap<String, String>();
                extra.put(WebDavStoreSettings.PATH_KEY,
                        mWebdavPathPrefixView.getText().toString());
                extra.put(WebDavStoreSettings.AUTH_PATH_KEY,
                        mWebdavAuthPathView.getText().toString());
                extra.put(WebDavStoreSettings.MAILBOX_PATH_KEY,
                        mWebdavMailboxPathView.getText().toString());
            }

            mAccount.deleteCertificate(host, port, CheckDirection.INCOMING);
            ServerSettings settings = new ServerSettings(mStoreType, host, port,
                    connectionSecurity, authType, username, password, clientCertificateAlias, extra);

            mAccount.setStoreUri(Store.createStoreUri(settings));

            mAccount.setCompression(Account.TYPE_MOBILE, mCompressionMobile.isChecked());
            mAccount.setCompression(Account.TYPE_WIFI, mCompressionWifi.isChecked());
            mAccount.setCompression(Account.TYPE_OTHER, mCompressionOther.isChecked());
            mAccount.setSubscribedFoldersOnly(mSubscribedFoldersOnly.isChecked());

            AccountSetupCheckSettings.actionCheckSettings(this, mAccount, CheckDirection.INCOMING);
        } catch (Exception e) {
            failure(e);
        }

    }

    public void onClick(View v) {
        try {
            switch (v.getId()) {
            case R.id.next:
                onNext();
                break;
            }
        } catch (Exception e) {
            failure(e);
        }
    }

    private void failure(Exception use) {
        Log.e(K9.LOG_TAG, "Failure", use);
        String toastText = getString(R.string.account_setup_bad_uri, use.getMessage());

        Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }

    /*
     * Calls validateFields() which enables or disables the Next button
     * based on the fields' validity.
     */
    TextWatcher validationTextWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            validateFields();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            /* unused */
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            /* unused */
        }
    };

    OnClientCertificateChangedListener clientCertificateChangedListener = new OnClientCertificateChangedListener() {
        @Override
        public void onClientCertificateChanged(String alias) {
            validateFields();
        }
    };
}
