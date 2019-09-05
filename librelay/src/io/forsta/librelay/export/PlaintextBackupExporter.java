package io.forsta.librelay.export;


import android.content.Context;
import android.os.Environment;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.model.MessageRecord;

import java.io.File;
import java.io.IOException;

public class PlaintextBackupExporter {

  private static final String FILENAME = "SignalPlaintextBackup.xml";

  public static void exportPlaintextToSd(Context context)
      throws NoExternalStorageException, IOException
  {
    verifyExternalStorageForPlaintextExport();
    exportPlaintext(context);
  }

  private static void verifyExternalStorageForPlaintextExport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();
  }

  public static File getPlaintextExportFile() {
    return new File(Environment.getExternalStorageDirectory(), FILENAME);
  }

  private static void exportPlaintext(Context context)
      throws IOException
  {
    int count               = DbFactory.getMessageDatabase(context).getMessageCount();
    XmlBackup.Writer writer = new XmlBackup.Writer(getPlaintextExportFile().getAbsolutePath(), count);


    MessageRecord record;
    MessageDatabase.Reader reader = null;
    int skip                            = 0;
    int ROW_LIMIT                       = 500;

    do {
      if (reader != null)
        reader.close();

      reader = DbFactory.getMessageDatabase(context).getMessages(skip, ROW_LIMIT);

      while ((record = reader.getNext()) != null) {
        XmlBackup.XmlBackupItem item =
            new XmlBackup.XmlBackupItem(0, record.getIndividualRecipient().getAddress(),
                                        record.getDateReceived(),
                                        MessageDatabase.Types.translateToSystemBaseType(record.getType()),
                                        null, record.getDisplayBody().toString(), null,
                                        1, record.getDeliveryStatus());

        writer.writeItem(item);
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);

    writer.close();
  }
}
