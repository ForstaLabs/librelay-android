package io.forsta.librelay.export;

import android.content.Context;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.util.Log;

import io.forsta.librelay.database.MessageDatabase;

import java.io.File;
import java.io.IOException;


public class PlaintextBackupImporter {

  public static void importPlaintextFromSd(Context context)
      throws NoExternalStorageException, IOException
  {
    Log.w("PlaintextBackupImporter", "Importing plaintext...");
    verifyExternalStorageForPlaintextImport();
    importPlaintext(context);
  }

  private static void verifyExternalStorageForPlaintextImport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canRead() || !getPlaintextExportFile().exists())
      throw new NoExternalStorageException();
  }

  private static File getPlaintextExportFile() {
    File backup    = PlaintextBackupExporter.getPlaintextExportFile();
    File oldBackup = new File(Environment.getExternalStorageDirectory(), "TextSecurePlaintextBackup.xml");

    if (!backup.exists() && oldBackup.exists()) {
      return oldBackup;
    }
    return backup;
  }

  private static void importPlaintext(Context context)
      throws IOException
  {
    Log.w("PlaintextBackupImporter", "NOT IMPLEMENTED");
//    Log.w("PlaintextBackupImporter", "importPlaintext()");
//    MessageDatabase db          = DbFactory.getMmsDatabase(context);
//    SQLiteDatabase transaction = db.beginTransaction();
//
//    try {
//      ThreadDatabase threads         = DbFactory.getThreadDatabase(context);
//      XmlBackup      backup          = new XmlBackup(getPlaintextExportFile().getAbsolutePath());
//      Set<Long>      modifiedThreads = new HashSet<Long>();
//      XmlBackup.XmlBackupItem item;
//
//      while ((item = backup.getNext()) != null) {
//        Recipients recipients = RecipientFactory.getRecipientsFromString(context, item.getAddress(), false);
//        long            threadId   = threads.getThreadIdFor(recipients);
//        SQLiteStatement statement  = db.createInsertStatement(transaction);
//
//        if (item.getAddress() == null || item.getAddress().equals("null"))
//          continue;
//
//        if (!isAppropriateTypeForImport(item.getType()))
//          continue;
//
//        addStringToStatement(statement, 1, item.getAddress());
//        addNullToStatement(statement, 2);
//        addLongToStatement(statement, 3, item.getDate());
//        addLongToStatement(statement, 4, item.getDate());
//        addLongToStatement(statement, 5, item.getProtocol());
//        addLongToStatement(statement, 6, item.getRead());
//        addLongToStatement(statement, 7, item.getStatus());
//        addTranslatedTypeToStatement(statement, 8, item.getType());
//        addNullToStatement(statement, 9);
//        addStringToStatement(statement, 10, item.getSubject());
//        addEncryptedStingToStatement(masterCipher, statement, 11, item.getBody());
//        addStringToStatement(statement, 12, item.getServiceCenter());
//        addLongToStatement(statement, 13, threadId);
//        modifiedThreads.add(threadId);
//        statement.execute();
//      }
//
//      for (long threadId : modifiedThreads) {
//        threads.update(threadId, true);
//      }
//
//      Log.w("PlaintextBackupImporter", "Exited loop");
//    } catch (XmlPullParserException e) {
//      Log.w("PlaintextBackupImporter", e);
//      throw new IOException("XML Parsing error!");
//    } finally {
//      db.endTransaction(transaction);
//    }
  }

  private static void addEncryptedStingToStatement(SQLiteStatement statement, int index, String value) {
    if (value == null || value.equals("null")) {
      statement.bindNull(index);
    } else {
      statement.bindString(index, value);
    }
  }

  private static void addTranslatedTypeToStatement(SQLiteStatement statement, int index, int type) {
    statement.bindLong(index, MessageDatabase.Types.translateFromSystemBaseType(type) | MessageDatabase.Types.ENCRYPTION_SYMMETRIC_BIT);
  }

  private static void addStringToStatement(SQLiteStatement statement, int index, String value) {
    if (value == null || value.equals("null")) statement.bindNull(index);
    else                                       statement.bindString(index, value);
  }

  private static void addNullToStatement(SQLiteStatement statement, int index) {
    statement.bindNull(index);
  }

  private static void addLongToStatement(SQLiteStatement statement, int index, long value) {
    statement.bindLong(index, value);
  }

  private static boolean isAppropriateTypeForImport(long theirType) {
    long ourType = MessageDatabase.Types.translateFromSystemBaseType(theirType);

    return ourType == MessageDatabase.Types.BASE_INBOX_TYPE ||
           ourType == MessageDatabase.Types.BASE_SENT_TYPE ||
           ourType == MessageDatabase.Types.BASE_SENT_FAILED_TYPE;
  }


}
