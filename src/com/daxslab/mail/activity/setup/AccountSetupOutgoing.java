
package com.daxslab.mail.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.daxslab.mail.*;
import com.daxslab.mail.activity.K9Activity;
import com.daxslab.mail.activity.setup.AccountSetupCheckSettings.CheckDirection;
import com.daxslab.mail.helper.Utility;
import com.daxslab.mail.mail.AuthType;
import com.daxslab.mail.mail.ConnectionSecurity;
import com.daxslab.mail.mail.ServerSettings;
import com.daxslab.mail.mail.Transport;
import com.daxslab.mail.mail.transport.SmtpTransport;
import com.daxslab.mail.view.ClientCertificateSpinner;
import com.daxslab.mail.view.ClientCertificateSpinner.OnClientCertificateChangedListener;

import java.net.URI;
import java.net.URISyntaxException;

public class AccountSetupOutgoing extends K9Activity implements OnClickListener,
    OnCheckedChangeListener {
    private static final String EXTRA_ACCOUNT = "account";

    private static final String EXTRA_MAKE_DEFAULT = "makeDefault";
    private static final String STATE_SECURITY_TYPE_POSITION = "stateSecurityTypePosition";
    private static final String STATE_AUTH_TYPE_POSITION = "authTypePosition";

    private static final String SMTP_PORT = "587";
    private static final String SMTP_SSL_PORT = "465";

    private EditText mUsernameView;
    private EditText mPasswordView;
    private ClientCertificateSpinner mClientCertificateSpinner;
    private TextView mClientCertificateLabelView;
    private TextView mPasswordLabelView;
    private EditText mServerView;
    private EditText mPortView;
    private String mCurrentPortViewSetting;
    private CheckBox mRequireLoginView;
    private ViewGroup mRequireLoginSettingsView;
    private Spinner mSecurityTypeView;
    private int mCurrentSecurityTypeViewPosition;
    private Spinner mAuthTypeView;
    private int mCurrentAuthTypeViewPosition;
    private ArrayAdapter<AuthType> mAuthTypeAdapter;
    private Button mNextButton;
    private Account mAccount;
    private boolean mMakeDefault;

    public static void actionOutgoingSettings(Context context, Account account, boolean makeDefault) {
        Intent i = new Intent(context, AccountSetupOutgoing.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MAKE_DEFAULT, makeDefault);
        context.startActivity(i);
    }

    public static void actionEditOutgoingSettings(Context context, Account account) {
        context.startActivity(intentActionEditOutgoingSettings(context, account));
    }

    public static Intent intentActionEditOutgoingSettings(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupOutgoing.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_outgoing);

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);

        try {
            if (new URI(mAccount.getStoreUri()).getScheme().startsWith("webdav")) {
                mAccount.setTransportUri(mAccount.getStoreUri());
                AccountSetupCheckSettings.actionCheckSettings(this, mAccount, CheckDirection.OUTGOING);
            }
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        mUsernameView = (EditText)findViewById(R.id.account_username);
        mPasswordView = (EditText)findViewById(R.id.account_password);
        mClientCertificateSpinner = (ClientCertificateSpinner)findViewById(R.id.account_client_certificate_spinner);
        mClientCertificateLabelView = (TextView)findViewById(R.id.account_client_certificate_label);
        mPasswordLabelView = (TextView)findViewById(R.id.account_password_label);
        mServerView = (EditText)findViewById(R.id.account_server);
        mPortView = (EditText)findViewById(R.id.account_port);
        mRequireLoginView = (CheckBox)findViewById(R.id.account_require_login);
        mRequireLoginSettingsView = (ViewGroup)findViewById(R.id.account_require_login_settings);
        mSecurityTypeView = (Spinner)findViewById(R.id.account_security_type);
        mAuthTypeView = (Spinner)findViewById(R.id.account_auth_type);
        mNextButton = (Button)findViewById(R.id.next);

        mNextButton.setOnClickListener(this);

        mSecurityTypeView.setAdapter(ConnectionSecurity.getArrayAdapter(this));

        mAuthTypeAdapter = AuthType.getArrayAdapter(this);
        mAuthTypeView.setAdapter(mAuthTypeAdapter);

        /*
         * Only allow digits in the port field.
         */
        mPortView.setKeyListener(DigitsKeyListener.getInstance("0123456789"));

        //FIXME: get Account object again?
        accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
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
            ServerSettings settings = Transport.decodeTransportUri(mAccount.getTransportUri());

            updateAuthPlainTextFromSecurityType(settings.connectionSecurity);

            if (savedInstanceState == null) {
                // The first item is selected if settings.authenticationType is null or is not in mAuthTypeAdapter
                mCurrentAuthTypeViewPosition = mAuthTypeAdapter.getPosition(settings.authenticationType);
            } else {
                mCurrentAuthTypeViewPosition = savedInstanceState.getInt(STATE_AUTH_TYPE_POSITION);
            }
            mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
            updateViewFromAuthType();

            // Select currently configured security type
            if (savedInstanceState == null) {
                mCurrentSecurityTypeViewPosition = settings.connectionSecurity.ordinal();
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

            if (settings.username != null && !settings.username.isEmpty()) {
                mUsernameView.setText(settings.username);
                mRequireLoginView.setChecked(true);
                mRequireLoginSettingsView.setVisibility(View.VISIBLE);
            }

            if (settings.password != null) {
                mPasswordView.setText(settings.password);
            }

            if (settings.clientCertificateAlias != null) {
                mClientCertificateSpinner.setAlias(settings.clientCertificateAlias);
            }

            if (settings.host != null) {
                mServerView.setText(settings.host);
            }

            if (settings.port != -1) {
                mPortView.setText(Integer.toString(settings.port));
            } else {
                updatePortFromSecurityType();
            }
            mCurrentPortViewSetting = mPortView.getText().toString();
        } catch (Exception e) {
            /*
             * We should always be able to parse our own settings.
             */
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

                    boolean isInsecure = (ConnectionSecurity.NONE == mSecurityTypeView.getSelectedItem());
                    boolean isAuthExternal = (AuthType.EXTERNAL == mAuthTypeView.getSelectedItem());
                    boolean loginNotRequired = !mRequireLoginView.isChecked();

                    /*
                     * If the user selects ConnectionSecurity.NONE, a
                     * warning would normally pop up if the authentication
                     * is AuthType.EXTERNAL (i.e., using client
                     * certificates). But such a warning is irrelevant if
                     * login is not required. So to avoid such a warning
                     * (generated in validateFields()) under those
                     * conditions, we change the (irrelevant) authentication
                     * method to PLAIN.
                     */
                    if (isInsecure && isAuthExternal && loginNotRequired) {
                        OnItemSelectedListener onItemSelectedListener = mAuthTypeView.getOnItemSelectedListener();
                        mAuthTypeView.setOnItemSelectedListener(null);
                        mCurrentAuthTypeViewPosition = mAuthTypeAdapter.getPosition(AuthType.PLAIN);
                        mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
                        mAuthTypeView.setOnItemSelectedListener(onItemSelectedListener);
                        updateViewFromAuthType();
                    }

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

        mRequireLoginView.setOnCheckedChangeListener(this);
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
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (mRequireLoginView.isChecked()) {
            mRequireLoginSettingsView.setVisibility(View.VISIBLE);
        } else {
            mRequireLoginSettingsView.setVisibility(View.GONE);
        }
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
            String toastText = getString(R.string.account_setup_outgoing_invalid_setting_combo_notice,
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

        mNextButton
                .setEnabled(Utility.domainFieldValid(mServerView)
                        && Utility.requiredFieldValid(mPortView)
                        && (!mRequireLoginView.isChecked()
                                || hasValidPasswordSettings || hasValidExternalAuthSettings));
        Utility.setCompoundDrawablesAlpha(mNextButton, mNextButton.isEnabled() ? 255 : 128);
    }

    private void updatePortFromSecurityType() {
        ConnectionSecurity securityType = (ConnectionSecurity) mSecurityTypeView.getSelectedItem();
        updateAuthPlainTextFromSecurityType(securityType);

        // Remove listener so as not to trigger validateFields() which is called
        // elsewhere as a result of user interaction.
        mPortView.removeTextChangedListener(validationTextWatcher);
        mPortView.setText(getDefaultSmtpPort(securityType));
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    private String getDefaultSmtpPort(ConnectionSecurity securityType) {
        String port;
        switch (securityType) {
        case NONE:
        case STARTTLS_REQUIRED:
            port = SMTP_PORT;
            break;
        case SSL_TLS_REQUIRED:
            port = SMTP_SSL_PORT;
            break;
        default:
            port = "";
            Log.e(K9.LOG_TAG, "Unhandled ConnectionSecurity type encountered");
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
                mAccount.save(Preferences.getPreferences(this));
                finish();
            } else {
                AccountSetupOptions.actionOptions(this, mAccount, mMakeDefault);
                finish();
            }
        }
    }

    protected void onNext() {
        ConnectionSecurity securityType = (ConnectionSecurity) mSecurityTypeView.getSelectedItem();
        String uri;
        String username = null;
        String password = null;
        String clientCertificateAlias = null;
        AuthType authType = null;
        if (mRequireLoginView.isChecked()) {
            username = mUsernameView.getText().toString();

            authType = (AuthType) mAuthTypeView.getSelectedItem();
            if (AuthType.EXTERNAL == authType) {
                clientCertificateAlias = mClientCertificateSpinner.getAlias();
            } else {
                password = mPasswordView.getText().toString();
            }
        }

        String newHost = mServerView.getText().toString();
        int newPort = Integer.parseInt(mPortView.getText().toString());
        String type = SmtpTransport.TRANSPORT_TYPE;
        ServerSettings server = new ServerSettings(type, newHost, newPort, securityType, authType, username, password, clientCertificateAlias);
        uri = Transport.createTransportUri(server);
        mAccount.deleteCertificate(newHost, newPort, CheckDirection.OUTGOING);
        mAccount.setTransportUri(uri);
        AccountSetupCheckSettings.actionCheckSettings(this, mAccount, CheckDirection.OUTGOING);
    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.next:
            onNext();
            break;
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mRequireLoginSettingsView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        validateFields();
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
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    OnClientCertificateChangedListener clientCertificateChangedListener = new OnClientCertificateChangedListener() {
        @Override
        public void onClientCertificateChanged(String alias) {
            validateFields();
        }
    };
}
