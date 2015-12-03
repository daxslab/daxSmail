package com.daxslab.mail.crypto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.app.Fragment;

import com.daxslab.mail.mail.Message;

/**
 * A CryptoProvider provides functionalities such as encryption, decryption, digital signatures.
 * It currently also stores the results of such encryption or decryption.
 * TODO: separate the storage from the provider
 */
abstract public class CryptoProvider {
    static final long serialVersionUID = 0x21071234;

    abstract public boolean isAvailable(Context context);
    abstract public boolean isEncrypted(Message message);
    abstract public boolean isSigned(Message message);
    abstract public boolean onActivityResult(Activity activity, int requestCode, int resultCode,
            Intent data, PgpData pgpData);
    abstract public boolean onDecryptActivityResult(CryptoDecryptCallback callback,
            int requestCode, int resultCode, Intent data, PgpData pgpData);
    abstract public boolean selectSecretKey(Activity activity, PgpData pgpData);
    abstract public boolean selectEncryptionKeys(Activity activity, String emails, PgpData pgpData);
    abstract public boolean encrypt(Activity activity, String data, PgpData pgpData);
    abstract public boolean decrypt(Fragment fragment, String data, PgpData pgpData);
    abstract public long[] getSecretKeyIdsFromEmail(Context context, String email);
    abstract public long[] getPublicKeyIdsFromEmail(Context context, String email);
    abstract public boolean hasSecretKeyForEmail(Context context, String email);
    abstract public boolean hasPublicKeyForEmail(Context context, String email);
    abstract public String getUserId(Context context, long keyId);
    abstract public String getName();
    abstract public boolean test(Context context);

    public static CryptoProvider createInstance(String name) {
        if (Apg.NAME.equals(name)) {
            return Apg.createInstance();
        }

        return None.createInstance();
    }

    public interface CryptoDecryptCallback {
        void onDecryptDone(PgpData pgpData);
    }
}
