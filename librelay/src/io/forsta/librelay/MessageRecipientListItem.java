/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.librelay;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import io.forsta.librelay.crypto.AcceptIdentityMismatch;
import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.components.FromTextView;
import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.MessageDatabase;
import io.forsta.librelay.database.documents.IdentityKeyMismatch;
import io.forsta.librelay.database.documents.NetworkFailure;
import io.forsta.librelay.database.model.MessageReceipt;
import io.forsta.librelay.database.model.MessageRecord;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.messaging.MessageSender;

/**
 * A simple view to show the recipients of a message
 *
 * @author Jake McGinty
 */
public class MessageRecipientListItem extends RelativeLayout
    implements Recipient.RecipientModifiedListener
{
  private final static String TAG = MessageRecipientListItem.class.getSimpleName();

  private Recipient       recipient;
  private FromTextView    fromView;
  private TextView        errorDescription;
  private TextView receiptStatus;
  private Button          conflictButton;
  private Button          resendButton;
  private AvatarImageView contactPhotoImage;

  private final Handler handler = new Handler();

  public MessageRecipientListItem(Context context) {
    super(context);
  }

  public MessageRecipientListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.fromView          = (FromTextView)    findViewById(R.id.from);
    this.errorDescription  = (TextView)        findViewById(R.id.error_description);
    this.receiptStatus = findViewById(R.id.receipt_status);
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.conflictButton    = (Button)          findViewById(R.id.conflict_button);
    this.resendButton      = (Button)          findViewById(R.id.resend_button);

  }

  public void set(final MessageRecord record,
                  final Recipient recipient)
  {
    this.recipient = recipient;

    recipient.addListener(this);
    fromView.setText(recipient);
    contactPhotoImage.setAvatar(recipient, false);
    setIssueIndicators(record);

    if (record.isOutgoing()) {
      MessageReceipt receipt = record.getMessageReceipt(recipient.getAddress());
      if (receipt != null) {
        receiptStatus.setVisibility(VISIBLE);
        if (receipt.isRead()) {
          receiptStatus.setText("Read");
        } else if (receipt.isDelivered()) {
          receiptStatus.setText("Delivered");
        } else {
          receiptStatus.setText("Sending");
        }
      }
    }
  }

  private void setIssueIndicators(final MessageRecord record)
  {
    final NetworkFailure      networkFailure = getNetworkFailure(record);
    final IdentityKeyMismatch keyMismatch    = networkFailure == null ? getKeyMismatch(record) : null;

    String errorText = "";

    if (keyMismatch != null) {
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.VISIBLE);

      errorText = getContext().getString(R.string.MessageDetailsRecipient_new_safety_numbers);
      conflictButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          new AcceptIdentityMismatch(getContext(), record, keyMismatch).execute();
        }
      });
    } else if (networkFailure != null && record.isFailed()) {
      resendButton.setVisibility(View.VISIBLE);
      resendButton.setEnabled(true);
      resendButton.requestFocus();
      conflictButton.setVisibility(View.GONE);

      errorText = getContext().getString(R.string.MessageDetailsRecipient_failed_to_send);
      resendButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          resendButton.setEnabled(false);
          new ResendAsyncTask(record, networkFailure).execute();
        }
      });
    } else {
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.GONE);
    }

    errorDescription.setText(errorText);
    errorDescription.setVisibility(TextUtils.isEmpty(errorText) ? View.GONE : View.VISIBLE);
  }

  private NetworkFailure getNetworkFailure(final MessageRecord record) {
    if (record.hasNetworkFailures()) {
      for (final NetworkFailure failure : record.getNetworkFailures()) {
        if (failure.getRecipientId() == recipient.getRecipientId()) {
          return failure;
        }
      }
    }
    return null;
  }

  private IdentityKeyMismatch getKeyMismatch(final MessageRecord record) {
    if (record.isIdentityMismatchFailure()) {
      for (final IdentityKeyMismatch mismatch : record.getIdentityKeyMismatches()) {
        if (mismatch.getRecipientId() == recipient.getRecipientId()) {
          return mismatch;
        }
      }
    }
    return null;
  }

  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  @Override
  public void onModified(final Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipient);
        contactPhotoImage.setAvatar(recipient, false);
      }
    });
  }

  private class ResendAsyncTask extends AsyncTask<Void,Void,Void> {
    private final MessageRecord  record;
    private final NetworkFailure failure;

    public ResendAsyncTask(MessageRecord record, NetworkFailure failure) {
      this.record       = record;
      this.failure      = failure;
    }

    @Override
    protected Void doInBackground(Void... params) {
      MessageDatabase messageDatabase = DbFactory.getMessageDatabase(getContext());
      messageDatabase.removeFailure(record.getId(), failure);

      MessageSender.resend(getContext(), record);

      return null;
    }
  }

}
