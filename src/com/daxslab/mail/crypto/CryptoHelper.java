
package com.daxslab.mail.crypto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.daxslab.mail.mail.Message;
import com.daxslab.mail.mail.MessagingException;
import com.daxslab.mail.mail.Part;
import com.daxslab.mail.mail.internet.MimeUtility;

public class CryptoHelper {

    public static Pattern PGP_MESSAGE =
            Pattern.compile(".*?(-----BEGIN PGP MESSAGE-----.*?-----END PGP MESSAGE-----).*",
                    Pattern.DOTALL);

    public static Pattern PGP_SIGNED_MESSAGE =
            Pattern.compile(
                    ".*?(-----BEGIN PGP SIGNED MESSAGE-----.*?-----BEGIN PGP SIGNATURE-----.*?-----END PGP SIGNATURE-----).*",
                    Pattern.DOTALL);

    public CryptoHelper() {
        super();
    }

    /**
     * TODO: use new parseMessage() from PgpUtils to actually parse!
     * @param message
     * @return
     */
    public boolean isEncrypted(Message message) {
        String data = null;
        try {
            Part part = MimeUtility.findFirstPartByMimeType(message, "text/plain");
            if (part == null) {
                part = MimeUtility.findFirstPartByMimeType(message, "text/html");
            }
            if (part != null) {
                data = MimeUtility.getTextFromPart(part);
            }
        } catch (MessagingException e) {
            // guess not...
            // TODO: maybe log this?
        }

        if (data == null) {
            return false;
        }

        Matcher matcher = PGP_MESSAGE.matcher(data);
        return matcher.matches();
    }

    public boolean isSigned(Message message) {
        String data = null;
        try {
            Part part = MimeUtility.findFirstPartByMimeType(message, "text/plain");
            if (part == null) {
                part = MimeUtility.findFirstPartByMimeType(message, "text/html");
            }
            if (part != null) {
                data = MimeUtility.getTextFromPart(part);
            }
        } catch (MessagingException e) {
            // guess not...
            // TODO: maybe log this?
        }

        if (data == null) {
            return false;
        }

        Matcher matcher = PGP_SIGNED_MESSAGE.matcher(data);
        return matcher.matches();
    }
}
