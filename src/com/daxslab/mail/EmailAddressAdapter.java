/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.daxslab.mail;

import com.daxslab.mail.helper.Contacts;
import com.daxslab.mail.mail.Address;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class EmailAddressAdapter extends ResourceCursorAdapter {
    private final Contacts mContacts;

    public EmailAddressAdapter(Context context) {
        super(context, R.layout.recipient_dropdown_item, null);
        mContacts = Contacts.getInstance(context.getApplicationContext());
    }

    @Override
    public final String convertToString(final Cursor cursor) {
        final String name = mContacts.getName(cursor);
        final String address = mContacts.getEmail(cursor);

        return (address == null) ? "" : new Address(address, name).toString();
    }

    @Override
    public final void bindView(final View view, final Context context, final Cursor cursor) {
        final TextView text1 = (TextView) view.findViewById(R.id.text1);
        final TextView text2 = (TextView) view.findViewById(R.id.text2);
        text1.setText(mContacts.getName(cursor));
        text2.setText(mContacts.getEmail(cursor));
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        return mContacts.searchContacts(constraint);
    }
}
