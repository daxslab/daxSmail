
package com.daxslab.mail;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Log;

import com.daxslab.mail.activity.setup.AccountSetupCheckSettings.CheckDirection;
import com.daxslab.mail.crypto.Apg;
import com.daxslab.mail.crypto.CryptoProvider;
import com.daxslab.mail.helper.Utility;
import com.daxslab.mail.mail.Address;
import com.daxslab.mail.mail.MessagingException;
import com.daxslab.mail.mail.Store;
import com.daxslab.mail.mail.Folder.FolderClass;
import com.daxslab.mail.mail.store.LocalStore;
import com.daxslab.mail.mail.store.StorageManager;
import com.daxslab.mail.mail.store.StorageManager.StorageProvider;
import com.daxslab.mail.provider.EmailProvider;
import com.daxslab.mail.provider.EmailProvider.StatsColumns;
import com.daxslab.mail.search.ConditionsTreeNode;
import com.daxslab.mail.search.LocalSearch;
import com.daxslab.mail.search.SqlQueryBuilder;
import com.daxslab.mail.search.SearchSpecification.Attribute;
import com.daxslab.mail.search.SearchSpecification.SearchCondition;
import com.daxslab.mail.search.SearchSpecification.Searchfield;
import com.daxslab.mail.security.LocalKeyStore;
import com.daxslab.mail.view.ColorChip;
import com.larswerkman.colorpicker.ColorPicker;

/**
 * Account stores all of the settings for a single account defined by the user. It is able to save
 * and delete itself given a Preferences to work with. Each account is defined by a UUID.
 */
public class Account implements BaseAccount {
    /**
     * Default value for the inbox folder (never changes for POP3 and IMAP)
     */
    public static final String INBOX = "INBOX";

    /**
     * This local folder is used to store messages to be sent.
     */
    public static final String OUTBOX = "K9MAIL_INTERNAL_OUTBOX";

    public static final String EXPUNGE_IMMEDIATELY = "EXPUNGE_IMMEDIATELY";
    public static final String EXPUNGE_MANUALLY = "EXPUNGE_MANUALLY";
    public static final String EXPUNGE_ON_POLL = "EXPUNGE_ON_POLL";

    public static final int DELETE_POLICY_NEVER = 0;
    public static final int DELETE_POLICY_7DAYS = 1;
    public static final int DELETE_POLICY_ON_DELETE = 2;
    public static final int DELETE_POLICY_MARK_AS_READ = 3;

    public static final String TYPE_WIFI = "WIFI";
    public static final String TYPE_MOBILE = "MOBILE";
    public static final String TYPE_OTHER = "OTHER";
    private static final String[] networkTypes = { TYPE_WIFI, TYPE_MOBILE, TYPE_OTHER };

    public static final MessageFormat DEFAULT_MESSAGE_FORMAT = MessageFormat.HTML;
    public static final boolean DEFAULT_MESSAGE_FORMAT_AUTO = false;
    public static final boolean DEFAULT_MESSAGE_READ_RECEIPT = false;
    public static final QuoteStyle DEFAULT_QUOTE_STYLE = QuoteStyle.PREFIX;
    public static final String DEFAULT_QUOTE_PREFIX = ">";
    public static final boolean DEFAULT_QUOTED_TEXT_SHOWN = true;
    public static final boolean DEFAULT_REPLY_AFTER_QUOTE = false;
    public static final boolean DEFAULT_STRIP_SIGNATURE = true;
    public static final int DEFAULT_REMOTE_SEARCH_NUM_RESULTS = 25;

    public static final String ACCOUNT_DESCRIPTION_KEY = "description";
    public static final String STORE_URI_KEY = "storeUri";
    public static final String TRANSPORT_URI_KEY = "transportUri";

    public static final String IDENTITY_NAME_KEY = "name";
    public static final String IDENTITY_EMAIL_KEY = "email";
    public static final String IDENTITY_DESCRIPTION_KEY = "description";

    /*
     * http://developer.android.com/design/style/color.html
     * Note: Order does matter, it's the order in which they will be picked.
     */
    public static final Integer[] PREDEFINED_COLORS = new Integer[] {
            Color.parseColor("#0099CC"),    // blue
            Color.parseColor("#669900"),    // green
            Color.parseColor("#FF8800"),    // orange
            Color.parseColor("#CC0000"),    // red
            Color.parseColor("#9933CC")     // purple
    };

    public enum SortType {
        SORT_DATE(R.string.sort_earliest_first, R.string.sort_latest_first, false),
        SORT_ARRIVAL(R.string.sort_earliest_first, R.string.sort_latest_first, false),
        SORT_SUBJECT(R.string.sort_subject_alpha, R.string.sort_subject_re_alpha, true),
        SORT_SENDER(R.string.sort_sender_alpha, R.string.sort_sender_re_alpha, true),
        SORT_UNREAD(R.string.sort_unread_first, R.string.sort_unread_last, true),
        SORT_FLAGGED(R.string.sort_flagged_first, R.string.sort_flagged_last, true),
        SORT_ATTACHMENT(R.string.sort_attach_first, R.string.sort_unattached_first, true);

        private int ascendingToast;
        private int descendingToast;
        private boolean defaultAscending;

        SortType(int ascending, int descending, boolean ndefaultAscending) {
            ascendingToast = ascending;
            descendingToast = descending;
            defaultAscending = ndefaultAscending;
        }

        public int getToast(boolean ascending) {
            return (ascending) ? ascendingToast : descendingToast;
        }

        public boolean isDefaultAscending() {
            return defaultAscending;
        }
    }

    public static final SortType DEFAULT_SORT_TYPE = SortType.SORT_DATE;
    public static final boolean DEFAULT_SORT_ASCENDING = false;


    /**
     * <pre>
     * 0 - Never (DELETE_POLICY_NEVER)
     * 1 - After 7 days (DELETE_POLICY_7DAYS)
     * 2 - When I delete from inbox (DELETE_POLICY_ON_DELETE)
     * 3 - Mark as read (DELETE_POLICY_MARK_AS_READ)
     * </pre>
     */
    private int mDeletePolicy;

    private final String mUuid;
    private String mStoreUri;

    /**
     * Storage provider ID, used to locate and manage the underlying DB/file
     * storage
     */
    private String mLocalStorageProviderId;
    private String mTransportUri;
    private String mDescription;
    private String mAlwaysBcc;
    private int mAutomaticCheckIntervalMinutes;
    private int mDisplayCount;
    private int mChipColor;
    private long mLastAutomaticCheckTime;
    private long mLatestOldMessageSeenTime;
    private boolean mNotifyNewMail;
    private FolderMode mFolderNotifyNewMailMode;
    private boolean mNotifySelfNewMail;
    private String mInboxFolderName;
    private String mDraftsFolderName;
    private String mSentFolderName;
    private String mTrashFolderName;
    private String mArchiveFolderName;
    private String mSpamFolderName;
    private String mAutoExpandFolderName;
    private FolderMode mFolderDisplayMode;
    private FolderMode mFolderSyncMode;
    private FolderMode mFolderPushMode;
    private FolderMode mFolderTargetMode;
    private int mAccountNumber;
    private boolean mPushPollOnConnect;
    private boolean mNotifySync;
    private SortType mSortType;
    private HashMap<SortType, Boolean> mSortAscending = new HashMap<SortType, Boolean>();
    private ShowPictures mShowPictures;
    private boolean mIsSignatureBeforeQuotedText;
    private String mExpungePolicy = EXPUNGE_IMMEDIATELY;
    private int mMaxPushFolders;
    private int mIdleRefreshMinutes;
    private boolean goToUnreadMessageSearch;
    private final Map<String, Boolean> compressionMap = new ConcurrentHashMap<String, Boolean>();
    private Searchable searchableFolders;
    private boolean subscribedFoldersOnly;
    private int maximumPolledMessageAge;
    private int maximumAutoDownloadMessageSize;
    // Tracks if we have sent a notification for this account for
    // current set of fetched messages
    private boolean mRingNotified;
    private MessageFormat mMessageFormat;
    private boolean mMessageFormatAuto;
    private boolean mMessageReadReceipt;
    private QuoteStyle mQuoteStyle;
    private String mQuotePrefix;
    private boolean mDefaultQuotedTextShown;
    private boolean mReplyAfterQuote;
    private boolean mStripSignature;
    private boolean mSyncRemoteDeletions;
    private String mCryptoApp;
    private boolean mCryptoAutoSignature;
    private boolean mCryptoAutoEncrypt;
    private boolean mMarkMessageAsReadOnView;
    private boolean mAlwaysShowCcBcc;
    private boolean mAllowRemoteSearch;
    private boolean mRemoteSearchFullText;
    private int mRemoteSearchNumResults;

    private CryptoProvider mCryptoProvider = null;

    private ColorChip mUnreadColorChip;
    private ColorChip mReadColorChip;

    private ColorChip mFlaggedUnreadColorChip;
    private ColorChip mFlaggedReadColorChip;
    private ColorChip mCheckmarkChip;


