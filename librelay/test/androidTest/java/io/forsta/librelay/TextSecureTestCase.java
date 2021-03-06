package io.forsta.librelay;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class TextSecureTestCase extends InstrumentationTestCase {

  @Override
  public void setUp() throws Exception {
    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
  }

  protected Context getContext() {
    return getInstrumentation().getContext();
  }
}
