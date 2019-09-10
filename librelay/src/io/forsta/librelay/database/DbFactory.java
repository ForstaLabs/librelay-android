package io.forsta.librelay.database;

import android.content.Context;

/**
 * Created by jlewis on 4/6/17.
 */

public class DbFactory {
  private static DbFactory instance;
  private static final Object lock = new Object();
  private DbHelper dbHelper;
  private Contacts contacts;
  private final Identities identities;
  private final SignedPrekeys signedPrekeys;
  private final Prekeys prekeys;
  private final Sessions sessions;
  private final MessageDatabase messages;
  private final MessageReceiptsDatabase receipts;
  private final AttachmentDatabase attachments;
  private final ImageDatabase images;
  private final ThreadDatabase threads;
  private final DraftDatabase drafts;
  private final PushDatabase pushMessages;
  private final GroupDatabase groups;
  private final RecipientPreferenceDatabase recipientPreferences;
  private final ThreadPreferenceDatabase threadPreferences;

  public static DbFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DbFactory(context.getApplicationContext());

      return instance;
    }
  }

  private DbFactory(Context context) {
    this.dbHelper = new DbHelper(context);
    this.contacts = new Contacts(context, dbHelper);
    this.identities = new Identities(context, dbHelper);
    this.signedPrekeys = new SignedPrekeys(context, dbHelper);
    this.prekeys = new Prekeys(context, dbHelper);
    this.sessions = new Sessions(context, dbHelper);
    this.messages = new MessageDatabase(context, dbHelper);
    this.receipts = new MessageReceiptsDatabase(context, dbHelper);
    this.attachments = new AttachmentDatabase(context, dbHelper);
    this.images = new ImageDatabase(context, dbHelper);
    this.threads = new ThreadDatabase(context, dbHelper);
    this.drafts = new DraftDatabase(context, dbHelper);
    this.pushMessages = new PushDatabase(context, dbHelper);
    this.groups = new GroupDatabase(context, dbHelper);
    this.recipientPreferences = new RecipientPreferenceDatabase(context, dbHelper);
    this.threadPreferences = new ThreadPreferenceDatabase(context, dbHelper);
  }

  public static Contacts getContacts(Context context) {
    return getInstance(context).contacts;
  }

  public static Identities getIdentities(Context context) {
    return getInstance(context).identities;
  }

  public static Prekeys getPrekeys(Context context) {
    return getInstance(context).prekeys;
  }

  public static SignedPrekeys getSignedPrekeys(Context context) {
    return getInstance(context).signedPrekeys;
  }

  public static Sessions getSessions(Context context) {
    return getInstance(context).sessions;
  }

  public static ThreadDatabase getThreadDatabase(Context context) {
    return getInstance(context).threads;
  }

  public static MessageDatabase getMessageDatabase(Context context) {
    return getInstance(context).messages;
  }

  public static AttachmentDatabase getAttachmentDatabase(Context context) {
    return getInstance(context).attachments;
  }

  public static ImageDatabase getImageDatabase(Context context) {
    return getInstance(context).images;
  }

  public static MessageReceiptsDatabase getMessageReceiptDatabase(Context context) {
    return getInstance(context).receipts;
  }

  public static DraftDatabase getDraftDatabase(Context context) {
    return getInstance(context).drafts;
  }

  public static PushDatabase getPushDatabase(Context context) {
    return getInstance(context).pushMessages;
  }

  public static GroupDatabase getGroupDatabase(Context context) {
    return getInstance(context).groups;
  }

  public static RecipientPreferenceDatabase getRecipientPreferenceDatabase(Context context) {
    return getInstance(context).recipientPreferences;
  }

  public static ThreadPreferenceDatabase getThreadPreferenceDatabase(Context context) {
    return getInstance(context).threadPreferences;
  }

  public void onApplicationLevelUpgrade(Context context, int fromVersion)
  {



//    SQLiteDatabase db = databaseHelper.getWritableDatabase();
//    db.beginTransaction();
//
//
//    db.setTransactionSuccessful();
//    db.endTransaction();

//    MessageNotifier.updateNotification(context, masterSecret);
  }

  public void reset(Context context) {
    DbHelper librelayOld = this.dbHelper;
    this.dbHelper = new DbHelper(context);

    this.messages.reset(dbHelper);
    this.attachments.reset(dbHelper);
    this.threads.reset(dbHelper);
    this.receipts.reset(dbHelper);
    this.drafts.reset(dbHelper);
    this.pushMessages.reset(dbHelper);
    this.groups.reset(dbHelper);
    this.recipientPreferences.reset(dbHelper);
    this.threadPreferences.reset(dbHelper);
    librelayOld.close();
  }
}