    /**
     * Indicates whether this account is enabled, i.e. ready for use, or not.
     *
     * <p>
     * Right now newly imported accounts are disabled if the settings file didn't contain a
     * password for the incoming and/or outgoing server.
     * </p>
     */
    private boolean mEnabled;

    /**
     * Name of the folder that was last selected for a copy or move operation.
     *
     * Note: For now this value isn't persisted. So it will be reset when
     *       K-9 Mail is restarted.
     */
    private String lastSelectedFolderName = null;

    private List<Identity> identities;

    private NotificationSetting mNotificationSetting = new NotificationSetting();

    public enum FolderMode {
        NONE, ALL, FIRST_CLASS, FIRST_AND_SECOND_CLASS, NOT_SECOND_CLASS
    }

    public enum ShowPictures {
        NEVER, ALWAYS, ONLY_FROM_CONTACTS
    }

    public enum Searchable {
        ALL, DISPLAYABLE, NONE
    }

    public enum QuoteStyle {
        PREFIX, HEADER
    }

    public enum MessageFormat {
        TEXT, HTML, AUTO
    }

    protected Account(Context context) {
        mUuid = UUID.randomUUID().toString();
        mLocalStorageProviderId = StorageManager.getInstance(K9.app).getDefaultProviderId();
        mAutomaticCheckIntervalMinutes = -1;
        mIdleRefreshMinutes = 24;
        mPushPollOnConnect = true;
        mDisplayCount = K9.DEFAULT_VISIBLE_LIMIT;
        mAccountNumber = -1;
        mNotifyNewMail = true;
        mFolderNotifyNewMailMode = FolderMode.ALL;
        mNotifySync = true;
        mNotifySelfNewMail = true;
        mFolderDisplayMode = FolderMode.NOT_SECOND_CLASS;
        mFolderSyncMode = FolderMode.FIRST_CLASS;
        mFolderPushMode = FolderMode.FIRST_CLASS;
        mFolderTargetMode = FolderMode.NOT_SECOND_CLASS;
        mSortType = DEFAULT_SORT_TYPE;
        mSortAscending.put(DEFAULT_SORT_TYPE, DEFAULT_SORT_ASCENDING);
        mShowPictures = ShowPictures.NEVER;
        mIsSignatureBeforeQuotedText = false;
        mExpungePolicy = EXPUNGE_IMMEDIATELY;
        mAutoExpandFolderName = INBOX;
        mInboxFolderName = INBOX;
        mMaxPushFolders = 10;
        mChipColor = pickColor(context);
        goToUnreadMessageSearch = false;
        subscribedFoldersOnly = false;
        maximumPolledMessageAge = -1;
        maximumAutoDownloadMessageSize = 32768;
        mMessageFormat = DEFAULT_MESSAGE_FORMAT;
        mMessageFormatAuto = DEFAULT_MESSAGE_FORMAT_AUTO;
        mMessageReadReceipt = DEFAULT_MESSAGE_READ_RECEIPT;
        mQuoteStyle = DEFAULT_QUOTE_STYLE;
        mQuotePrefix = DEFAULT_QUOTE_PREFIX;
        mDefaultQuotedTextShown = DEFAULT_QUOTED_TEXT_SHOWN;
        mReplyAfterQuote = DEFAULT_REPLY_AFTER_QUOTE;
        mStripSignature = DEFAULT_STRIP_SIGNATURE;
        mSyncRemoteDeletions = true;
        mCryptoApp = Apg.NAME;
        mCryptoAutoSignature = false;
        mCryptoAutoEncrypt = false;
        mAllowRemoteSearch = false;
        mRemoteSearchFullText = false;
        mRemoteSearchNumResults = DEFAULT_REMOTE_SEARCH_NUM_RESULTS;
        mEnabled = true;
        mMarkMessageAsReadOnView = true;
        mAlwaysShowCcBcc = false;

        searchableFolders = Searchable.ALL;

        identities = new ArrayList<Identity>();

        Identity identity = new Identity();
        identity.setSignatureUse(true);
        identity.setSignature(context.getString(R.string.default_signature));
        identity.setDescription(context.getString(R.string.default_identity_description));
        identities.add(identity);

        mNotificationSetting = new NotificationSetting();
        mNotificationSetting.setVibrate(false);
        mNotificationSetting.setVibratePattern(0);
        mNotificationSetting.setVibrateTimes(5);
        mNotificationSetting.setRing(true);
        mNotificationSetting.setRingtone("content://settings/system/notification_sound");
        mNotificationSetting.setLedColor(mChipColor);

        cacheChips();
    }

    /*
     * Pick a nice Android guidelines color if we haven't used them all yet.
     */
    private int pickColor(Context context) {
        Account[] accounts = Preferences.getPreferences(context).getAccounts();

        List<Integer> availableColors = new ArrayList<Integer>(PREDEFINED_COLORS.length);
        Collections.addAll(availableColors, PREDEFINED_COLORS);

        for (Account account : accounts) {
            Integer color = account.getChipColor();
            if (availableColors.contains(color)) {
                availableColors.remove(color);
                if (availableColors.isEmpty()) {
                    break;
                }
            }
        }

        return (availableColors.isEmpty()) ? ColorPicker.getRandomColor() : availableColors.get(0);
    }

    protected Account(Preferences preferences, String uuid) {
        this.mUuid = uuid;
        loadAccount(preferences);
    }

