package com.daxslab.mail.preferences;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.daxslab.mail.K9;
import com.daxslab.mail.helper.Utility;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Storage implements SharedPreferences {
    private static ConcurrentHashMap<Context, Storage> storages =
        new ConcurrentHashMap<Context, Storage>();

    private volatile ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<String, String>();

    private CopyOnWriteArrayList<OnSharedPreferenceChangeListener> listeners =
        new CopyOnWriteArrayList<OnSharedPreferenceChangeListener>();

    private int DB_VERSION = 2;
    private String DB_NAME = "preferences_storage";

    private ThreadLocal<ConcurrentHashMap<String, String>> workingStorage
    = new ThreadLocal<ConcurrentHashMap<String, String>>();
    private ThreadLocal<SQLiteDatabase> workingDB =
        new ThreadLocal<SQLiteDatabase>();
    private ThreadLocal<ArrayList<String>> workingChangedKeys = new ThreadLocal<ArrayList<String>>();


    private Context context = null;

    private SQLiteDatabase openDB() {
        SQLiteDatabase mDb = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null);

        if (mDb.getVersion() == 1) {
            Log.i(K9.LOG_TAG, "Updating preferences to urlencoded username/password");

            String accountUuids = readValue(mDb, "accountUuids");
            if (accountUuids != null && accountUuids.length() != 0) {
                String[] uuids = accountUuids.split(",");
                for (String uuid : uuids) {
                    try {
                        String storeUriStr = Utility.base64Decode(readValue(mDb, uuid + ".storeUri"));
                        String transportUriStr = Utility.base64Decode(readValue(mDb, uuid + ".transportUri"));

                        URI uri = new URI(transportUriStr);
                        String newUserInfo = null;
                        if (transportUriStr != null) {
                            String[] userInfoParts = uri.getUserInfo().split(":");

                            String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");
                            String passwordEnc = "";
                            String authType = "";
                            if (userInfoParts.length > 1) {
                                passwordEnc = ":" + URLEncoder.encode(userInfoParts[1], "UTF-8");
                            }
                            if (userInfoParts.length > 2) {
                                authType = ":" + userInfoParts[2];
                            }

                            newUserInfo = usernameEnc + passwordEnc + authType;
                        }

                        if (newUserInfo != null) {
                            URI newUri = new URI(uri.getScheme(), newUserInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                            String newTransportUriStr = Utility.base64Encode(newUri.toString());
                            writeValue(mDb, uuid + ".transportUri", newTransportUriStr);
                        }

                        uri = new URI(storeUriStr);
                        newUserInfo = null;
                        if (storeUriStr.startsWith("imap")) {
                            String[] userInfoParts = uri.getUserInfo().split(":");
                            if (userInfoParts.length == 2) {
                                String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");
                                String passwordEnc = URLEncoder.encode(userInfoParts[1], "UTF-8");

                                newUserInfo = usernameEnc + ":" + passwordEnc;
                            } else {
                                String authType = userInfoParts[0];
                                String usernameEnc = URLEncoder.encode(userInfoParts[1], "UTF-8");
                                String passwordEnc = URLEncoder.encode(userInfoParts[2], "UTF-8");

                                newUserInfo = authType + ":" + usernameEnc + ":" + passwordEnc;
                            }
                        } else if (storeUriStr.startsWith("pop3")) {
                            String[] userInfoParts = uri.getUserInfo().split(":", 2);
                            String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");

                            String passwordEnc = "";
                            if (userInfoParts.length > 1) {
                                passwordEnc = ":" + URLEncoder.encode(userInfoParts[1], "UTF-8");
                            }

                            newUserInfo = usernameEnc + passwordEnc;
                        } else if (storeUriStr.startsWith("webdav")) {
                            String[] userInfoParts = uri.getUserInfo().split(":", 2);
                            String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");

                            String passwordEnc = "";
                            if (userInfoParts.length > 1) {
                                passwordEnc = ":" + URLEncoder.encode(userInfoParts[1], "UTF-8");
                            }

                            newUserInfo = usernameEnc + passwordEnc;
                        }

                        if (newUserInfo != null) {
                            URI newUri = new URI(uri.getScheme(), newUserInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                            String newStoreUriStr = Utility.base64Encode(newUri.toString());
                            writeValue(mDb, uuid + ".storeUri", newStoreUriStr);
                        }
                    } catch (Exception e) {
                        Log.e(K9.LOG_TAG, "ooops", e);
                    }
                }
            }

            mDb.setVersion(DB_VERSION);
        }

        if (mDb.getVersion() != DB_VERSION) {
            Log.i(K9.LOG_TAG, "Creating Storage database");
            mDb.execSQL("DROP TABLE IF EXISTS preferences_storage");
            mDb.execSQL("CREATE TABLE preferences_storage " +
                        "(primkey TEXT PRIMARY KEY ON CONFLICT REPLACE, value TEXT)");
            mDb.setVersion(DB_VERSION);
        }
        return mDb;
    }


    public static Storage getStorage(Context context) {
        Storage tmpStorage = storages.get(context);
        if (tmpStorage != null) {
            if (K9.DEBUG) {
                Log.d(K9.LOG_TAG, "Returning already existing Storage");
            }
            return tmpStorage;
        } else {
            if (K9.DEBUG) {
                Log.d(K9.LOG_TAG, "Creating provisional storage");
            }
            tmpStorage = new Storage(context);
            Storage oldStorage = storages.putIfAbsent(context, tmpStorage);
            if (oldStorage != null) {
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Another thread beat us to creating the Storage, returning that one");
                }
                return oldStorage;
            } else {
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Returning the Storage we created");
                }
                return tmpStorage;
            }
        }
    }

    private void loadValues() {
        long startTime = System.currentTimeMillis();
        Log.i(K9.LOG_TAG, "Loading preferences from DB into Storage");
        Cursor cursor = null;
        SQLiteDatabase mDb = null;
        try {
            mDb = openDB();

            cursor = mDb.rawQuery("SELECT primkey, value FROM preferences_storage", null);
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                String value = cursor.getString(1);
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Loading key '" + key + "', value = '" + value + "'");
                }
                storage.put(key, value);
            }
        } finally {
            Utility.closeQuietly(cursor);
            if (mDb != null) {
                mDb.close();
            }
            long endTime = System.currentTimeMillis();
            Log.i(K9.LOG_TAG, "Preferences load took " + (endTime - startTime) + "ms");
        }
    }

    private Storage(Context context) {
        this.context = context;
        loadValues();
    }

    private void keyChange(String key) {
        ArrayList<String> changedKeys = workingChangedKeys.get();
        if (!changedKeys.contains(key)) {
            changedKeys.add(key);
        }
    }

    protected void put(String key, String value) {
        ContentValues cv = generateCV(key, value);
        workingDB.get().insert("preferences_storage", "primkey", cv);
        liveUpdate(key, value);
    }

    protected void put(Map<String, String> insertables) {
        String sql = "INSERT INTO preferences_storage (primkey, value) VALUES (?, ?)";
        SQLiteStatement stmt = workingDB.get().compileStatement(sql);

        for (Map.Entry<String, String> entry : insertables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            stmt.bindString(1, key);
            stmt.bindString(2, value);
            stmt.execute();
            stmt.clearBindings();
            liveUpdate(key, value);
        }
        stmt.close();
    }

    private ContentValues generateCV(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("primkey", key);
        cv.put("value", value);
        return cv;
    }

    private void liveUpdate(String key, String value) {
        workingStorage.get().put(key, value);

        keyChange(key);
    }

    protected void remove(String key) {
        workingDB.get().delete("preferences_storage", "primkey = ?", new String[] { key });
        workingStorage.get().remove(key);

        keyChange(key);
    }

    protected void removeAll() {
        for (String key : workingStorage.get().keySet()) {
            keyChange(key);
        }
        workingDB.get().execSQL("DELETE FROM preferences_storage");
        workingStorage.get().clear();
    }

    protected void doInTransaction(Runnable dbWork) {
        ConcurrentHashMap<String, String> newStorage = new ConcurrentHashMap<String, String>();
        newStorage.putAll(storage);
        workingStorage.set(newStorage);

        SQLiteDatabase mDb = openDB();
        workingDB.set(mDb);

        ArrayList<String> changedKeys = new ArrayList<String>();
        workingChangedKeys.set(changedKeys);

        mDb.beginTransaction();
        try {
            dbWork.run();
            mDb.setTransactionSuccessful();
            storage = newStorage;
            for (String changedKey : changedKeys) {
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    listener.onSharedPreferenceChanged(this, changedKey);
                }
            }
        } finally {
            workingDB.remove();
            workingStorage.remove();
            workingChangedKeys.remove();
            mDb.endTransaction();
            mDb.close();
        }
    }

    public long size() {
        return storage.size();
    }

    //@Override
    public boolean contains(String key) {
        return storage.contains(key);
    }

    //@Override
    public com.daxslab.mail.preferences.Editor edit() {
        return new com.daxslab.mail.preferences.Editor(this);
    }

    //@Override
    public Map<String, String> getAll() {
        return storage;
    }

    //@Override
    public boolean getBoolean(String key, boolean defValue) {
        String val = storage.get(key);
        if (val == null) {
            return defValue;
        }
        return Boolean.parseBoolean(val);
    }

    //@Override
    public float getFloat(String key, float defValue) {
        String val = storage.get(key);
        if (val == null) {
            return defValue;
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException nfe) {
            Log.e(K9.LOG_TAG, "Could not parse float", nfe);
            return defValue;
        }
    }

    //@Override
    public int getInt(String key, int defValue) {
        String val = storage.get(key);
        if (val == null) {
            return defValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException nfe) {
            Log.e(K9.LOG_TAG, "Could not parse int", nfe);
            return defValue;
        }
    }

    //@Override
    public long getLong(String key, long defValue) {
        String val = storage.get(key);
        if (val == null) {
            return defValue;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException nfe) {
            Log.e(K9.LOG_TAG, "Could not parse long", nfe);
            return defValue;
        }
    }

    //@Override
    public String getString(String key, String defValue) {
        String val = storage.get(key);
        if (val == null) {
            return defValue;
        }
        return val;
    }

    //@Override
    public void registerOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener) {
        listeners.addIfAbsent(listener);
    }

    //@Override
    public void unregisterOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    private String readValue(SQLiteDatabase mDb, String key) {
        Cursor cursor = null;
        String value = null;
        try {
            cursor = mDb.query(
                         "preferences_storage",
                         new String[] {"value"},
                         "primkey = ?",
                         new String[] {key},
                         null,
                         null,
                         null);

            if (cursor.moveToNext()) {
                value = cursor.getString(0);
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Loading key '" + key + "', value = '" + value + "'");
                }
            }
        } finally {
            Utility.closeQuietly(cursor);
        }

        return value;
    }

    private void writeValue(SQLiteDatabase mDb, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("primkey", key);
        cv.put("value", value);

        long result = mDb.insert("preferences_storage", "primkey", cv);

        if (result == -1) {
            Log.e(K9.LOG_TAG, "Error writing key '" + key + "', value = '" + value + "'");
        }
    }


    @Override
    public Set<String> getStringSet(String arg0, Set<String> arg1) {
        throw new RuntimeException("Not implemented");
    }
}
