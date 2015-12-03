
package com.daxslab.mail.helper;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.daxslab.mail.K9;
import com.daxslab.mail.mail.filter.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
    /**
     * Regular expression that represents characters we won't allow in file names.
     *
     * <p>
     * Allowed are:
     * <ul>
     *   <li>word characters (letters, digits, and underscores): {@code \w}</li>
     *   <li>spaces: {@code " "}</li>
     *   <li>special characters: {@code !}, {@code #}, {@code $}, {@code %}, {@code &}, {@code '},
     *       {@code (}, {@code )}, {@code -}, {@code @}, {@code ^}, {@code `}, <code>&#123;</code>,
     *       <code>&#125;</code>, {@code ~}, {@code .}, {@code ,}</li>
     * </ul></p>
     *
     * @see #sanitizeFilename(String)
     */
    private static final String INVALID_CHARACTERS = "[^\\w !#$%&'()\\-@\\^`{}~.,]+";

    /**
     * Invalid characters in a file name are replaced by this character.
     *
     * @see #sanitizeFilename(String)
     */
    private static final String REPLACEMENT_CHARACTER = "_";

    // \u00A0 (non-breaking space) happens to be used by French MUA

    // Note: no longer using the ^ beginning character combined with (...)+
    // repetition matching as we might want to strip ML tags. Ex:
    // Re: [foo] Re: RE : [foo] blah blah blah
    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
                "((Re|Fw|Fwd|Aw|R\\u00E9f\\.)(\\[\\d+\\])?[\\u00A0 ]?: *)+", Pattern.CASE_INSENSITIVE);

    /**
     * Mailing-list tag pattern to match strings like "[foobar] "
     */
    private static final Pattern TAG_PATTERN = Pattern.compile("\\[[-_a-z0-9]+\\] ",
            Pattern.CASE_INSENSITIVE);

    private static Handler sMainThreadHandler;

    public static boolean arrayContains(Object[] a, Object o) {
        for (Object element : a) {
            if (element.equals(o)) {
                return true;
            }
        }
        return false;
    }

    public static boolean arrayContainsAny(Object[] a, Object... o) {
        for (Object element : a) {
            if (arrayContains(o, element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combines the given array of Objects into a single String using
     * each Object's toString() method and the separator character
     * between each part.
     *
     * @param parts
     * @param separator
     * @return new String
     */
    public static String combine(Object[] parts, char separator) {
        if (parts == null) {
            return null;
        }
        return TextUtils.join(String.valueOf(separator), parts);
    }

    public static String base64Decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        byte[] decoded = new Base64().decode(encoded.getBytes());
        return new String(decoded);
    }

    public static String base64Encode(String s) {
        if (s == null) {
            return s;
        }
        byte[] encoded = new Base64().encode(s.getBytes());
        return new String(encoded);
    }

    public static boolean requiredFieldValid(TextView view) {
        return view.getText() != null && view.getText().length() > 0;
    }


    public static boolean requiredFieldValid(Editable s) {
        return s != null && s.length() > 0;
    }

    public static boolean domainFieldValid(EditText view) {
        if (view.getText() != null) {
            String s = view.getText().toString();
            if (s.matches("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$") &&
                s.length() <= 253) {
                return true;
            }
            if (s.matches("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern ATOM = Pattern.compile("^(?:[a-zA-Z0-9!#$%&'*+\\-/=?^_`{|}~]|\\s)+$");

    /**
     * Quote a string, if necessary, based upon the definition of an "atom," as defined by RFC2822
     * (http://tools.ietf.org/html/rfc2822#section-3.2.4). Strings that consist purely of atoms are
     * left unquoted; anything else is returned as a quoted string.
     * @param text String to quote.
     * @return Possibly quoted string.
     */
    public static String quoteAtoms(final String text) {
        if (ATOM.matcher(text).matches()) {
            return text;
        } else {
            return quoteString(text);
        }
    }

    /**
     * Ensures that the given string starts and ends with the double quote character. The string is not modified in any way except to add the
     * double quote character to start and end if it's not already there.
     * sample -> "sample"
     * "sample" -> "sample"
     * ""sample"" -> "sample"
     * "sample"" -> "sample"
     * sa"mp"le -> "sa"mp"le"
     * "sa"mp"le" -> "sa"mp"le"
     * (empty string) -> ""
     * " -> ""
     * @param s
     * @return
     */
    public static String quoteString(String s) {
        if (s == null) {
            return null;
        }
        if (!s.matches("^\".*\"$")) {
            return "\"" + s + "\"";
        } else {
            return s;
        }
    }

    /**
     * A fast version of  URLDecoder.decode() that works only with UTF-8 and does only two
     * allocations. This version is around 3x as fast as the standard one and I'm using it
     * hundreds of times in places that slow down the UI, so it helps.
     */
    public static String fastUrlDecode(String s) {
        try {
            byte[] bytes = s.getBytes("UTF-8");
            byte ch;
            int length = 0;
            for (int i = 0, count = bytes.length; i < count; i++) {
                ch = bytes[i];
                if (ch == '%') {
                    int h = (bytes[i + 1] - '0');
                    int l = (bytes[i + 2] - '0');
                    if (h > 9) {
                        h -= 7;
                    }
                    if (l > 9) {
                        l -= 7;
                    }
                    bytes[length] = (byte)((h << 4) | l);
                    i += 2;
                } else if (ch == '+') {
                    bytes[length] = ' ';
                } else {
                    bytes[length] = bytes[i];
                }
                length++;
            }
            return new String(bytes, 0, length, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            return null;
        }
    }

    /*
     * TODO disabled this method globally. It is used in all the settings screens but I just
     * noticed that an unrelated icon was dimmed. Android must share drawables internally.
     */
    public static void setCompoundDrawablesAlpha(TextView view, int alpha) {
//        Drawable[] drawables = view.getCompoundDrawables();
//        for (Drawable drawable : drawables) {
//            if (drawable != null) {
//                drawable.setAlpha(alpha);
//            }
//        }
    }

    /**
     * <p>Wraps a multiline string of text, identifying words by <code>' '</code>.</p>
     *
     * <p>New lines will be separated by the system property line separator.
     * Very long words, such as URLs will <i>not</i> be wrapped.</p>
     *
     * <p>Leading spaces on a new line are stripped.
     * Trailing spaces are not stripped.</p>
     *
     * <pre>
     * WordUtils.wrap(null, *) = null
     * WordUtils.wrap("", *) = ""
     * </pre>
     *
     * Adapted from the Apache Commons Lang library.
     * http://svn.apache.org/viewvc/commons/proper/lang
     *   /trunk/src/main/java/org/apache/commons/lang3/text/WordUtils.java
     * SVN Revision 925967, Mon Mar 22 06:16:49 2010 UTC
     *
     * Licensed to the Apache Software Foundation (ASF) under one or more
     * contributor license agreements.  See the NOTICE file distributed with
     * this work for additional information regarding copyright ownership.
     * The ASF licenses this file to You under the Apache License, Version 2.0
     * (the "License"); you may not use this file except in compliance with
     * the License.  You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     *
     * @param str  the String to be word wrapped, may be null
     * @param wrapLength  the column to wrap the words at, less than 1 is treated as 1
     * @return a line with newlines inserted, <code>null</code> if null input
     */
    private static final String NEWLINE_REGEX = "(?:\\r?\\n)";
    public static String wrap(String str, int wrapLength) {
        StringBuilder result = new StringBuilder();
        for (String piece : str.split(NEWLINE_REGEX)) {
            result.append(wrap(piece, wrapLength, null, false));
            result.append("\r\n");
        }
        return result.toString();
    }

    /**
     * <p>Wraps a single line of text, identifying words by <code>' '</code>.</p>
     *
     * <p>Leading spaces on a new line are stripped.
     * Trailing spaces are not stripped.</p>
     *
     * <pre>
     * WordUtils.wrap(null, *, *, *) = null
     * WordUtils.wrap("", *, *, *) = ""
     * </pre>
     *
     * This is from the Apache Commons Lang library.
     * http://svn.apache.org/viewvc/commons/proper/lang
     *   /trunk/src/main/java/org/apache/commons/lang3/text/WordUtils.java
     * SVN Revision 925967, Mon Mar 22 06:16:49 2010 UTC
     *
     * Licensed to the Apache Software Foundation (ASF) under one or more
     * contributor license agreements.  See the NOTICE file distributed with
     * this work for additional information regarding copyright ownership.
     * The ASF licenses this file to You under the Apache License, Version 2.0
     * (the "License"); you may not use this file except in compliance with
     * the License.  You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     *
     * @param str  the String to be word wrapped, may be null
     * @param wrapLength  the column to wrap the words at, less than 1 is treated as 1
     * @param newLineStr  the string to insert for a new line,
     *  <code>null</code> uses the system property line separator
     * @param wrapLongWords  true if long words (such as URLs) should be wrapped
     * @return a line with newlines inserted, <code>null</code> if null input
     */
    public static String wrap(String str, int wrapLength, String newLineStr, boolean wrapLongWords) {
        if (str == null) {
            return null;
        }
        if (newLineStr == null) {
            newLineStr = "\r\n";
        }
        if (wrapLength < 1) {
            wrapLength = 1;
        }
        int inputLineLength = str.length();
        int offset = 0;
        StringBuilder wrappedLine = new StringBuilder(inputLineLength + 32);

        while ((inputLineLength - offset) > wrapLength) {
            if (str.charAt(offset) == ' ') {
                offset++;
                continue;
            }
            int spaceToWrapAt = str.lastIndexOf(' ', wrapLength + offset);

            if (spaceToWrapAt >= offset) {
                // normal case
                wrappedLine.append(str.substring(offset, spaceToWrapAt));
                wrappedLine.append(newLineStr);
                offset = spaceToWrapAt + 1;
            } else {
                // really long word or URL
                if (wrapLongWords) {
                    // wrap really long word one line at a time
                    wrappedLine.append(str.substring(offset, wrapLength + offset));
                    wrappedLine.append(newLineStr);
                    offset += wrapLength;
                } else {
                    // do not wrap really long word, just extend beyond limit
                    spaceToWrapAt = str.indexOf(' ', wrapLength + offset);
                    if (spaceToWrapAt >= 0) {
                        wrappedLine.append(str.substring(offset, spaceToWrapAt));
                        wrappedLine.append(newLineStr);
                        offset = spaceToWrapAt + 1;
                    } else {
                        wrappedLine.append(str.substring(offset));
                        offset = inputLineLength;
                    }
                }
            }
        }

        // Whatever is left in line is short enough to just pass through
        wrappedLine.append(str.substring(offset));

        return wrappedLine.toString();
    }

    /**
     * Extract the 'original' subject value, by ignoring leading
     * response/forward marker and '[XX]' formatted tags (as many mailing-list
     * softwares do).
     *
     * <p>
     * Result is also trimmed.
     * </p>
     *
     * @param subject
     *            Never <code>null</code>.
     * @return Never <code>null</code>.
     */
    public static String stripSubject(final String subject) {
        int lastPrefix = 0;

        final Matcher tagMatcher = TAG_PATTERN.matcher(subject);
        String tag = null;
        // whether tag stripping logic should be active
        boolean tagPresent = false;
        // whether the last action stripped a tag
        boolean tagStripped = false;
        if (tagMatcher.find(0)) {
            tagPresent = true;
            if (tagMatcher.start() == 0) {
                // found at beginning of subject, considering it an actual tag
                tag = tagMatcher.group();

                // now need to find response marker after that tag
                lastPrefix = tagMatcher.end();
                tagStripped = true;
            }
        }

        final Matcher matcher = RESPONSE_PATTERN.matcher(subject);

        // while:
        // - lastPrefix is within the bounds
        // - response marker found at lastPrefix position
        // (to make sure we don't catch response markers that are part of
        // the actual subject)

        while (lastPrefix < subject.length() - 1
                && matcher.find(lastPrefix)
                && matcher.start() == lastPrefix
                && (!tagPresent || tag == null || subject.regionMatches(matcher.end(), tag, 0,
                        tag.length()))) {
            lastPrefix = matcher.end();

            if (tagPresent) {
                tagStripped = false;
                if (tag == null) {
                    // attempt to find tag
                    if (tagMatcher.start() == lastPrefix) {
                        tag = tagMatcher.group();
                        lastPrefix += tag.length();
                        tagStripped = true;
                    }
                } else if (lastPrefix < subject.length() - 1 && subject.startsWith(tag, lastPrefix)) {
                    // Re: [foo] Re: [foo] blah blah blah
                    //               ^     ^
                    //               ^     ^
                    //               ^    new position
                    //               ^
                    //              initial position
                    lastPrefix += tag.length();
                    tagStripped = true;
                }
            }
        }
        // Null pointer check is to make the static analysis component of Eclipse happy.
        if (tagStripped && (tag != null)) {
            // restore the last tag
            lastPrefix -= tag.length();
        }
        if (lastPrefix > -1 && lastPrefix < subject.length() - 1) {
            return subject.substring(lastPrefix).trim();
        } else {
            return subject.trim();
        }
    }

    /**
     * @param parentDir
     * @param name
     *            Never <code>null</code>.
     */
    public static void touchFile(final File parentDir, final String name) {
        final File file = new File(parentDir, name);
        try {
            if (!file.exists()) {
                file.createNewFile();
            } else {
                file.setLastModified(System.currentTimeMillis());
            }
        } catch (Exception e) {
            Log.d(K9.LOG_TAG, "Unable to touch file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Creates a unique file in the given directory by appending a hyphen
     * and a number to the given filename.
     *
     * @param directory
     * @param filename
     * @return
     */
    public static File createUniqueFile(File directory, String filename) {
        File file = new File(directory, filename);
        if (!file.exists()) {
            return file;
        }
        // Get the extension of the file, if any.
        int index = filename.lastIndexOf('.');
        String format;
        if (index != -1) {
            String name = filename.substring(0, index);
            String extension = filename.substring(index);
            format = name + "-%d" + extension;
        } else {
            format = filename + "-%d";
        }
        for (int i = 2; i < Integer.MAX_VALUE; i++) {
            file = new File(directory, String.format(Locale.US, format, i));
            if (!file.exists()) {
                return file;
            }
        }
        return null;
    }



    /**
     * @param from
     * @param to
     * @return
     */
    public static boolean move(final File from, final File to) {
        if (to.exists()) {
            to.delete();
        }
        to.getParentFile().mkdirs();

        try {
            FileInputStream in = new FileInputStream(from);
            try {
                FileOutputStream out = new FileOutputStream(to);
                try {
                    byte[] buffer = new byte[1024];
                    int count = -1;
                    while ((count = in.read(buffer)) > 0) {
                        out.write(buffer, 0, count);
                    }
                } finally {
                    out.close();
                }
            } finally {
                try { in.close(); } catch (Throwable ignore) {}
            }
            from.delete();
            return true;
        } catch (Exception e) {
            Log.w(K9.LOG_TAG, "cannot move " + from.getAbsolutePath() + " to " + to.getAbsolutePath(), e);
            return false;
        }

    }

    /**
     * @param fromDir
     * @param toDir
     */
    public static void moveRecursive(final File fromDir, final File toDir) {
        if (!fromDir.exists()) {
            return;
        }
        if (!fromDir.isDirectory()) {
            if (toDir.exists()) {
                if (!toDir.delete()) {
                    Log.w(K9.LOG_TAG, "cannot delete already existing file/directory " + toDir.getAbsolutePath());
                }
            }
            if (!fromDir.renameTo(toDir)) {
                Log.w(K9.LOG_TAG, "cannot rename " + fromDir.getAbsolutePath() + " to " + toDir.getAbsolutePath() + " - moving instead");
                move(fromDir, toDir);
            }
            return;
        }
        if (!toDir.exists() || !toDir.isDirectory()) {
            if (toDir.exists()) {
                toDir.delete();
            }
            if (!toDir.mkdirs()) {
                Log.w(K9.LOG_TAG, "cannot create directory " + toDir.getAbsolutePath());
            }
        }
        File[] files = fromDir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                moveRecursive(file, new File(toDir, file.getName()));
                file.delete();
            } else {
                File target = new File(toDir, file.getName());
                if (!file.renameTo(target)) {
                    Log.w(K9.LOG_TAG, "cannot rename " + file.getAbsolutePath() + " to " + target.getAbsolutePath() + " - moving instead");
                    move(file, target);
                }
            }
        }
        if (!fromDir.delete()) {
            Log.w(K9.LOG_TAG, "cannot delete " + fromDir.getAbsolutePath());
        }
    }

    private static final String IMG_SRC_REGEX = "(?is:<img[^>]+src\\s*=\\s*['\"]?([a-z]+)\\:)";
    private static final Pattern IMG_PATTERN = Pattern.compile(IMG_SRC_REGEX);
    /**
     * Figure out if this part has images.
     * TODO: should only return true if we're an html part
     * @param message Content to evaluate
     * @return True if it has external images; false otherwise.
     */
    public static boolean hasExternalImages(final String message) {
        Matcher imgMatches = IMG_PATTERN.matcher(message);
        while (imgMatches.find()) {
            if (!imgMatches.group(1).equals("content")) {
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "External images found");
                }
                return true;
            }
        }
        if (K9.DEBUG) {
            Log.d(K9.LOG_TAG, "No external images.");
        }
        return false;
    }

    /**
     * Unconditionally close a Cursor.  Equivalent to {@link Cursor#close()},
     * if cursor is non-null.  This is typically used in finally blocks.
     *
     * @param cursor cursor to close
     */
    public static void closeQuietly(final Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    /**
     * Replace characters we don't allow in file names with a replacement character.
     *
     * @param filename
     *         The original file name.
     *
     * @return The sanitized file name containing only allowed characters.
     */
    public static String sanitizeFilename(String filename) {
        return filename.replaceAll(INVALID_CHARACTERS, REPLACEMENT_CHARACTER);
    }

    /**
     * Check to see if we have network connectivity.
     * @param app Current application (Hint: see if your base class has a getApplication() method.)
     * @return true if we have connectivity, false otherwise.
     */
    public static boolean hasConnectivity(final Application app) {
        final ConnectivityManager connectivityManager =
            (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        final NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        if (netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED) {
            return true;
        } else {
            return false;
        }
    }

    private static final Pattern MESSAGE_ID = Pattern.compile("<" +
            "(?:" +
                "[a-zA-Z0-9!#$%&'*+\\-/=?^_`{|}~]+" +
                "(?:\\.[a-zA-Z0-9!#$%&'*+\\-/=?^_`{|}~]+)*" +
                "|" +
                "\"(?:[^\\\\\"]|\\\\.)*\"" +
            ")" +
            "@" +
            "(?:" +
                "[a-zA-Z0-9!#$%&'*+\\-/=?^_`{|}~]+" +
                "(?:\\.[a-zA-Z0-9!#$%&'*+\\-/=?^_`{|}~]+)*" +
                "|" +
                "\\[(?:[^\\\\\\]]|\\\\.)*\\]" +
            ")" +
            ">");

    public static List<String> extractMessageIds(final String text) {
        List<String> messageIds = new ArrayList<String>();
        Matcher matcher = MESSAGE_ID.matcher(text);

        int start = 0;
        while (matcher.find(start)) {
            String messageId = text.substring(matcher.start(), matcher.end());
            messageIds.add(messageId);
            start = matcher.end();
        }

        return messageIds;
    }

    public static String extractMessageId(final String text) {
        Matcher matcher = MESSAGE_ID.matcher(text);

        if (matcher.find()) {
            return text.substring(matcher.start(), matcher.end());
        }

        return null;
    }

    /**
     * @return a {@link Handler} tied to the main thread.
     */
    public static Handler getMainThreadHandler() {
        if (sMainThreadHandler == null) {
            // No need to synchronize -- it's okay to create an extra Handler, which will be used
            // only once and then thrown away.
            sMainThreadHandler = new Handler(Looper.getMainLooper());
        }
        return sMainThreadHandler;
    }
}