    /**
     * Load stored settings for this account.
     */
    private synchronized void loadAccount(Preferences preferences) {

        SharedPreferences prefs = preferences.getPreferences();

        mStoreUri = Utility.base64Decode(prefs.getString(mUuid + ".storeUri", null));
        mLocalStorageProviderId = prefs.getString(mUuid + ".localStorageProvider", StorageManager.getInstance(K9.app).getDefaultProviderId());
        mTransportUri = Utility.base64Decode(prefs.getString(mUuid + ".transportUri", null));
        mDescription = prefs.getString(mUuid + ".description", null);
        mAlwaysBcc = prefs.getString(mUuid + ".alwaysBcc", mAlwaysBcc);
        mAutomaticCheckIntervalMinutes = prefs.getInt(mUuid + ".automaticCheckIntervalMinutes", -1);
        mIdleRefreshMinutes = prefs.getInt(mUuid + ".idleRefreshMinutes", 24);
        mPushPollOnConnect = prefs.getBoolean(mUuid + ".pushPollOnConnect", true);
        mDisplayCount = prefs.getInt(mUuid + ".displayCount", K9.DEFAULT_VISIBLE_LIMIT);
        if (mDisplayCount < 0) {
            mDisplayCount = K9.DEFAULT_VISIBLE_LIMIT;
        }
        mLastAutomaticCheckTime = prefs.getLong(mUuid + ".lastAutomaticCheckTime", 0);
        mLatestOldMessageSeenTime = prefs.getLong(mUuid + ".latestOldMessageSeenTime", 0);
        mNotifyNewMail = prefs.getBoolean(mUuid + ".notifyNewMail", false);
        try {
            mFolderNotifyNewMailMode = FolderMode.valueOf(prefs.getString(mUuid  + ".folderNotifyNewMailMode",
                                                 FolderMode.ALL.name()));
        } catch (Exception e) {
            mFolderNotifyNewMailMode = FolderMode.ALL;
        }
        mNotifySelfNewMail = prefs.getBoolean(mUuid + ".notifySelfNewMail", true);
        mNotifySync = prefs.getBoolean(mUuid + ".notifyMailCheck", false);
        mDeletePolicy = prefs.getInt(mUuid + ".deletePolicy", 0);
        mInboxFolderName = prefs.getString(mUuid  + ".inboxFolderName", INBOX);
        mDraftsFolderName = prefs.getString(mUuid  + ".draftsFolderName", "Drafts");
        mSentFolderName = prefs.getString(mUuid  + ".sentFolderName", "Sent");
        mTrashFolderName = prefs.getString(mUuid  + ".trashFolderName", "Trash");
        mArchiveFolderName = prefs.getString(mUuid  + ".archiveFolderName", "Archive");
        mSpamFolderName = prefs.getString(mUuid  + ".spamFolderName", "Spam");
        mExpungePolicy = prefs.getString(mUuid  + ".expungePolicy", EXPUNGE_IMMEDIATELY);
        mSyncRemoteDeletions = prefs.getBoolean(mUuid  + ".syncRemoteDeletions", true);

        mMaxPushFolders = prefs.getInt(mUuid + ".maxPushFolders", 10);
        goToUnreadMessageSearch = prefs.getBoolean(mUuid + ".goToUnreadMessageSearch", false);
        subscribedFoldersOnly = prefs.getBoolean(mUuid + ".subscribedFoldersOnly", false);
        maximumPolledMessageAge = prefs.getInt(mUuid + ".maximumPolledMessageAge", -1);
        maximumAutoDownloadMessageSize = prefs.getInt(mUuid + ".maximumAutoDownloadMessageSize", 32768);
        mMessageFormat = MessageFormat.valueOf(prefs.getString(mUuid + ".messageFormat", DEFAULT_MESSAGE_FORMAT.name()));
        mMessageFormatAuto = prefs.getBoolean(mUuid + ".messageFormatAuto", DEFAULT_MESSAGE_FORMAT_AUTO);
        if (mMessageFormatAuto && mMessageFormat == MessageFormat.TEXT) {
            mMessageFormat = MessageFormat.AUTO;
        }
        mMessageReadReceipt = prefs.getBoolean(mUuid + ".messageReadReceipt", DEFAULT_MESSAGE_READ_RECEIPT);
        mQuoteStyle = QuoteStyle.valueOf(prefs.getString(mUuid + ".quoteStyle", DEFAULT_QUOTE_STYLE.name()));
        mQuotePrefix = prefs.getString(mUuid + ".quotePrefix", DEFAULT_QUOTE_PREFIX);
        mDefaultQuotedTextShown = prefs.getBoolean(mUuid + ".defaultQuotedTextShown", DEFAULT_QUOTED_TEXT_SHOWN);
        mReplyAfterQuote = prefs.getBoolean(mUuid + ".replyAfterQuote", DEFAULT_REPLY_AFTER_QUOTE);
        mStripSignature = prefs.getBoolean(mUuid + ".stripSignature", DEFAULT_STRIP_SIGNATURE);
        for (String type : networkTypes) {
            Boolean useCompression = prefs.getBoolean(mUuid + ".useCompression." + type,
                                     true);
            compressionMap.put(type, useCompression);
        }

        mAutoExpandFolderName = prefs.getString(mUuid  + ".autoExpandFolderName", INBOX);

        mAccountNumber = prefs.getInt(mUuid + ".accountNumber", 0);

        mChipColor = prefs.getInt(mUuid + ".chipColor", ColorPicker.getRandomColor());

        try {
            mSortType = SortType.valueOf(prefs.getString(mUuid + ".sortTypeEnum",
                                                 SortType.SORT_DATE.name()));
        } catch (Exception e) {
            mSortType = SortType.SORT_DATE;
        }

        mSortAscending.put(mSortType, prefs.getBoolean(mUuid + ".sortAscending", false));

        try {
            mShowPictures = ShowPictures.valueOf(prefs.getString(mUuid + ".showPicturesEnum",
                                                 ShowPictures.NEVER.name()));
        } catch (Exception e) {
            mShowPictures = ShowPictures.NEVER;
        }

        mNotificationSetting.setVibrate(prefs.getBoolean(mUuid + ".vibrate", false));
        mNotificationSetting.setVibratePattern(prefs.getInt(mUuid + ".vibratePattern", 0));
        mNotificationSetting.setVibrateTimes(prefs.getInt(mUuid + ".vibrateTimes", 5));
        mNotificationSetting.setRing(prefs.getBoolean(mUuid + ".ring", true));
        mNotificationSetting.setRingtone(prefs.getString(mUuid  + ".ringtone",
                                         "content://settings/system/notification_sound"));
        mNotificationSetting.setLed(prefs.getBoolean(mUuid + ".led", true));
        mNotificationSetting.setLedColor(prefs.getInt(mUuid + ".ledColor", mChipColor));

        try {
            mFolderDisplayMode = FolderMode.valueOf(prefs.getString(mUuid  + ".folderDisplayMode",
                                                    FolderMode.NOT_SECOND_CLASS.name()));
        } catch (Exception e) {
            mFolderDisplayMode = FolderMode.NOT_SECOND_CLASS;
        }

        try {
            mFolderSyncMode = FolderMode.valueOf(prefs.getString(mUuid  + ".folderSyncMode",
                                                 FolderMode.FIRST_CLASS.name()));
        } catch (Exception e) {
            mFolderSyncMode = FolderMode.FIRST_CLASS;
        }

        try {
            mFolderPushMode = FolderMode.valueOf(prefs.getString(mUuid  + ".folderPushMode",
                                                 FolderMode.FIRST_CLASS.name()));
        } catch (Exception e) {
            mFolderPushMode = FolderMode.FIRST_CLASS;
        }

        try {
            mFolderTargetMode = FolderMode.valueOf(prefs.getString(mUuid  + ".folderTargetMode",
                                                   FolderMode.NOT_SECOND_CLASS.name()));
        } catch (Exception e) {
            mFolderTargetMode = FolderMode.NOT_SECOND_CLASS;
        }

        try {
            searchableFolders = Searchable.valueOf(prefs.getString(mUuid  + ".searchableFolders",
                                                   Searchable.ALL.name()));
        } catch (Exception e) {
            searchableFolders = Searchable.ALL;
        }

        mIsSignatureBeforeQuotedText = prefs.getBoolean(mUuid  + ".signatureBeforeQuotedText", false);
        identities = loadIdentities(prefs);

        mCryptoApp = prefs.getString(mUuid + ".cryptoApp", Apg.NAME);
        mCryptoAutoSignature = prefs.getBoolean(mUuid + ".cryptoAutoSignature", false);
        mCryptoAutoEncrypt = prefs.getBoolean(mUuid + ".cryptoAutoEncrypt", false);
        mAllowRemoteSearch = prefs.getBoolean(mUuid + ".allowRemoteSearch", false);
        mRemoteSearchFullText = prefs.getBoolean(mUuid + ".remoteSearchFullText", false);
        mRemoteSearchNumResults = prefs.getInt(mUuid + ".remoteSearchNumResults", DEFAULT_REMOTE_SEARCH_NUM_RESULTS);

        mEnabled = prefs.getBoolean(mUuid + ".enabled", true);
        mMarkMessageAsReadOnView = prefs.getBoolean(mUuid + ".markMessageAsReadOnView", true);
        mAlwaysShowCcBcc = prefs.getBoolean(mUuid + ".alwaysShowCcBcc", false);

        cacheChips();

        // Use email address as account description if necessary
        if (mDescription == null) {
            mDescription = getEmail();
        }
    }

    protected synchronized void delete(Preferences preferences) {
        // Get the list of account UUIDs
        String[] uuids = preferences.getPreferences().getString("accountUuids", "").split(",");

        // Create a list of all account UUIDs excluding this account
        List<String> newUuids = new ArrayList<String>(uuids.length);
        for (String uuid : uuids) {
            if (!uuid.equals(mUuid)) {
                newUuids.add(uuid);
            }
        }

        SharedPreferences.Editor editor = preferences.getPreferences().edit();

        // Only change the 'accountUuids' value if this account's UUID was listed before
        if (newUuids.size() < uuids.length) {
            String accountUuids = Utility.combine(newUuids.toArray(), ',');
            editor.putString("accountUuids", accountUuids);
        }

        editor.remove(mUuid + ".storeUri");
        editor.remove(mUuid + ".transportUri");
        editor.remove(mUuid + ".description");
        editor.remove(mUuid + ".name");
        editor.remove(mUuid + ".email");
        editor.remove(mUuid + ".alwaysBcc");
        editor.remove(mUuid + ".automaticCheckIntervalMinutes");
        editor.remove(mUuid + ".pushPollOnConnect");
        editor.remove(mUuid + ".idleRefreshMinutes");
        editor.remove(mUuid + ".lastAutomaticCheckTime");
        editor.remove(mUuid + ".latestOldMessageSeenTime");
        editor.remove(mUuid + ".notifyNewMail");
        editor.remove(mUuid + ".notifySelfNewMail");
        editor.remove(mUuid + ".deletePolicy");
        editor.remove(mUuid + ".draftsFolderName");
        editor.remove(mUuid + ".sentFolderName");
        editor.remove(mUuid + ".trashFolderName");
        editor.remove(mUuid + ".archiveFolderName");
        editor.remove(mUuid + ".spamFolderName");
        editor.remove(mUuid + ".autoExpandFolderName");
        editor.remove(mUuid + ".accountNumber");
        editor.remove(mUuid + ".vibrate");
        editor.remove(mUuid + ".vibratePattern");
        editor.remove(mUuid + ".vibrateTimes");
        editor.remove(mUuid + ".ring");
        editor.remove(mUuid + ".ringtone");
        editor.remove(mUuid + ".folderDisplayMode");
        editor.remove(mUuid + ".folderSyncMode");
        editor.remove(mUuid + ".folderPushMode");
        editor.remove(mUuid + ".folderTargetMode");
        editor.remove(mUuid + ".signatureBeforeQuotedText");
        editor.remove(mUuid + ".expungePolicy");
        editor.remove(mUuid + ".syncRemoteDeletions");
        editor.remove(mUuid + ".maxPushFolders");
        editor.remove(mUuid + ".searchableFolders");
        editor.remove(mUuid + ".chipColor");
        editor.remove(mUuid + ".led");
        editor.remove(mUuid + ".ledColor");
        editor.remove(mUuid + ".goToUnreadMessageSearch");
        editor.remove(mUuid + ".subscribedFoldersOnly");
        editor.remove(mUuid + ".maximumPolledMessageAge");
        editor.remove(mUuid + ".maximumAutoDownloadMessageSize");
        editor.remove(mUuid + ".messageFormatAuto");
        editor.remove(mUuid + ".quoteStyle");
        editor.remove(mUuid + ".quotePrefix");
        editor.remove(mUuid + ".sortTypeEnum");
        editor.remove(mUuid + ".sortAscending");
        editor.remove(mUuid + ".showPicturesEnum");
        editor.remove(mUuid + ".replyAfterQuote");
        editor.remove(mUuid + ".stripSignature");
        editor.remove(mUuid + ".cryptoApp");
        editor.remove(mUuid + ".cryptoAutoSignature");
        editor.remove(mUuid + ".cryptoAutoEncrypt");
        editor.remove(mUuid + ".enabled");
        editor.remove(mUuid + ".markMessageAsReadOnView");
        editor.remove(mUuid + ".alwaysShowCcBcc");
        editor.remove(mUuid + ".allowRemoteSearch");
        editor.remove(mUuid + ".remoteSearchFullText");
        editor.remove(mUuid + ".remoteSearchNumResults");
        editor.remove(mUuid + ".defaultQuotedTextShown");
        editor.remove(mUuid + ".displayCount");
        editor.remove(mUuid + ".inboxFolderName");
        editor.remove(mUuid + ".localStorageProvider");
        editor.remove(mUuid + ".messageFormat");
        editor.remove(mUuid + ".messageReadReceipt");
        editor.remove(mUuid + ".notifyMailCheck");
        for (String type : networkTypes) {
            editor.remove(mUuid + ".useCompression." + type);
        }
        deleteIdentities(preferences.getPreferences(), editor);
        // TODO: Remove preference settings that may exist for individual
        // folders in the account.
        editor.commit();
    }

