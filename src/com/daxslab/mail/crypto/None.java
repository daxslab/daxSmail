package com.daxslab.mail.crypto;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;

import com.daxslab.mail.mail.Message;

/**
 * Dummy CryptoProvider for when cryptography is disabled. It is never "available" and doesn't
 * do anything.
 */
public class None extends CryptoProvider {
    static final long serialVersionUID = 0x21071230;
    public static final String NAME = "";

    public static None createInstance() {
        return new None();
    }

    @Override
    public boolean isAvailable(Context context) {
        return false;
    }

    @Override
    public boolean selectSecretKey(Activity activity, PgpData pgpData) {
        return false;
    }

    @Override
    public boolean selectEncryptionKeys(Activity activity, String emails, PgpData pgpData) {
        return false;
    }

    @Override
    public long[] getSecretKeyIdsFromEmail(Context context, String email) {
        return null;
    }

    @Override
    public long[] getPublicKeyIdsFromEmail(Context context, String email) {
        return null;
    }

    @Override
    public boolean hasSecretKeyForEmail(Context context, String email) {
        return false;
    }

    @Override
    public boolean hasPublicKeyForEmail(Context context, String email) {
        return false;
    }

    @Override
    public String getUserId(Context context, long keyId) {
        return null;
    }

    @Override
    public boolean onActivityResult(Activity activity, int requestCode, int resultCode,
                                    android.content.Intent data, PgpData pgpData) {
        return false;
    }

    @Override
    public boolean onDecryptActivityResult(CryptoDecryptCallback callback, int requestCode,
            int resultCode, Intent data, PgpData pgpData) {
        return false;
    }

    @Override
    public boolean encrypt(Activity activity, String data, PgpData pgpData) {
        return false;
    }

    @Override
    public boolean decrypt(Fragment fragment, String data, PgpData pgpData) {
        return false;
    }

    @Override
    public boolean isEncrypted(Message message) {
        return false;
    }

    @Override
    public boolean isSigned(Message message) {
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean test(Context context) {
        return true;
    }
}
