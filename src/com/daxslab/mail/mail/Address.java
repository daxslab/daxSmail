
package com.daxslab.mail.mail;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.field.address.AddressBuilder;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;

import com.daxslab.mail.K9;
import com.daxslab.mail.helper.Contacts;
import com.daxslab.mail.helper.StringUtils;
import com.daxslab.mail.helper.Utility;


public class Address {
    /**
     * If the number of addresses exceeds this value the addresses aren't
     * resolved to the names of Android contacts.
     *
     * <p>
     * TODO: This number was chosen arbitrarily and should be determined by
     * performance tests.
     * </p>
     *
     * @see Address#toFriendly(Address[], Contacts)
     */
    private static final int TOO_MANY_ADDRESSES = 50;

    /**
     * Immutable empty {@link Address} array
     */
    private static final Address[] EMPTY_ADDRESS_ARRAY = new Address[0];

    String mAddress;

    String mPersonal;


    public Address(Address address) {
        mAddress = address.mAddress;
        mPersonal = address.mPersonal;
    }

    public Address(String address, String personal) {
        this(address, personal, true);
    }

    public Address(String address) {
        this(address, null, true);
    }

    private Address(String address, String personal, boolean parse) {
        if (parse) {
            Rfc822Token[] tokens =  Rfc822Tokenizer.tokenize(address);
            if (tokens.length > 0) {
                Rfc822Token token = tokens[0];
                mAddress = token.getAddress();
                String name = token.getName();
                if (!StringUtils.isNullOrEmpty(name)) {
                    /*
                     * Don't use the "personal" argument if "address" is of the form:
                     * James Bond <james.bond@mi6.uk>
                     *
                     * See issue 2920
                     */
                    mPersonal = name;
                } else {
                    mPersonal = (personal == null) ? null : personal.trim();
                }
            } else {
                // This should be an error
            }
        } else {
            mAddress = address;
            mPersonal = personal;
        }
    }

    public String getAddress() {
        return mAddress;
    }

    public String getHostname() {
        int hostIdx = mAddress.lastIndexOf("@");

        if (hostIdx == -1) {
            return null;
        }

        return mAddress.substring(hostIdx+1);
    }

    public void setAddress(String address) {
        this.mAddress = address;
    }

    public String getPersonal() {
        return mPersonal;
    }

    public void setPersonal(String personal) {
        if ("".equals(personal)) {
            personal = null;
        }
        if (personal != null) {
            personal = personal.trim();
        }
        this.mPersonal = personal;
    }

    /**
     * Parse a comma separated list of email addresses in human readable format and return an
     * array of Address objects, RFC-822 encoded.
     *
     * @param addressList
     * @return An array of 0 or more Addresses.
     */
    public static Address[] parseUnencoded(String addressList) {
        List<Address> addresses = new ArrayList<Address>();
        if (!StringUtils.isNullOrEmpty(addressList)) {
            Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(addressList);
            for (Rfc822Token token : tokens) {
                String address = token.getAddress();
                if (!StringUtils.isNullOrEmpty(address)) {
                    addresses.add(new Address(token.getAddress(), token.getName(), false));
                }
            }
        }
        return addresses.toArray(EMPTY_ADDRESS_ARRAY);
    }

    /**
     * Parse a comma separated list of addresses in RFC-822 format and return an
     * array of Address objects.
     *
     * @param addressList
     * @return An array of 0 or more Addresses.
     */
    public static Address[] parse(String addressList) {
        if (StringUtils.isNullOrEmpty(addressList)) {
            return EMPTY_ADDRESS_ARRAY;
        }
        List<Address> addresses = new ArrayList<Address>();
        try {
            MailboxList parsedList =  AddressBuilder.DEFAULT.parseAddressList(addressList).flatten();

            for (int i = 0, count = parsedList.size(); i < count; i++) {
                org.apache.james.mime4j.dom.address.Address address = parsedList.get(i);
                if (address instanceof Mailbox) {
                    Mailbox mailbox = (Mailbox)address;
                    addresses.add(new Address(mailbox.getLocalPart() + "@" + mailbox.getDomain(), mailbox.getName(), false));
                } else {
                    Log.e(K9.LOG_TAG, "Unknown address type from Mime4J: "
                            + address.getClass().toString());
                }
            }
        } catch (MimeException pe) {
            Log.e(K9.LOG_TAG, "MimeException in Address.parse()", pe);
            //but we do an silent failover : we just use the given string as name with empty address
            addresses.add(new Address(null, addressList, false));
        }
        return addresses.toArray(EMPTY_ADDRESS_ARRAY);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Address) {
            Address other = (Address) o;
            if (mPersonal != null && other.mPersonal != null && !mPersonal.equals(other.mPersonal)) {
                return false;
            }
            return mAddress.equals(other.mAddress);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int hash = mAddress.hashCode();
        if (mPersonal != null) {
            hash += 3 * mPersonal.hashCode();
        }
        return hash;
    }