    public static int findNewAccountNumber(List<Integer> accountNumbers) {
        int newAccountNumber = -1;
        Collections.sort(accountNumbers);
        for (int accountNumber : accountNumbers) {
            if (accountNumber > newAccountNumber + 1) {
                break;
            }
            newAccountNumber = accountNumber;
        }
        newAccountNumber++;
        return newAccountNumber;
    }

    public static List<Integer> getExistingAccountNumbers(Preferences preferences) {
        Account[] accounts = preferences.getAccounts();
        List<Integer> accountNumbers = new ArrayList<Integer>(accounts.length);
        for (Account a : accounts) {
            accountNumbers.add(a.getAccountNumber());
        }
        return accountNumbers;
    }
    public static int generateAccountNumber(Preferences preferences) {
        List<Integer> accountNumbers = getExistingAccountNumbers(preferences);
        return findNewAccountNumber(accountNumbers);
    }

    public void move(Preferences preferences, boolean moveUp) {
        String[] uuids = preferences.getPreferences().getString("accountUuids", "").split(",");
        SharedPreferences.Editor editor = preferences.getPreferences().edit();
        String[] newUuids = new String[uuids.length];
        if (moveUp) {
            for (int i = 0; i < uuids.length; i++) {
                if (i > 0 && uuids[i].equals(mUuid)) {
                    newUuids[i] = newUuids[i-1];
                    newUuids[i-1] = mUuid;
                }
                else {
                    newUuids[i] = uuids[i];
                }
            }
        }
        else {
            for (int i = uuids.length - 1; i >= 0; i--) {
                if (i < uuids.length - 1 && uuids[i].equals(mUuid)) {
                    newUuids[i] = newUuids[i+1];
                    newUuids[i+1] = mUuid;
                }
                else {
                    newUuids[i] = uuids[i];
                }
            }
        }
        String accountUuids = Utility.combine(newUuids, ',');
        editor.putString("accountUuids", accountUuids);
        editor.commit();
        preferences.loadAccounts();
    }

    public synchronized void save(Preferences preferences) {
        SharedPreferences.Editor editor = preferences.getPreferences().edit();

        if (!preferences.getPreferences().getString("accountUuids", "").contains(mUuid)) {
            /*
             * When the account is first created we assign it a unique account number. The
             * account number will be unique to that account for the lifetime of the account.
             * So, we get all the existing account numbers, sort them ascending, loop through
             * the list and check if the number is greater than 1 + the previous number. If so
             * we use the previous number + 1 as the account number. This refills gaps.
             * mAccountNumber starts as -1 on a newly created account. It must be -1 for this
             * algorithm to work.
             *
             * I bet there is a much smarter way to do this. Anyone like to suggest it?
             */
            Account[] accounts = preferences.getAccounts();
            int[] accountNumbers = new int[accounts.length];
            for (int i = 0; i < accounts.length; i++) {
                accountNumbers[i] = accounts[i].getAccountNumber();
            }
            Arrays.sort(accountNumbers);
            for (int accountNumber : accountNumbers) {
                if (accountNumber > mAccountNumber + 1) {
                    break;
                }
                mAccountNumber = accountNumber;
            }
            mAccountNumber++;

            String accountUuids = preferences.getPreferences().getString("accountUuids", "");
            accountUuids += (accountUuids.length() != 0 ? "," : "") + mUuid;
            editor.putString("accountUuids", accountUuids);
        }

        editor.putString(mUuid + ".storeUri", Utility.base64Encode(mStoreUri));
        editor.putString(mUuid + ".localStorageProvider", mLocalStorageProviderId);
        editor.putString(mUuid + ".transportUri", Utility.base64Encode(mTransportUri));
        editor.putString(mUuid + ".description", mDescription);
        editor.putString(mUuid + ".alwaysBcc", mAlwaysBcc);
        editor.putInt(mUuid + ".automaticCheckIntervalMinutes", mAutomaticCheckIntervalMinutes);
        editor.putInt(mUuid + ".idleRefreshMinutes", mIdleRefreshMinutes);
        editor.putBoolean(mUuid + ".pushPollOnConnect", mPushPollOnConnect);
        editor.putInt(mUuid + ".displayCount", mDisplayCount);
        editor.putLong(mUuid + ".lastAutomaticCheckTime", mLastAutomaticCheckTime);
        editor.putLong(mUuid + ".latestOldMessageSeenTime", mLatestOldMessageSeenTime);
        editor.putBoolean(mUuid + ".notifyNewMail", mNotifyNewMail);
        editor.putString(mUuid + ".folderNotifyNewMailMode", mFolderNotifyNewMailMode.name());
        editor.putBoolean(mUuid + ".notifySelfNewMail", mNotifySelfNewMail);
        editor.putBoolean(mUuid + ".notifyMailCheck", mNotifySync);
        editor.putInt(mUuid + ".deletePolicy", mDeletePolicy);
        editor.putString(mUuid + ".inboxFolderName", mInboxFolderName);
        editor.putString(mUuid + ".draftsFolderName", mDraftsFolderName);
        editor.putString(mUuid + ".sentFolderName", mSentFolderName);
        editor.putString(mUuid + ".trashFolderName", mTrashFolderName);
        editor.putString(mUuid + ".archiveFolderName", mArchiveFolderName);
        editor.putString(mUuid + ".spamFolderName", mSpamFolderName);
        editor.putString(mUuid + ".autoExpandFolderName", mAutoExpandFolderName);
        editor.putInt(mUuid + ".accountNumber", mAccountNumber);
        editor.putString(mUuid + ".sortTypeEnum", mSortType.name());
        editor.putBoolean(mUuid + ".sortAscending", mSortAscending.get(mSortType));
        editor.putString(mUuid + ".showPicturesEnum", mShowPictures.name());
        editor.putString(mUuid + ".folderDisplayMode", mFolderDisplayMode.name());
        editor.putString(mUuid + ".folderSyncMode", mFolderSyncMode.name());
        editor.putString(mUuid + ".folderPushMode", mFolderPushMode.name());
        editor.putString(mUuid + ".folderTargetMode", mFolderTargetMode.name());
        editor.putBoolean(mUuid + ".signatureBeforeQuotedText", this.mIsSignatureBeforeQuotedText);
        editor.putString(mUuid + ".expungePolicy", mExpungePolicy);
        editor.putBoolean(mUuid + ".syncRemoteDeletions", mSyncRemoteDeletions);
        editor.putInt(mUuid + ".maxPushFolders", mMaxPushFolders);
        editor.putString(mUuid + ".searchableFolders", searchableFolders.name());
        editor.putInt(mUuid + ".chipColor", mChipColor);
        editor.putBoolean(mUuid + ".goToUnreadMessageSearch", goToUnreadMessageSearch);
        editor.putBoolean(mUuid + ".subscribedFoldersOnly", subscribedFoldersOnly);
        editor.putInt(mUuid + ".maximumPolledMessageAge", maximumPolledMessageAge);
        editor.putInt(mUuid + ".maximumAutoDownloadMessageSize", maximumAutoDownloadMessageSize);
        if (MessageFormat.AUTO.equals(mMessageFormat)) {
            // saving MessageFormat.AUTO as is to the database will cause downgrades to crash on
            // startup, so we save as MessageFormat.TEXT instead with a separate flag for auto.
            editor.putString(mUuid + ".messageFormat", Account.MessageFormat.TEXT.name());
            mMessageFormatAuto = true;
        } else {
            editor.putString(mUuid + ".messageFormat", mMessageFormat.name());
            mMessageFormatAuto = false;
        }
        editor.putBoolean(mUuid + ".messageFormatAuto", mMessageFormatAuto);
        editor.putBoolean(mUuid + ".messageReadReceipt", mMessageReadReceipt);
        editor.putString(mUuid + ".quoteStyle", mQuoteStyle.name());
        editor.putString(mUuid + ".quotePrefix", mQuotePrefix);
        editor.putBoolean(mUuid + ".defaultQuotedTextShown", mDefaultQuotedTextShown);
        editor.putBoolean(mUuid + ".replyAfterQuote", mReplyAfterQuote);
        editor.putBoolean(mUuid + ".stripSignature", mStripSignature);
        editor.putString(mUuid + ".cryptoApp", mCryptoApp);
        editor.putBoolean(mUuid + ".cryptoAutoSignature", mCryptoAutoSignature);
        editor.putBoolean(mUuid + ".cryptoAutoEncrypt", mCryptoAutoEncrypt);
        editor.putBoolean(mUuid + ".allowRemoteSearch", mAllowRemoteSearch);
        editor.putBoolean(mUuid + ".remoteSearchFullText", mRemoteSearchFullText);
        editor.putInt(mUuid + ".remoteSearchNumResults", mRemoteSearchNumResults);
        editor.putBoolean(mUuid + ".enabled", mEnabled);
        editor.putBoolean(mUuid + ".markMessageAsReadOnView", mMarkMessageAsReadOnView);
        editor.putBoolean(mUuid + ".alwaysShowCcBcc", mAlwaysShowCcBcc);

        editor.putBoolean(mUuid + ".vibrate", mNotificationSetting.shouldVibrate());
        editor.putInt(mUuid + ".vibratePattern", mNotificationSetting.getVibratePattern());
        editor.putInt(mUuid + ".vibrateTimes", mNotificationSetting.getVibrateTimes());
        editor.putBoolean(mUuid + ".ring", mNotificationSetting.shouldRing());
        editor.putString(mUuid + ".ringtone", mNotificationSetting.getRingtone());
        editor.putBoolean(mUuid + ".led", mNotificationSetting.isLed());
        editor.putInt(mUuid + ".ledColor", mNotificationSetting.getLedColor());

        for (String type : networkTypes) {
            Boolean useCompression = compressionMap.get(type);
            if (useCompression != null) {
                editor.putBoolean(mUuid + ".useCompression." + type, useCompression);
            }
        }
        saveIdentities(preferences.getPreferences(), editor);

        editor.commit();

    }

