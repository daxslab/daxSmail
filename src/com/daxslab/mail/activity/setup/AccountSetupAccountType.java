
package com.daxslab.mail.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import com.daxslab.mail.Account;
import com.daxslab.mail.K9;
import com.daxslab.mail.Preferences;
import com.daxslab.mail.R;
import com.daxslab.mail.activity.Accounts;
import com.daxslab.mail.activity.K9Activity;
import com.daxslab.mail.mail.AuthType;
import com.daxslab.mail.mail.ConnectionSecurity;
import com.daxslab.mail.mail.ServerSettings;
import com.daxslab.mail.mail.Store;
import com.daxslab.mail.mail.Transport;
import com.daxslab.mail.mail.store.ImapStore;
import com.daxslab.mail.mail.store.Pop3Store;
import com.daxslab.mail.mail.transport.SmtpTransport;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Prompts the user to select an account type. The account type, along with the
 * passed in email address, password and makeDefault are then passed on to the
 * AccountSetupIncoming activity.
 */
public class AccountSetupAccountType extends K9Activity implements OnClickListener {
    private static final String EXTRA_ACCOUNT = "account";

    private static final String EXTRA_MAKE_DEFAULT = "makeDefault";

    private Account mAccount;

    private boolean mMakeDefault;

    public static void actionSelectAccountType(Context context, Account account, boolean makeDefault) {
        Intent i = new Intent(context, AccountSetupAccountType.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MAKE_DEFAULT, makeDefault);
        context.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_account_type);
        ((Button)findViewById(R.id.nautaImap)).setOnClickListener(this);
        ((Button)findViewById(R.id.nautaPop3)).setOnClickListener(this);
        ((Button)findViewById(R.id.pop)).setOnClickListener(this);
        ((Button)findViewById(R.id.imap)).setOnClickListener(this);
        ((Button)findViewById(R.id.webdav)).setOnClickListener(this);

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        mMakeDefault = getIntent().getBooleanExtra(EXTRA_MAKE_DEFAULT, false);
    }

    private void setNautaSmtp(String username, String password) throws Exception{
        /**
         * Set Nauta smtp settings to mAccount
         */

        URI uri = new URI(mAccount.getTransportUri());
        uri = new URI("smtp+tls+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
        mAccount.setTransportUri(uri.toString());

        ConnectionSecurity securityType = ConnectionSecurity.NONE;

        String host = "smtp.nauta.cu";
        int port = 25;
        String type = SmtpTransport.TRANSPORT_TYPE;
        AuthType authType = AuthType.PLAIN;

        ServerSettings server = new ServerSettings(type, host, port, securityType, authType, username, password, null);
        String smtpUri = Transport.createTransportUri(server);
        mAccount.deleteCertificate(host, port, AccountSetupCheckSettings.CheckDirection.OUTGOING);
        mAccount.setTransportUri(smtpUri);

    }

    private void onNautaImap(){
        try {

            URI uri = new URI(mAccount.getStoreUri());
            uri = new URI("imap+ssl+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setStoreUri(uri.toString());

            ServerSettings settings = Store.decodeStoreUri(mAccount.getStoreUri());

            // incoming

            mAccount.setDeletePolicy(Account.DELETE_POLICY_ON_DELETE);

            String mStoreType = ImapStore.STORE_TYPE;
            String host = "imap.nauta.cu";
            int port = 143;

            ConnectionSecurity connectionSecurity = ConnectionSecurity.NONE;
            AuthType authType = AuthType.PLAIN;
            String username = settings.username+"@nauta.cu";
            String password = settings.password;
            String clientCertificateAlias = null;
            Map<String, String> extra = null;

            extra = new HashMap<String, String>();
            extra.put(ImapStore.ImapStoreSettings.AUTODETECT_NAMESPACE_KEY, Boolean.toString(true));
            extra.put(ImapStore.ImapStoreSettings.PATH_PREFIX_KEY, "");

            mAccount.setStoreUri(Store.createStoreUri(settings));

            mAccount.setCompression(Account.TYPE_MOBILE, true);
            mAccount.setCompression(Account.TYPE_WIFI, true);
            mAccount.setCompression(Account.TYPE_OTHER, true);
            mAccount.setSubscribedFoldersOnly(false);

            mAccount.deleteCertificate(host, port, AccountSetupCheckSettings.CheckDirection.INCOMING);
            ServerSettings inServer = new ServerSettings(mStoreType, host, port, connectionSecurity, authType, username, password, clientCertificateAlias);
            String imapUri = Store.createStoreUri(inServer);
            mAccount.setStoreUri(imapUri);


            // outgoing
            setNautaSmtp(username, password);

            // save and finish

            mAccount.save(Preferences.getPreferences(this));

            Accounts.listAccounts(this);
            finish();

        }catch (Exception use) {
            failure(use);
        }
    }

    private void onNautaPop3(){
        try {

            URI uri = new URI(mAccount.getStoreUri());
            uri = new URI("pop3+ssl+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setStoreUri(uri.toString());

            ServerSettings settings = Store.decodeStoreUri(mAccount.getStoreUri());

            // incoming

            mAccount.setDeletePolicy(Account.DELETE_POLICY_NEVER);

            String mStoreType = Pop3Store.STORE_TYPE;
            String host = "pop.nauta.cu";
            int port = 110;

            ConnectionSecurity connectionSecurity = ConnectionSecurity.NONE;
            AuthType authType = AuthType.PLAIN;
            String username = settings.username+"@nauta.cu";
            String password = settings.password;
            String clientCertificateAlias = null;

            mAccount.setStoreUri(Store.createStoreUri(settings));

            mAccount.setCompression(Account.TYPE_MOBILE, true);
            mAccount.setCompression(Account.TYPE_WIFI, true);
            mAccount.setCompression(Account.TYPE_OTHER, true);
            mAccount.setSubscribedFoldersOnly(false);

            mAccount.deleteCertificate(host, port, AccountSetupCheckSettings.CheckDirection.INCOMING);
            ServerSettings inServer = new ServerSettings(mStoreType, host, port, connectionSecurity, authType, username, password, clientCertificateAlias);
            String pop3Uri = Store.createStoreUri(inServer);
            mAccount.setStoreUri(pop3Uri);


            // outgoing
            setNautaSmtp(username, password);

            // save and finish

            mAccount.save(Preferences.getPreferences(this));

            Accounts.listAccounts(this);
            finish();

        }catch (Exception use) {
            failure(use);
        }
    }

    private void onPop() {
        try {
            URI uri = new URI(mAccount.getStoreUri());
            uri = new URI("pop3+ssl+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setStoreUri(uri.toString());

            uri = new URI(mAccount.getTransportUri());
            uri = new URI("smtp+tls+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setTransportUri(uri.toString());

            AccountSetupIncoming.actionIncomingSettings(this, mAccount, mMakeDefault);
            finish();
        } catch (Exception use) {
            failure(use);
        }

    }

    private void onImap() {
        try {
            URI uri = new URI(mAccount.getStoreUri());
            uri = new URI("imap+ssl+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setStoreUri(uri.toString());

            uri = new URI(mAccount.getTransportUri());
            uri = new URI("smtp+tls+", uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setTransportUri(uri.toString());

            AccountSetupIncoming.actionIncomingSettings(this, mAccount, mMakeDefault);
            finish();
        } catch (Exception use) {
            failure(use);
        }

    }

    private void onWebDav() {
        try {
            URI uri = new URI(mAccount.getStoreUri());

            /*
             * The user info we have been given from
             * AccountSetupBasics.onManualSetup() is encoded as an IMAP store
             * URI: AuthType:UserName:Password (no fields should be empty).
             * However, AuthType is not applicable to WebDAV nor to its store
             * URI. Re-encode without it, using just the UserName and Password.
             */
            String userPass = "";
            String[] userInfo = uri.getUserInfo().split(":");
            if (userInfo.length > 1) {
                userPass = userInfo[1];
            }
            if (userInfo.length > 2) {
                userPass = userPass + ":" + userInfo[2];
            }
            uri = new URI("webdav+ssl+", userPass, uri.getHost(), uri.getPort(), null, null, null);
            mAccount.setStoreUri(uri.toString());
            AccountSetupIncoming.actionIncomingSettings(this, mAccount, mMakeDefault);
            finish();
        } catch (Exception use) {
            failure(use);
        }

    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.nautaImap:
            onNautaImap();
            break;
        case R.id.nautaPop3:
            onNautaPop3();
            break;
        case R.id.pop:
            onPop();
            break;
        case R.id.imap:
            onImap();
            break;
        case R.id.webdav:
            onWebDav();
            break;
        }
    }

    private void failure(Exception use) {
        Log.e(K9.LOG_TAG, "Failure", use);
        String toastText = getString(R.string.account_setup_bad_uri, use.getMessage());

        Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }
}
