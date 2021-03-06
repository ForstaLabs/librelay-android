package io.forsta.librelay.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.forsta.librelay.R;

/**
 * Created by john on 9/30/2017.
 */

public class SelectedRecipient extends LinearLayout {
  private ImageButton removeButton;
  private TextView selectedTag;
  private String address;

  public SelectedRecipient(Context context) {
    this(context, null);
  }

  public SelectedRecipient(Context context, AttributeSet attrs) {
    super(context, attrs);
    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.new_conversation_selected_recipient, this, true);
    initializeViews();
  }


  private void initializeViews() {
    removeButton = (ImageButton) findViewById(R.id.selected_recipient_remove);
    removeButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        performClick();
      }
    });
    selectedTag = (TextView) findViewById(R.id.selected_recipient_tag);
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  public void setText(String text) {
    selectedTag.setText(text);
  }

  public String getText() {
    return selectedTag.getText().toString();
  }
}
