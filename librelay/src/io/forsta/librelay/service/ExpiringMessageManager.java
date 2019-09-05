package io.forsta.librelay.service;

import android.content.Context;
import android.util.Log;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.model.MessageRecord;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExpiringMessageManager {

  private static final String TAG = ExpiringMessageManager.class.getSimpleName();

  private final TreeSet<ExpiringMessageReference> expiringMessageReferences = new TreeSet<>(new ExpiringMessageComparator());
  private final Executor                          executor                  = Executors.newSingleThreadExecutor();

  private final MessageDatabase messageDatabase;
  private final Context     context;

  public ExpiringMessageManager(Context context) {
    this.context     = context.getApplicationContext();
    this.messageDatabase = DbFactory.getMessageDatabase(context);

    executor.execute(new LoadTask());
    executor.execute(new ProcessTask());
  }

  public void scheduleDeletion(long id, long expiresInMillis) {
    scheduleDeletion(id, System.currentTimeMillis(), expiresInMillis);
  }

  public void scheduleDeletion(long id, long startedAtTimestamp, long expiresInMillis) {
    long expiresAtMillis = startedAtTimestamp + expiresInMillis;

    synchronized (expiringMessageReferences) {
      expiringMessageReferences.add(new ExpiringMessageReference(id, expiresAtMillis));
      expiringMessageReferences.notifyAll();
    }
  }

  public void checkSchedule() {
    synchronized (expiringMessageReferences) {
      expiringMessageReferences.notifyAll();
    }
  }

  private class LoadTask implements Runnable {
    public void run() {
      MessageDatabase.Reader messageReader = messageDatabase.getExpireStartedMessages();

      MessageRecord messageRecord = null;

      while ((messageRecord = messageReader.getNext()) != null) {
        expiringMessageReferences.add(new ExpiringMessageReference(messageRecord.getId(),
                                                                   messageRecord.getExpireStarted() + messageRecord.getExpiresIn()));
      }
      messageReader.close();
    }
  }

  private class ProcessTask implements Runnable {
    public void run() {
      while (true) {
        ExpiringMessageReference expiredMessage = null;

        synchronized (expiringMessageReferences) {
          try {
            while (expiringMessageReferences.isEmpty()) expiringMessageReferences.wait();

            ExpiringMessageReference nextReference = expiringMessageReferences.first();
            long                     waitTime      = nextReference.expiresAtMillis - System.currentTimeMillis();

            if (waitTime > 0) {
              ExpirationListener.setAlarm(context, waitTime);
              expiringMessageReferences.wait(waitTime);
            } else {
              expiredMessage = nextReference;
              expiringMessageReferences.remove(nextReference);
            }

          } catch (InterruptedException e) {
            Log.w(TAG, e);
          }
        }

        if (expiredMessage != null) {
          messageDatabase.delete(expiredMessage.id);
        }
      }
    }
  }

  private static class ExpiringMessageReference {
    private final long    id;
    private final long    expiresAtMillis;

    private ExpiringMessageReference(long id, long expiresAtMillis) {
      this.id = id;
      this.expiresAtMillis = expiresAtMillis;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) return false;
      if (!(other instanceof ExpiringMessageReference)) return false;

      ExpiringMessageReference that = (ExpiringMessageReference)other;
      return this.id == that.id && this.expiresAtMillis == that.expiresAtMillis;
    }

    @Override
    public int hashCode() {
      return (int)this.id ^ (int)expiresAtMillis;
    }
  }

  private static class ExpiringMessageComparator implements Comparator<ExpiringMessageReference> {
    @Override
    public int compare(ExpiringMessageReference lhs, ExpiringMessageReference rhs) {
      if      (lhs.expiresAtMillis < rhs.expiresAtMillis) return -1;
      else if (lhs.expiresAtMillis > rhs.expiresAtMillis) return 1;
      else if (lhs.id < rhs.id)                           return -1;
      else if (lhs.id > rhs.id)                           return 1;
      else                                                return 0;
    }
  }

}
