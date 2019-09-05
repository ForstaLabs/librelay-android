package io.forsta.librelay.atlas;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by jlewis on 5/8/17.
 */

public class AtlasSyncService extends Service {

  private static AtlasSyncAdapter syncAdapter = null;
  private static final Object syncLock = new Object();

  @Override
  public void onCreate() {
    synchronized (syncLock) {
      if (syncAdapter == null) {
        syncAdapter = new AtlasSyncAdapter(getApplicationContext(), true);
      }
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {

    return syncAdapter.getSyncAdapterBinder();
  }
}
