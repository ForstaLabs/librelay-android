package io.forsta.librelay.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import io.forsta.librelay.R;
import io.forsta.librelay.util.TextSecurePreferences;

public class ComposeText extends AppCompatEditText {

  private SpannableString hint;
  private SpannableString subHint;
  private GiphySelectListener giphyListener;

  public ComposeText(Context context) {
    super(context);
  }

  public ComposeText(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ComposeText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setGiphySelectListener(GiphySelectListener listener) {
    this.giphyListener = listener;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    final InputConnection ic = super.onCreateInputConnection(outAttrs);
    EditorInfoCompat.setContentMimeTypes(outAttrs,
        new String[] {"image/gif"});
    return InputConnectionCompat.createWrapper(ic, outAttrs, new InputConnectionCompat.OnCommitContentListener() {
      @Override
      public boolean onCommitContent(InputContentInfoCompat inputContentInfoCompat, int i, Bundle bundle) {
        Log.w("ComposeText", "Giphy: " + inputContentInfoCompat.getContentUri());
        if (giphyListener != null) {
          giphyListener.onSelect(inputContentInfoCompat.getContentUri());
          return true;
        }
        return false;
      }
    });
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    if (!TextUtils.isEmpty(hint)) {
      if (!TextUtils.isEmpty(subHint)) {
        setHint(new SpannableStringBuilder().append(ellipsizeToWidth(hint))
                                            .append("\n")
                                            .append(ellipsizeToWidth(subHint)));
      } else {
        setHint(ellipsizeToWidth(hint));
      }
    }
  }

  private CharSequence ellipsizeToWidth(CharSequence text) {
    return TextUtils.ellipsize(text,
                               getPaint(),
                               getWidth() - getPaddingLeft() - getPaddingRight(),
                               TruncateAt.END);
  }

  public void setHint(@NonNull String hint, @Nullable CharSequence subHint) {
    this.hint = new SpannableString(hint);
    this.hint.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

    if (subHint != null) {
      this.subHint = new SpannableString(subHint);
      this.subHint.setSpan(new RelativeSizeSpan(0.8f), 0, subHint.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    } else {
      this.subHint = null;
    }

    if (this.subHint != null) {
      super.setHint(new SpannableStringBuilder().append(ellipsizeToWidth(this.hint))
                                                .append("\n")
                                                .append(ellipsizeToWidth(this.subHint)));
    } else {
      super.setHint(ellipsizeToWidth(this.hint));
    }
  }

  public void appendInvite(String invite) {
    if (!TextUtils.isEmpty(getText()) && !getText().toString().equals(" ")) {
      append(" ");
    }

    append(invite);
    setSelection(getText().length());
  }

  private boolean isLandscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private void initialize() {
    final boolean enterSends     = TextSecurePreferences.isEnterSendsEnabled(getContext());
    final boolean useSystemEmoji = TextSecurePreferences.isSystemEmojiPreferred(getContext());

    int imeOptions = (getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
    int inputType  = getInputType();

    if (isLandscape()) setImeActionLabel(getContext().getString(R.string.conversation_activity__type_message_push), EditorInfo.IME_ACTION_SEND);
    else               setImeActionLabel(getContext().getString(R.string.conversation_activity__type_message_push), 0);

    if (useSystemEmoji) {
      inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
    }

    inputType  = !isLandscape() && enterSends
        ? inputType & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE
        : inputType | InputType.TYPE_TEXT_FLAG_MULTI_LINE;

    imeOptions = enterSends
        ? imeOptions & ~EditorInfo.IME_FLAG_NO_ENTER_ACTION
        : imeOptions | EditorInfo.IME_FLAG_NO_ENTER_ACTION;

    setInputType(inputType);
    setImeOptions(imeOptions);
    setHint(getContext().getString(R.string.conversation_activity__type_message_push), null);
  }

  public interface GiphySelectListener {
    void onSelect(Uri uri);
  }
}
