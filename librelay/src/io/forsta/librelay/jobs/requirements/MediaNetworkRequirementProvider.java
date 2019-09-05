package io.forsta.librelay.jobs.requirements;

import io.forsta.librelay.jobmanager.requirements.RequirementListener;
import io.forsta.librelay.jobmanager.requirements.RequirementProvider;

public class MediaNetworkRequirementProvider implements RequirementProvider {

  private RequirementListener listener;

  public void notifyMediaControlEvent() {
    if (listener != null) listener.onRequirementStatusChanged();
  }

  @Override
  public void setListener(RequirementListener listener) {
    this.listener = listener;
  }
}