    @Override
    public String toString() {
        if (!StringUtils.isNullOrEmpty(mPersonal)) {
            return Utility.quoteAtoms(mPersonal) + " <" + mAddress + ">";
        } else {
            return mAddress;
        }
    }

    public static String toString(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        return TextUtils.join(", ", addresses);
    }

    public String toEncodedString() {
        if (!StringUtils.isNullOrEmpty(mPersonal)) {
            return EncoderUtil.encodeAddressDisplayName(mPersonal) + " <" + mAddress + ">";
        } else {
            return mAddress;
        }
    }

    public static String toEncodedString(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            sb.append(addresses[i].toEncodedString());
            if (i < addresses.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    /**
     * Returns either the personal portion of the Address or the address portion if the personal
     * is not available.
     * @return
     */
    public CharSequence toFriendly() {
        return toFriendly((Contacts)null);
    }

    /**
     * Returns the name of the contact this email address belongs to if
     * the {@link Contacts contacts} parameter is not {@code null} and a
     * contact is found. Otherwise the personal portion of the {@link Address}
     * is returned. If that isn't available either, the email address is
     * returned.
     *
     * @param contacts
     *         A {@link Contacts} instance or {@code null}.
     * @return
     *         A "friendly" name for this {@link Address}.
     */
    public CharSequence toFriendly(final Contacts contacts) {
        if (!K9.showCorrespondentNames()) {
            return mAddress;

        } else if (contacts != null) {
            final String name = contacts.getNameForAddress(mAddress);

            // TODO: The results should probably be cached for performance reasons.

            if (name != null) {
                if (K9.changeContactNameColor()) {
                    final SpannableString coloredName = new SpannableString(name);
                    coloredName.setSpan(new ForegroundColorSpan(K9.getContactNameColor()),
                                        0,
                                        coloredName.length(),
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                       );
                    return coloredName;
                } else {
                    return name;
                }
            }
        }

        return (!StringUtils.isNullOrEmpty(mPersonal)) ? mPersonal : mAddress;
    }

    public static CharSequence toFriendly(Address[] addresses) {
        return toFriendly(addresses, null);
    }

    public static CharSequence toFriendly(Address[] addresses, Contacts contacts) {
        if (addresses == null) {
            return null;
        }

        if (addresses.length >= TOO_MANY_ADDRESSES) {
            // Don't look up contacts if the number of addresses is very high.
            contacts = null;
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            sb.append(addresses[i].toFriendly(contacts));
            if (i < addresses.length - 1) {
                sb.append(',');
            }
        }
        return sb;
    }

    /**
     * Unpacks an address list previously packed with packAddressList()
     * @param addressList Packed address list.
     * @return Unpacked list.
     */
    public static Address[] unpack(String addressList) {
        if (addressList == null) {
            return new Address[] { };
        }
        ArrayList<Address> addresses = new ArrayList<Address>();
        int length = addressList.length();
        int pairStartIndex = 0;
        int pairEndIndex = 0;
        int addressEndIndex = 0;
        while (pairStartIndex < length) {
            pairEndIndex = addressList.indexOf(",\u0000", pairStartIndex);
            if (pairEndIndex == -1) {
                pairEndIndex = length;
            }
            addressEndIndex = addressList.indexOf(";\u0000", pairStartIndex);
            String address = null;
            String personal = null;
            if (addressEndIndex == -1 || addressEndIndex > pairEndIndex) {
                address = addressList.substring(pairStartIndex, pairEndIndex);
            } else {
                address = addressList.substring(pairStartIndex, addressEndIndex);
                personal = addressList.substring(addressEndIndex + 2, pairEndIndex);
            }
            addresses.add(new Address(address, personal, false));
            pairStartIndex = pairEndIndex + 2;
        }
        return addresses.toArray(new Address[addresses.size()]);
    }

    /**
     * Packs an address list into a String that is very quick to read
     * and parse. Packed lists can be unpacked with unpackAddressList()
     * The packed list is a ",\u0000" separated list of:
     * address;\u0000personal
     * @param addresses Array of addresses to pack.
     * @return Packed addresses.
     */
    public static String pack(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0, count = addresses.length; i < count; i++) {
            Address address = addresses[i];
            sb.append(address.getAddress());
            String personal = address.getPersonal();
            if (personal != null) {
                sb.append(";\u0000");
                // Escape quotes in the address part on the way in
                personal = personal.replaceAll("\"", "\\\"");
                sb.append(personal);
            }
            if (i < count - 1) {
                sb.append(",\u0000");
            }
        }
        return sb.toString();
    }
}