    public void resetVisibleLimits() {
        try {
            getLocalStore().resetVisibleLimits(getDisplayCount());
        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "Unable to reset visible limits", e);
        }

    }

    /**
     * @param context
     * @return <code>null</code> if not available
     * @throws MessagingException
     * @see {@link #isAvailable(Context)}
     */
    public AccountStats getStats(Context context) throws MessagingException {
        if (!isAvailable(context)) {
            return null;
        }

        AccountStats stats = new AccountStats();

        ContentResolver cr = context.getContentResolver();

        Uri uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI,
                "account/" + getUuid() + "/stats");

        String[] projection = {
                StatsColumns.UNREAD_COUNT,
                StatsColumns.FLAGGED_COUNT
        };

        // Create LocalSearch instance to exclude special folders (Trash, Drafts, Spam, Outbox,
        // Sent) and limit the search to displayable folders.
        LocalSearch search = new LocalSearch();
        excludeSpecialFolders(search);
        limitToDisplayableFolders(search);

        // Use the LocalSearch instance to create a WHERE clause to query the content provider
        StringBuilder query = new StringBuilder();
        List<String> queryArgs = new ArrayList<String>();
        ConditionsTreeNode conditions = search.getConditions();
        SqlQueryBuilder.buildWhereClause(this, conditions, query, queryArgs);

        String selection = query.toString();
        String[] selectionArgs = queryArgs.toArray(new String[0]);

        Cursor cursor = cr.query(uri, projection, selection, selectionArgs, null);
        try {
            if (cursor.moveToFirst()) {
                stats.unreadMessageCount = cursor.getInt(0);
                stats.flaggedMessageCount = cursor.getInt(1);
            }
        } finally {
            cursor.close();
        }

        LocalStore localStore = getLocalStore();
        if (K9.measureAccounts()) {
            stats.size = localStore.getSize();
        }

        return stats;
    }


    public synchronized void setChipColor(int color) {
        mChipColor = color;
        cacheChips();
    }

    public synchronized void cacheChips() {
        mReadColorChip = new ColorChip(mChipColor, true, ColorChip.CIRCULAR);
        mUnreadColorChip = new ColorChip(mChipColor, false, ColorChip.CIRCULAR);
        mFlaggedReadColorChip = new ColorChip(mChipColor, true, ColorChip.STAR);
        mFlaggedUnreadColorChip = new ColorChip(mChipColor, false, ColorChip.STAR);
        mCheckmarkChip = new ColorChip(mChipColor, true, ColorChip.CHECKMARK);
    }

    public ColorChip getCheckmarkChip() {
        return mCheckmarkChip;
    }

    public synchronized int getChipColor() {
        return mChipColor;
    }


    public ColorChip generateColorChip(boolean messageRead, boolean toMe, boolean ccMe,
            boolean fromMe, boolean messageFlagged) {
        ColorChip chip;

        if (messageRead) {
            if (messageFlagged) {
                chip = mFlaggedReadColorChip;
            } else {
                chip = mReadColorChip;
            }
        } else {
            if (messageFlagged) {
                chip = mFlaggedUnreadColorChip;
            } else {
                chip = mUnreadColorChip;
            }
        }

        return chip;
    }

    public ColorChip generateColorChip() {
        return new ColorChip(mChipColor, false, ColorChip.CIRCULAR);
    }

    @Override
    public String getUuid() {
        return mUuid;
    }

    public Uri getContentUri() {
        return Uri.parse("content://accounts/" + getUuid());
    }

    public synchronized String getStoreUri() {
        return mStoreUri;
    }

    public synchronized void setStoreUri(String storeUri) {
        this.mStoreUri = storeUri;
    }

    public synchronized String getTransportUri() {
        return mTransportUri;
    }

    public synchronized void setTransportUri(String transportUri) {
        this.mTransportUri = transportUri;
    }

    @Override
    public synchronized String getDescription() {
        return mDescription;
    }

    @Override
    public synchronized void setDescription(String description) {
        this.mDescription = description;
    }

    public synchronized String getName() {
        return identities.get(0).getName();
    }

    public synchronized void setName(String name) {
        identities.get(0).setName(name);
    }

    public synchronized boolean getSignatureUse() {
        return identities.get(0).getSignatureUse();
    }

    public synchronized void setSignatureUse(boolean signatureUse) {
        identities.get(0).setSignatureUse(signatureUse);
    }

    public synchronized String getSignature() {
        return identities.get(0).getSignature();
    }

    public synchronized void setSignature(String signature) {
        identities.get(0).setSignature(signature);
    }

    @Override
    public synchronized String getEmail() {
        return identities.get(0).getEmail();
    }

    @Override
    public synchronized void setEmail(String email) {
        identities.get(0).setEmail(email);
    }

    public synchronized String getAlwaysBcc() {
        return mAlwaysBcc;
    }

    public synchronized void setAlwaysBcc(String alwaysBcc) {
        this.mAlwaysBcc = alwaysBcc;
    }

    /* Have we sent a new mail notification on this account */
    public boolean isRingNotified() {
        return mRingNotified;
    }

    public void setRingNotified(boolean ringNotified) {
        mRingNotified = ringNotified;
    }

    public String getLocalStorageProviderId() {
        return mLocalStorageProviderId;
    }

    public void setLocalStorageProviderId(String id) {

        if (!mLocalStorageProviderId.equals(id)) {

            boolean successful = false;
            try {
                switchLocalStorage(id);
                successful = true;
            } catch (MessagingException e) {
                Log.e(K9.LOG_TAG, "Switching local storage provider from " +
                      mLocalStorageProviderId + " to " + id + " failed.", e);
            } finally {
                // if migration to/from SD-card failed once, it will fail again.
                if (!successful) {
                    return;
                }
            }

            mLocalStorageProviderId = id;
        }

    }

    /**
     * Returns -1 for never.
     */
    public synchronized int getAutomaticCheckIntervalMinutes() {
        return mAutomaticCheckIntervalMinutes;
    }

    /**
     * @param automaticCheckIntervalMinutes or -1 for never.
     */
    public synchronized boolean setAutomaticCheckIntervalMinutes(int automaticCheckIntervalMinutes) {
        int oldInterval = this.mAutomaticCheckIntervalMinutes;
        this.mAutomaticCheckIntervalMinutes = automaticCheckIntervalMinutes;

        return (oldInterval != automaticCheckIntervalMinutes);
    }

    public synchronized int getDisplayCount() {
        return mDisplayCount;
    }

    public synchronized void setDisplayCount(int displayCount) {
        if (displayCount != -1) {
            this.mDisplayCount = displayCount;
        } else {
            this.mDisplayCount = K9.DEFAULT_VISIBLE_LIMIT;
        }
        resetVisibleLimits();
    }

    public synchronized long getLastAutomaticCheckTime() {
        return mLastAutomaticCheckTime;
    }

    public synchronized void setLastAutomaticCheckTime(long lastAutomaticCheckTime) {
        this.mLastAutomaticCheckTime = lastAutomaticCheckTime;
    }

    public synchronized long getLatestOldMessageSeenTime() {
        return mLatestOldMessageSeenTime;
    }

    public synchronized void setLatestOldMessageSeenTime(long latestOldMessageSeenTime) {
        this.mLatestOldMessageSeenTime = latestOldMessageSeenTime;
    }

    public synchronized boolean isNotifyNewMail() {
        return mNotifyNewMail;
    }

    public synchronized void setNotifyNewMail(boolean notifyNewMail) {
        this.mNotifyNewMail = notifyNewMail;
    }

    public synchronized FolderMode getFolderNotifyNewMailMode() {
        return mFolderNotifyNewMailMode;
    }

    public synchronized void setFolderNotifyNewMailMode(FolderMode folderNotifyNewMailMode) {
        this.mFolderNotifyNewMailMode = folderNotifyNewMailMode;
    }

    public synchronized int getDeletePolicy() {
        return mDeletePolicy;
    }

    public synchronized void setDeletePolicy(int deletePolicy) {
        this.mDeletePolicy = deletePolicy;
    }

    public boolean isSpecialFolder(String folderName) {
        return (folderName != null && (folderName.equalsIgnoreCase(getInboxFolderName()) ||
                folderName.equals(getTrashFolderName()) ||
                folderName.equals(getDraftsFolderName()) ||
                folderName.equals(getArchiveFolderName()) ||
                folderName.equals(getSpamFolderName()) ||
                folderName.equals(getOutboxFolderName()) ||
                folderName.equals(getSentFolderName()) ||
                folderName.equals(getErrorFolderName())));
    }

    public synchronized String getDraftsFolderName() {
        return mDraftsFolderName;
    }

    public synchronized void setDraftsFolderName(String draftsFolderName) {
        mDraftsFolderName = draftsFolderName;
    }

    /**
     * Checks if this account has a drafts folder set.
     * @return true if account has a drafts folder set.
     */
    public synchronized boolean hasDraftsFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mDraftsFolderName);
    }

    public synchronized String getSentFolderName() {
        return mSentFolderName;
    }

    public synchronized String getErrorFolderName() {
        return K9.ERROR_FOLDER_NAME;
    }

    public synchronized void setSentFolderName(String sentFolderName) {
        mSentFolderName = sentFolderName;
    }

    /**
     * Checks if this account has a sent folder set.
     * @return true if account has a sent folder set.
     */
    public synchronized boolean hasSentFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mSentFolderName);
    }


    public synchronized String getTrashFolderName() {
        return mTrashFolderName;
    }

    public synchronized void setTrashFolderName(String trashFolderName) {
        mTrashFolderName = trashFolderName;
    }

    /**
     * Checks if this account has a trash folder set.
     * @return true if account has a trash folder set.
     */
    public synchronized boolean hasTrashFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mTrashFolderName);
    }

    public synchronized String getArchiveFolderName() {
        return mArchiveFolderName;
    }

    public synchronized void setArchiveFolderName(String archiveFolderName) {
        mArchiveFolderName = archiveFolderName;
    }

    /**
     * Checks if this account has an archive folder set.
     * @return true if account has an archive folder set.
     */
    public synchronized boolean hasArchiveFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mArchiveFolderName);
    }

    public synchronized String getSpamFolderName() {
        return mSpamFolderName;
    }

    public synchronized void setSpamFolderName(String spamFolderName) {
        mSpamFolderName = spamFolderName;
    }

    /**
     * Checks if this account has a spam folder set.
     * @return true if account has a spam folder set.
     */
    public synchronized boolean hasSpamFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mSpamFolderName);
    }

    public synchronized String getOutboxFolderName() {
        return OUTBOX;
    }

    public synchronized String getAutoExpandFolderName() {
        return mAutoExpandFolderName;
    }

    public synchronized void setAutoExpandFolderName(String autoExpandFolderName) {
        mAutoExpandFolderName = autoExpandFolderName;
    }

    public synchronized int getAccountNumber() {
        return mAccountNumber;
    }

    public synchronized FolderMode getFolderDisplayMode() {
        return mFolderDisplayMode;
    }

    public synchronized boolean setFolderDisplayMode(FolderMode displayMode) {
        FolderMode oldDisplayMode = mFolderDisplayMode;
        mFolderDisplayMode = displayMode;
        return oldDisplayMode != displayMode;
    }

    public synchronized FolderMode getFolderSyncMode() {
        return mFolderSyncMode;
    }

    public synchronized boolean setFolderSyncMode(FolderMode syncMode) {
        FolderMode oldSyncMode = mFolderSyncMode;
        mFolderSyncMode = syncMode;

        if (syncMode == FolderMode.NONE && oldSyncMode != FolderMode.NONE) {
            return true;
        }
        if (syncMode != FolderMode.NONE && oldSyncMode == FolderMode.NONE) {
            return true;
        }
        return false;
    }

    public synchronized FolderMode getFolderPushMode() {
        return mFolderPushMode;
    }

    public synchronized boolean setFolderPushMode(FolderMode pushMode) {
        FolderMode oldPushMode = mFolderPushMode;

        mFolderPushMode = pushMode;
        return pushMode != oldPushMode;
    }

    public synchronized boolean isShowOngoing() {
        return mNotifySync;
    }

    public synchronized void setShowOngoing(boolean showOngoing) {
        this.mNotifySync = showOngoing;
    }

    public synchronized SortType getSortType() {
        return mSortType;
    }

    public synchronized void setSortType(SortType sortType) {
        mSortType = sortType;
    }

    public synchronized boolean isSortAscending(SortType sortType) {
        if (mSortAscending.get(sortType) == null) {
            mSortAscending.put(sortType, sortType.isDefaultAscending());
        }
        return mSortAscending.get(sortType);
    }

    public synchronized void setSortAscending(SortType sortType, boolean sortAscending) {
        mSortAscending.put(sortType, sortAscending);
    }

    public synchronized ShowPictures getShowPictures() {
        return mShowPictures;
    }

    public synchronized void setShowPictures(ShowPictures showPictures) {
        mShowPictures = showPictures;
    }

    public synchronized FolderMode getFolderTargetMode() {
        return mFolderTargetMode;
    }

    public synchronized void setFolderTargetMode(FolderMode folderTargetMode) {
        mFolderTargetMode = folderTargetMode;
    }

    public synchronized boolean isSignatureBeforeQuotedText() {
        return mIsSignatureBeforeQuotedText;
    }

    public synchronized void setSignatureBeforeQuotedText(boolean mIsSignatureBeforeQuotedText) {
        this.mIsSignatureBeforeQuotedText = mIsSignatureBeforeQuotedText;
    }

    public synchronized boolean isNotifySelfNewMail() {
        return mNotifySelfNewMail;
    }

    public synchronized void setNotifySelfNewMail(boolean notifySelfNewMail) {
        mNotifySelfNewMail = notifySelfNewMail;
    }

    public synchronized String getExpungePolicy() {
        return mExpungePolicy;
    }

    public synchronized void setExpungePolicy(String expungePolicy) {
        mExpungePolicy = expungePolicy;
    }

    public synchronized int getMaxPushFolders() {
        return mMaxPushFolders;
    }

    public synchronized boolean setMaxPushFolders(int maxPushFolders) {
        int oldMaxPushFolders = mMaxPushFolders;
        mMaxPushFolders = maxPushFolders;
        return oldMaxPushFolders != maxPushFolders;
    }

    public LocalStore getLocalStore() throws MessagingException {
        return Store.getLocalInstance(this, K9.app);
    }

    public Store getRemoteStore() throws MessagingException {
        return Store.getRemoteInstance(this);
    }

    // It'd be great if this actually went into the store implementation
    // to get this, but that's expensive and not easily accessible
    // during initialization
    public boolean isSearchByDateCapable() {
        return (getStoreUri().startsWith("imap"));
    }


    @Override
    public synchronized String toString() {
        return mDescription;
    }

    public synchronized void setCompression(String networkType, boolean useCompression) {
        compressionMap.put(networkType, useCompression);
    }

    public synchronized boolean useCompression(String networkType) {
        Boolean useCompression = compressionMap.get(networkType);
        if (useCompression == null) {
            return true;
        }

        return useCompression;
    }

    public boolean useCompression(int type) {
        String networkType = TYPE_OTHER;
        switch (type) {
        case ConnectivityManager.TYPE_MOBILE:
            networkType = TYPE_MOBILE;
            break;
        case ConnectivityManager.TYPE_WIFI:
            networkType = TYPE_WIFI;
            break;
        }
        return useCompression(networkType);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Account) {
            return ((Account)o).mUuid.equals(mUuid);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return mUuid.hashCode();
    }

    private synchronized List<Identity> loadIdentities(SharedPreferences prefs) {
        List<Identity> newIdentities = new ArrayList<Identity>();
        int ident = 0;
        boolean gotOne = false;
        do {
            gotOne = false;
            String name = prefs.getString(mUuid + "." + IDENTITY_NAME_KEY + "." + ident, null);
            String email = prefs.getString(mUuid + "." + IDENTITY_EMAIL_KEY + "." + ident, null);
            boolean signatureUse = prefs.getBoolean(mUuid  + ".signatureUse." + ident, true);
            String signature = prefs.getString(mUuid + ".signature." + ident, null);
            String description = prefs.getString(mUuid + "." + IDENTITY_DESCRIPTION_KEY + "." + ident, null);
            final String replyTo = prefs.getString(mUuid + ".replyTo." + ident, null);
            if (email != null) {
                Identity identity = new Identity();
                identity.setName(name);
                identity.setEmail(email);
                identity.setSignatureUse(signatureUse);
                identity.setSignature(signature);
                identity.setDescription(description);
                identity.setReplyTo(replyTo);
                newIdentities.add(identity);
                gotOne = true;
            }
            ident++;
        } while (gotOne);

        if (newIdentities.isEmpty()) {
            String name = prefs.getString(mUuid + ".name", null);
            String email = prefs.getString(mUuid + ".email", null);
            boolean signatureUse = prefs.getBoolean(mUuid  + ".signatureUse", true);
            String signature = prefs.getString(mUuid + ".signature", null);
            Identity identity = new Identity();
            identity.setName(name);
            identity.setEmail(email);
            identity.setSignatureUse(signatureUse);
            identity.setSignature(signature);
            identity.setDescription(email);
            newIdentities.add(identity);
        }

        return newIdentities;
    }

    private synchronized void deleteIdentities(SharedPreferences prefs, SharedPreferences.Editor editor) {
        int ident = 0;
        boolean gotOne = false;
        do {
            gotOne = false;
            String email = prefs.getString(mUuid + "." + IDENTITY_EMAIL_KEY + "." + ident, null);
            if (email != null) {
                editor.remove(mUuid + "." + IDENTITY_NAME_KEY + "." + ident);
                editor.remove(mUuid + "." + IDENTITY_EMAIL_KEY + "." + ident);
                editor.remove(mUuid + ".signatureUse." + ident);
                editor.remove(mUuid + ".signature." + ident);
                editor.remove(mUuid + "." + IDENTITY_DESCRIPTION_KEY + "." + ident);
                editor.remove(mUuid + ".replyTo." + ident);
                gotOne = true;
            }
            ident++;
        } while (gotOne);
    }

    private synchronized void saveIdentities(SharedPreferences prefs, SharedPreferences.Editor editor) {
        deleteIdentities(prefs, editor);
        int ident = 0;

        for (Identity identity : identities) {
            editor.putString(mUuid + "." + IDENTITY_NAME_KEY + "." + ident, identity.getName());
            editor.putString(mUuid + "." + IDENTITY_EMAIL_KEY + "." + ident, identity.getEmail());
            editor.putBoolean(mUuid + ".signatureUse." + ident, identity.getSignatureUse());
            editor.putString(mUuid + ".signature." + ident, identity.getSignature());
            editor.putString(mUuid + "." + IDENTITY_DESCRIPTION_KEY + "." + ident, identity.getDescription());
            editor.putString(mUuid + ".replyTo." + ident, identity.getReplyTo());
            ident++;
        }
    }

    public synchronized List<Identity> getIdentities() {
        return identities;
    }

    public synchronized void setIdentities(List<Identity> newIdentities) {
        identities = new ArrayList<Identity>(newIdentities);
    }

    public synchronized Identity getIdentity(int i) {
        if (i < identities.size()) {
            return identities.get(i);
        }
        return null;
    }

    public boolean isAnIdentity(Address[] addrs) {
        if (addrs == null) {
            return false;
        }
        for (Address addr : addrs) {
            if (findIdentity(addr) != null) {
                return true;
            }
        }

        return false;
    }

    public boolean isAnIdentity(Address addr) {
        return findIdentity(addr) != null;
    }

    public synchronized Identity findIdentity(Address addr) {
        for (Identity identity : identities) {
            String email = identity.getEmail();
            if (email != null && email.equalsIgnoreCase(addr.getAddress())) {
                return identity;
            }
        }
        return null;
    }

    public synchronized Searchable getSearchableFolders() {
        return searchableFolders;
    }

    public synchronized void setSearchableFolders(Searchable searchableFolders) {
        this.searchableFolders = searchableFolders;
    }

    public synchronized int getIdleRefreshMinutes() {
        return mIdleRefreshMinutes;
    }

    public synchronized void setIdleRefreshMinutes(int idleRefreshMinutes) {
        mIdleRefreshMinutes = idleRefreshMinutes;
    }

    public synchronized boolean isPushPollOnConnect() {
        return mPushPollOnConnect;
    }

    public synchronized void setPushPollOnConnect(boolean pushPollOnConnect) {
        mPushPollOnConnect = pushPollOnConnect;
    }

    /**
     * Are we storing out localStore on the SD-card instead of the local device
     * memory?<br/>
     * Only to be called durin initial account-setup!<br/>
     * Side-effect: changes {@link #mLocalStorageProviderId}.
     *
     * @param newStorageProviderId
     *            Never <code>null</code>.
     * @throws MessagingException
     */
    public void switchLocalStorage(final String newStorageProviderId) throws MessagingException {
        if (!mLocalStorageProviderId.equals(newStorageProviderId)) {
            getLocalStore().switchLocalStorage(newStorageProviderId);
        }
    }

    public synchronized boolean goToUnreadMessageSearch() {
        return goToUnreadMessageSearch;
    }

    public synchronized void setGoToUnreadMessageSearch(boolean goToUnreadMessageSearch) {
        this.goToUnreadMessageSearch = goToUnreadMessageSearch;
    }

    public synchronized boolean subscribedFoldersOnly() {
        return subscribedFoldersOnly;
    }

    public synchronized void setSubscribedFoldersOnly(boolean subscribedFoldersOnly) {
        this.subscribedFoldersOnly = subscribedFoldersOnly;
    }

    public synchronized int getMaximumPolledMessageAge() {
        return maximumPolledMessageAge;
    }

    public synchronized void setMaximumPolledMessageAge(int maximumPolledMessageAge) {
        this.maximumPolledMessageAge = maximumPolledMessageAge;
    }

    public synchronized int getMaximumAutoDownloadMessageSize() {
        return maximumAutoDownloadMessageSize;
    }

    public synchronized void setMaximumAutoDownloadMessageSize(int maximumAutoDownloadMessageSize) {
        this.maximumAutoDownloadMessageSize = maximumAutoDownloadMessageSize;
    }

    public Date getEarliestPollDate() {
        int age = getMaximumPolledMessageAge();
        if (age >= 0) {
            Calendar now = Calendar.getInstance();
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            if (age < 28) {
                now.add(Calendar.DATE, age * -1);
            } else switch (age) {
                case 28:
                    now.add(Calendar.MONTH, -1);
                    break;
                case 56:
                    now.add(Calendar.MONTH, -2);
                    break;
                case 84:
                    now.add(Calendar.MONTH, -3);
                    break;
                case 168:
                    now.add(Calendar.MONTH, -6);
                    break;
                case 365:
                    now.add(Calendar.YEAR, -1);
                    break;
                }

            return now.getTime();
        }

        return null;
    }

    public MessageFormat getMessageFormat() {
        return mMessageFormat;
    }

    public void setMessageFormat(MessageFormat messageFormat) {
        this.mMessageFormat = messageFormat;
    }

    public synchronized boolean isMessageReadReceiptAlways() {
        return mMessageReadReceipt;
    }

    public synchronized void setMessageReadReceipt(boolean messageReadReceipt) {
        mMessageReadReceipt = messageReadReceipt;
    }

    public QuoteStyle getQuoteStyle() {
        return mQuoteStyle;
    }

    public void setQuoteStyle(QuoteStyle quoteStyle) {
        this.mQuoteStyle = quoteStyle;
    }

    public synchronized String getQuotePrefix() {
        return mQuotePrefix;
    }

    public synchronized void setQuotePrefix(String quotePrefix) {
        mQuotePrefix = quotePrefix;
    }

    public synchronized boolean isDefaultQuotedTextShown() {
        return mDefaultQuotedTextShown;
    }

    public synchronized void setDefaultQuotedTextShown(boolean shown) {
        mDefaultQuotedTextShown = shown;
    }

    public synchronized boolean isReplyAfterQuote() {
        return mReplyAfterQuote;
    }

    public synchronized void setReplyAfterQuote(boolean replyAfterQuote) {
        mReplyAfterQuote = replyAfterQuote;
    }

    public synchronized boolean isStripSignature() {
        return mStripSignature;
    }

    public synchronized void setStripSignature(boolean stripSignature) {
        mStripSignature = stripSignature;
    }

    public String getCryptoApp() {
        return mCryptoApp;
    }

    public void setCryptoApp(String cryptoApp) {
        mCryptoApp = cryptoApp;
        // invalidate the provider
        mCryptoProvider = null;
    }

    public boolean getCryptoAutoSignature() {
        return mCryptoAutoSignature;
    }

    public void setCryptoAutoSignature(boolean cryptoAutoSignature) {
        mCryptoAutoSignature = cryptoAutoSignature;
    }

    public boolean isCryptoAutoEncrypt() {
        return mCryptoAutoEncrypt;
    }

    public void setCryptoAutoEncrypt(boolean cryptoAutoEncrypt) {
        mCryptoAutoEncrypt = cryptoAutoEncrypt;
    }

    public boolean allowRemoteSearch() {
        return mAllowRemoteSearch;
    }

    public void setAllowRemoteSearch(boolean val) {
        mAllowRemoteSearch = val;
    }

    public int getRemoteSearchNumResults() {
        return mRemoteSearchNumResults;
    }

    public void setRemoteSearchNumResults(int val) {
        mRemoteSearchNumResults = (val >= 0 ? val : 0);
    }

    public String getInboxFolderName() {
        return mInboxFolderName;
    }

    public void setInboxFolderName(String mInboxFolderName) {
        this.mInboxFolderName = mInboxFolderName;
    }

    public synchronized boolean syncRemoteDeletions() {
        return mSyncRemoteDeletions;
    }

    public synchronized void setSyncRemoteDeletions(boolean syncRemoteDeletions) {
        mSyncRemoteDeletions = syncRemoteDeletions;
    }

    public synchronized String getLastSelectedFolderName() {
        return lastSelectedFolderName;
    }

    public synchronized void setLastSelectedFolderName(String folderName) {
        lastSelectedFolderName = folderName;
    }

    public synchronized CryptoProvider getCryptoProvider() {
        if (mCryptoProvider == null) {
            mCryptoProvider = CryptoProvider.createInstance(getCryptoApp());
        }
        return mCryptoProvider;
    }
    
    public synchronized String getOpenPgpProvider() {
        // return null if set to "APG" or "None"
        if (getCryptoApp().equals("apg") || getCryptoApp().equals("")) {
            return null;
        }
        return getCryptoApp();
    }

    public synchronized NotificationSetting getNotificationSetting() {
        return mNotificationSetting;
    }

    /**
     * @return <code>true</code> if our {@link StorageProvider} is ready. (e.g.
     *         card inserted)
     */
    public boolean isAvailable(Context context) {
        String localStorageProviderId = getLocalStorageProviderId();
        if (localStorageProviderId == null) {
            return true; // defaults to internal memory
        }
        return StorageManager.getInstance(K9.app).isReady(localStorageProviderId);
    }

    public synchronized boolean isEnabled() {
        return mEnabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public synchronized boolean isMarkMessageAsReadOnView() {
        return mMarkMessageAsReadOnView;
    }

    public synchronized void setMarkMessageAsReadOnView(boolean value) {
        mMarkMessageAsReadOnView = value;
    }

    public synchronized boolean isAlwaysShowCcBcc() {
        return mAlwaysShowCcBcc;
    }

    public synchronized void setAlwaysShowCcBcc(boolean show) {
        mAlwaysShowCcBcc = show;
    }
    public boolean isRemoteSearchFullText() {
        return false;   // Temporarily disabled
        //return mRemoteSearchFullText;
    }

    public void setRemoteSearchFullText(boolean val) {
        mRemoteSearchFullText = val;
    }

    /**
     * Modify the supplied {@link LocalSearch} instance to limit the search to displayable folders.
     *
     * <p>
     * This method uses the current folder display mode to decide what folders to include/exclude.
     * </p>
     *
     * @param search
     *         The {@code LocalSearch} instance to modify.
     *
     * @see #getFolderDisplayMode()
     */
    public void limitToDisplayableFolders(LocalSearch search) {
        final Account.FolderMode displayMode = getFolderDisplayMode();

        switch (displayMode) {
            case FIRST_CLASS: {
                // Count messages in the INBOX and non-special first class folders
                search.and(Searchfield.DISPLAY_CLASS, FolderClass.FIRST_CLASS.name(),
                        Attribute.EQUALS);
                break;
            }
            case FIRST_AND_SECOND_CLASS: {
                // Count messages in the INBOX and non-special first and second class folders
                search.and(Searchfield.DISPLAY_CLASS, FolderClass.FIRST_CLASS.name(),
                        Attribute.EQUALS);

                // TODO: Create a proper interface for creating arbitrary condition trees
                SearchCondition searchCondition = new SearchCondition(Searchfield.DISPLAY_CLASS,
                        Attribute.EQUALS, FolderClass.SECOND_CLASS.name());
                ConditionsTreeNode root = search.getConditions();
                if (root.mRight != null) {
                    root.mRight.or(searchCondition);
                } else {
                    search.or(searchCondition);
                }
                break;
            }
            case NOT_SECOND_CLASS: {
                // Count messages in the INBOX and non-special non-second-class folders
                search.and(Searchfield.DISPLAY_CLASS, FolderClass.SECOND_CLASS.name(),
                        Attribute.NOT_EQUALS);
                break;
            }
            default:
            case ALL: {
                // Count messages in the INBOX and non-special folders
                break;
            }
        }
    }

    /**
     * Modify the supplied {@link LocalSearch} instance to exclude special folders.
     *
     * <p>
     * Currently the following folders are excluded:
     * <ul>
     *   <li>Trash</li>
     *   <li>Drafts</li>
     *   <li>Spam</li>
     *   <li>Outbox</li>
     *   <li>Sent</li>
     * </ul>
     * The Inbox will always be included even if one of the special folders is configured to point
     * to the Inbox.
     * </p>
     *
     * @param search
     *         The {@code LocalSearch} instance to modify.
     */
    public void excludeSpecialFolders(LocalSearch search) {
        excludeSpecialFolder(search, getTrashFolderName());
        excludeSpecialFolder(search, getDraftsFolderName());
        excludeSpecialFolder(search, getSpamFolderName());
        excludeSpecialFolder(search, getOutboxFolderName());
        excludeSpecialFolder(search, getSentFolderName());
        excludeSpecialFolder(search, getErrorFolderName());
        search.or(new SearchCondition(Searchfield.FOLDER, Attribute.EQUALS, getInboxFolderName()));
    }

    /**
     * Modify the supplied {@link LocalSearch} instance to exclude "unwanted" folders.
     *
     * <p>
     * Currently the following folders are excluded:
     * <ul>
     *   <li>Trash</li>
     *   <li>Spam</li>
     *   <li>Outbox</li>
     * </ul>
     * The Inbox will always be included even if one of the special folders is configured to point
     * to the Inbox.
     * </p>
     *
     * @param search
     *         The {@code LocalSearch} instance to modify.
     */
    public void excludeUnwantedFolders(LocalSearch search) {
        excludeSpecialFolder(search, getTrashFolderName());
        excludeSpecialFolder(search, getSpamFolderName());
        excludeSpecialFolder(search, getOutboxFolderName());
        search.or(new SearchCondition(Searchfield.FOLDER, Attribute.EQUALS, getInboxFolderName()));
    }

    private void excludeSpecialFolder(LocalSearch search, String folderName) {
        if (!K9.FOLDER_NONE.equals(folderName)) {
            search.and(Searchfield.FOLDER, folderName, Attribute.NOT_EQUALS);
        }
    }

    /**
     * Add a new certificate for the incoming or outgoing server to the local key store.
     */
    public void addCertificate(CheckDirection direction,
            X509Certificate certificate) throws CertificateException {
        Uri uri;
        if (direction == CheckDirection.INCOMING) {
            uri = Uri.parse(getStoreUri());
        } else {
            uri = Uri.parse(getTransportUri());
        }
        LocalKeyStore localKeyStore = LocalKeyStore.getInstance();
        localKeyStore.addCertificate(uri.getHost(), uri.getPort(), certificate);
    }

    /**
     * Examine the existing settings for an account.  If the old host/port is different from the
     * new host/port, then try and delete any (possibly non-existent) certificate stored for the
     * old host/port.
     */
    public void deleteCertificate(String newHost, int newPort,
            CheckDirection direction) {
        Uri uri;
        if (direction == CheckDirection.INCOMING) {
            uri = Uri.parse(getStoreUri());
        } else {
            uri = Uri.parse(getTransportUri());
        }
        String oldHost = uri.getHost();
        int oldPort = uri.getPort();
        if (oldPort == -1) {
            // This occurs when a new account is created
            return;
        }
        if (!newHost.equals(oldHost) || newPort != oldPort) {
            LocalKeyStore localKeyStore = LocalKeyStore.getInstance();
            localKeyStore.deleteCertificate(oldHost, oldPort);
        }
    }

    /**
     * Examine the settings for the account and attempt to delete (possibly non-existent)
     * certificates for the incoming and outgoing servers.
     */
    public void deleteCertificates() {
        LocalKeyStore localKeyStore = LocalKeyStore.getInstance();

        Uri uri = Uri.parse(getStoreUri());
        localKeyStore.deleteCertificate(uri.getHost(), uri.getPort());
        uri = Uri.parse(getTransportUri());
        localKeyStore.deleteCertificate(uri.getHost(), uri.getPort());
    }
}
