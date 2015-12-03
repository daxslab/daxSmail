package com.daxslab.mail.mail;

import android.content.Context;
import android.widget.ArrayAdapter;

import com.daxslab.mail.K9;
import com.daxslab.mail.R;

public enum ConnectionSecurity {
    NONE(R.string.account_setup_incoming_security_none_label),
    STARTTLS_REQUIRED(R.string.account_setup_incoming_security_tls_label),
    SSL_TLS_REQUIRED(R.string.account_setup_incoming_security_ssl_label);

    static public ArrayAdapter<ConnectionSecurity> getArrayAdapter(Context context) {
        return getArrayAdapter(context, ConnectionSecurity.values());
    }

    static public ArrayAdapter<ConnectionSecurity> getArrayAdapter(Context context, ConnectionSecurity[] securityTypes) {
        ArrayAdapter<ConnectionSecurity> securityTypesAdapter = new ArrayAdapter<ConnectionSecurity>(context,
                android.R.layout.simple_spinner_item, securityTypes);
        securityTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return securityTypesAdapter;
    }

    private final int mResourceId;

    private ConnectionSecurity(int id) {
        mResourceId = id;
    }

    @Override
    public String toString() {
        return K9.app.getString(mResourceId);
    }
}
