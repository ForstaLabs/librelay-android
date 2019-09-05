package io.forsta.librelay.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.database.MessageDatabase.SyncMessageId;
import io.forsta.librelay.dependencies.ApplicationDependencies;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.util.JsonUtils;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MultiDeviceReadUpdateJob extends ContextJob {

  private static final long serialVersionUID = 1L;
  private static final String TAG = MultiDeviceReadUpdateJob.class.getSimpleName();
  private static final String KEY_MESSAGE_IDS = "message_ids";

  private List<SerializableSyncMessageId> messageIds;

  public MultiDeviceReadUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MultiDeviceReadUpdateJob(Context context, List<SyncMessageId> messageIds) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .create());

    this.messageIds = new LinkedList<>();

    for (SyncMessageId messageId : messageIds) {
      this.messageIds.add(new SerializableSyncMessageId(messageId.getAddress(), messageId.getTimetamp()));
    }
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.w(TAG, "Not multi device...");
      return;
    }

    List<ReadMessage> readMessages = new LinkedList<>();

    for (SerializableSyncMessageId messageId : messageIds) {
      readMessages.add(new ReadMessage(messageId.sender, messageId.timestamp));
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    messageSender.sendMessage(SignalServiceSyncMessage.forRead(readMessages));
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onAdded() {

  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    String[] ids = data.getStringArray(KEY_MESSAGE_IDS);

    messageIds = new ArrayList<>(ids.length);
    for (String id : ids) {
      try {
        messageIds.add(JsonUtils.fromJson(id, SerializableSyncMessageId.class));
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    String[] ids = new String[messageIds.size()];

    for (int i = 0; i < ids.length; i++) {
      try {
        ids[i] = JsonUtils.toJson(messageIds.get(i));
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    return dataBuilder.putStringArray(KEY_MESSAGE_IDS, ids).build();
  }

  @Override
  public void onCanceled() {

  }

  private static class SerializableSyncMessageId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty
    private final String sender;

    @JsonProperty
    private final long   timestamp;

    private SerializableSyncMessageId(@JsonProperty("sender") String sender, @JsonProperty("timestamp") long timestamp) {
      this.sender = sender;
      this.timestamp = timestamp;
    }
  }
}
