package com.daxslab.mail.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.daxslab.mail.R;
import com.daxslab.mail.activity.Accounts;
import com.daxslab.mail.activity.K9Activity;
import com.daxslab.mail.helper.HtmlConverter;

/**
 * Displays a welcome message when no accounts have been created yet.
 */
public class WelcomeMessage extends K9Activity implements OnClickListener{

    public static void showWelcomeMessage(Context context) {
        Intent intent = new Intent(context, WelcomeMessage.class);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.welcome_message);


        class ImageGetter implements Html.ImageGetter {

            public Drawable getDrawable(String source) {
                int id;

                if (source.equals("daxsLogo.png")) {
                    id = R.drawable.daxs_logo;
                }
                else if (source.equals("icon.png")) {
                    id = R.drawable.icon;
                }
                else {
                    return null;
                }

                Drawable d = getResources().getDrawable(id);
                d.setBounds(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());
                return d;
            }
        };

        TextView welcome = (TextView) findViewById(R.id.welcome_message);
        welcome.setText(HtmlConverter.htmlToSpanned(getString(R.string.accounts_welcome),new ImageGetter()));
        welcome.setMovementMethod(LinkMovementMethod.getInstance());

        ((Button) findViewById(R.id.next)).setOnClickListener(this);
        ((Button) findViewById(R.id.import_settings)).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next: {
                AccountSetupBasics.actionNewAccount(this);
                finish();
                break;
            }
            case R.id.import_settings: {
                Accounts.importSettings(this);
                finish();
                break;
            }
        }
    }
}
