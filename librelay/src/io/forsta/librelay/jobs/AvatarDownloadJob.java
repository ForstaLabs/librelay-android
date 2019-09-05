package io.forsta.librelay.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.GroupDatabase;
import io.forsta.librelay.jobmanager.JobParameters;
import io.forsta.librelay.jobmanager.SafeData;
import io.forsta.librelay.media.AttachmentStreamUriLoader.AttachmentModel;
import io.forsta.librelay.util.BitmapDecodingException;
import io.forsta.librelay.util.BitmapUtil;
import io.forsta.librelay.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class AvatarDownloadJob extends ContextJob {

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private static final String KEY_GROUP_ID = "group_id";

  private String groupId;

  public AvatarDownloadJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public AvatarDownloadJob(Context context, String groupId) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  protected void initialize(@NonNull SafeData data) {
    groupId = KEY_GROUP_ID;
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putString(KEY_GROUP_ID, groupId).build();
  }

  @Override
  public void onRun() throws IOException {
    GroupDatabase             database   = DbFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long   avatarId = record.getAvatarId();
        byte[] key      = record.getAvatarKey();
        String relay    = record.getRelay();

        if (avatarId == -1 || key == null) {
          return;
        }

        attachment = downloadAttachment(relay, avatarId);
        Bitmap avatar = BitmapUtil.createScaledBitmap(context, new AttachmentModel(attachment, key), 500, 500);

        database.updateAvatar(groupId, avatar);
      }
    } catch (BitmapDecodingException | NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(TextSecurePreferences.getServer(context),
                                                     null,
                                                     new StaticCredentialsProvider(TextSecurePreferences.getLocalAddress(context),
                                                                                   TextSecurePreferences.getPushServerPassword(context),
                                                                                   null),
                                                     TextSecurePreferences.getUserAgent(context));

    File destination = File.createTempFile("avatar", "tmp");

    destination.deleteOnExit();

    socket.retrieveAttachment(relay, contentLocation, destination, null);

    return destination;
  }

}
