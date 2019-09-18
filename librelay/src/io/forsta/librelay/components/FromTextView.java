package io.forsta.librelay.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;

import io.forsta.librelay.R;
import io.forsta.librelay.components.emoji.EmojiTextView;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.recipients.Recipients;

public class FromTextView extends EmojiTextView {

  private static final String TAG = FromTextView.class.getSimpleName();

  public FromTextView(Context context) {
    super(context);
  }

  public FromTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setText(Recipient recipient) {
    setText(RecipientFactory.getRecipientsFor(getContext(), recipient, true));
  }

  public void setText(Recipients recipients) {
    setText(recipients, true);
  }

  public void setText(Recipients recipients, boolean read) {
    int        attributes[]   = new int[]{R.attr.conversation_list_item_count_color};
    TypedArray colors         = getContext().obtainStyledAttributes(attributes);
    String fromString = recipients != null ? recipients.toCondensedString(getContext()) : "No Recipients";

    int typeface;

    if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder(fromString);
    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

    colors.recycle();

    setText(builder);
    setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    // Blocked setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_grey600_18dp, 0, 0, 0);
  }

  public void setText(String title, boolean read) {
    int typeface;
    if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder(title);
    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    setText(builder);
  }

}
