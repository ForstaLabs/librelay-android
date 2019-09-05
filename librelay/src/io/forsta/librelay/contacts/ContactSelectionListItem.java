package io.forsta.librelay.contacts;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.forsta.librelay.database.DbFactory;
import io.forsta.librelay.database.GroupDatabase;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.R;
import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;
import io.forsta.librelay.util.ViewUtil;

public class ContactSelectionListItem extends LinearLayout implements Recipient.RecipientModifiedListener {

  private AvatarImageView contactPhotoImage;
  private TextView        numberView;
  private TextView        nameView;
  private TextView        labelView;
  private CheckBox        checkBox;

  private long       id;
  private String     address;
  private int type;
  private Recipient recipient;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.numberView        = (TextView)        findViewById(R.id.number);
    this.labelView         = (TextView)        findViewById(R.id.label);
    this.nameView          = (TextView)        findViewById(R.id.name);
    this.checkBox          = (CheckBox)        findViewById(R.id.check_box);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(long id, int type, String name, String address, String label, int color, boolean multiSelect) {
    this.id     = id;
    this.address = address;
    this.type = type;

    if (type == 2) {
      GroupDatabase.GroupRecord group = DbFactory.getGroupDatabase(getContext()).getGroup(address);
      name = group.getTitle();
    } else {
      this.recipient = RecipientFactory.getRecipient(getContext(), address, false);

      if (this.recipient != null &&
          this.recipient.getName() != null)
      {
        name = this.recipient.getName();
      }
      this.recipient.addListener(this);
      this.contactPhotoImage.setAvatar(recipient, true);
    }
    this.nameView.setTextColor(color);
    this.numberView.setTextColor(color);

    setText(name, address, label);

    if (multiSelect) {
      this.checkBox.setVisibility(View.VISIBLE);
    } else {
      this.checkBox.setVisibility(View.GONE);
    }
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
      recipient = null;
    }
  }

  private void setText(String name, String number, String label) {
    if (number == null || number.isEmpty()) {
      this.numberView.setText("");
      this.labelView.setVisibility(View.GONE);
    } else {
      this.numberView.setText(number);
      this.labelView.setText(label);
      this.labelView.setVisibility(View.VISIBLE);
    }
    this.nameView.setEnabled(true);
    this.nameView.setText(name);
  }

  public long getContactId() {
    return id;
  }

  public String getAddress() {
    return address;
  }

  public int getType() {
    return type;
  }

  @Override
  public void onModified(final Recipient recipient) {
    if (this.recipient == recipient) {
      this.contactPhotoImage.post(new Runnable() {
        @Override
        public void run() {
          contactPhotoImage.setAvatar(recipient, true);
//          nameView.setText(recipients.toShortString());
        }
      });
    }
  }
}
