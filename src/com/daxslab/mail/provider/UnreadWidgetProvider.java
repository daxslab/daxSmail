package com.daxslab.mail.provider;

import com.daxslab.mail.Account;
import com.daxslab.mail.AccountStats;
import com.daxslab.mail.BaseAccount;
import com.daxslab.mail.K9;
import com.daxslab.mail.Preferences;
import com.daxslab.mail.R;
import com.daxslab.mail.activity.UnreadWidgetConfiguration;
import com.daxslab.mail.activity.FolderList;
import com.daxslab.mail.activity.MessageList;
import com.daxslab.mail.controller.MessagingController;
import com.daxslab.mail.search.LocalSearch;
import com.daxslab.mail.search.SearchAccount;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class UnreadWidgetProvider extends AppWidgetProvider {
    private static final int MAX_COUNT = 9999;

    /**
     * Trigger update for all of our unread widgets.
     *
     * @param context
     *         The {@code Context} object to use for the broadcast intent.
     */
    public static void updateUnreadCount(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(appContext);

        ComponentName thisWidget = new ComponentName(appContext, UnreadWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        Intent intent = new Intent(context, UnreadWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);

        context.sendBroadcast(intent);
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, String accountUuid) {

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                R.layout.unread_widget_layout);

        int unreadCount = 0;
        String accountName = context.getString(R.string.app_name);
        Intent clickIntent = null;
        try {
            BaseAccount account = null;
            AccountStats stats = null;

            SearchAccount searchAccount = null;
            if (SearchAccount.UNIFIED_INBOX.equals(accountUuid)) {
                searchAccount = SearchAccount.createUnifiedInboxAccount(context);
            } else if (SearchAccount.ALL_MESSAGES.equals(accountUuid)) {
                searchAccount = SearchAccount.createAllMessagesAccount(context);
            }

            if (searchAccount != null) {
                account = searchAccount;
                MessagingController controller = MessagingController.getInstance(K9.app);
                stats = controller.getSearchAccountStatsSynchronous(searchAccount, null);
                clickIntent = MessageList.intentDisplaySearch(context,
                        searchAccount.getRelatedSearch(), false, true, true);
            } else {
                Account realAccount = Preferences.getPreferences(context).getAccount(accountUuid);
                if (realAccount != null) {
                    account = realAccount;
                    stats = realAccount.getStats(context);

                    if (K9.FOLDER_NONE.equals(realAccount.getAutoExpandFolderName())) {
                        clickIntent = FolderList.actionHandleAccountIntent(context, realAccount, false);
                    } else {
                        LocalSearch search = new LocalSearch(realAccount.getAutoExpandFolderName());
                        search.addAllowedFolder(realAccount.getAutoExpandFolderName());
                        search.addAccountUuid(account.getUuid());
                        clickIntent = MessageList.intentDisplaySearch(context, search, false, true,
                                true);
                    }
                    clickIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                }
            }

            if (account != null) {
                accountName = account.getDescription();
            }

            if (stats != null) {
                unreadCount = stats.unreadMessageCount;
            }
        } catch (Exception e) {
            if (K9.DEBUG) {
                Log.e(K9.LOG_TAG, "Error getting widget configuration", e);
            }
        }

        if (unreadCount <= 0) {
            // Hide TextView for unread count if there are no unread messages.
            remoteViews.setViewVisibility(R.id.unread_count, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.unread_count, View.VISIBLE);

            String displayCount = (unreadCount <= MAX_COUNT) ?
                    String.valueOf(unreadCount) : String.valueOf(MAX_COUNT) + "+";
            remoteViews.setTextViewText(R.id.unread_count, displayCount);
        }

        remoteViews.setTextViewText(R.id.account_name, accountName);

        if (clickIntent == null) {
            // If the widget configuration couldn't be loaded we open the configuration
            // activity when the user clicks the widget.
            clickIntent = new Intent(context, UnreadWidgetConfiguration.class);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);


        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId,
                clickIntent, 0);

        remoteViews.setOnClickPendingIntent(R.id.unread_widget_layout, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
    }


    /**
     * Called when one or more widgets need to be updated.
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            String accountUuid = UnreadWidgetConfiguration.getAccountUuid(context, widgetId);

            updateWidget(context, appWidgetManager, widgetId, accountUuid);
        }
    }

    /**
     * Called when a widget instance is deleted.
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            UnreadWidgetConfiguration.deleteWidgetConfiguration(context, appWidgetId);
        }
    }
}
