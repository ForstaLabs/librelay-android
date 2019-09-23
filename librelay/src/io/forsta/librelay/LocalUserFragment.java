package io.forsta.librelay;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.forsta.librelay.atlas.model.AtlasUser;
import io.forsta.librelay.components.AvatarImageView;
import io.forsta.librelay.recipients.Recipient;
import io.forsta.librelay.recipients.RecipientFactory;
import io.forsta.librelay.util.TextSecurePreferences;

/**
 * Created by jlewis on 5/19/17.
 */

public class LocalUserFragment extends Fragment {
  private static final String TAG = LocalUserFragment.class.getSimpleName();
  private TextView userName;
  private TextView orgTag;
  private AvatarImageView contactPhotoImage;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

    final View view = inflater.inflate(R.layout.forsta_local_user_fragment, container, false);
    userName = (TextView) view.findViewById(R.id.local_user);
    orgTag = (TextView) view.findViewById(R.id.local_org_tag);
    contactPhotoImage = (AvatarImageView) view.findViewById(R.id.local_photo_image);
    String tsServer = TextSecurePreferences.getServer(getContext());

    final AtlasUser user = AtlasUser.getLocalUser(getActivity());
    if (user != null) {
      userName.setText(user.getName());
      orgTag.setText("@" + user.getTag() + ":" + user.getOrgTag());
      Recipient recipient = RecipientFactory.getRecipient(getContext(), user.uid, false);
      contactPhotoImage.setAvatar(recipient, false);
    }

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
  }
}
