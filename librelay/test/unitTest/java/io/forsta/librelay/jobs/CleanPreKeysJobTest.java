package io.forsta.librelay.jobs;

import org.junit.Test;
import io.forsta.securesms.BaseUnitTest;
import io.forsta.librelay.service.ForstaServiceAccountManager;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class CleanPreKeysJobTest extends BaseUnitTest {
  @Test
  public void testSignedPreKeyRotationNotRegistered() throws IOException {
    ForstaServiceAccountManager accountManager    = mock(ForstaServiceAccountManager.class);
    SignedPreKeyStore           signedPreKeyStore = mock(SignedPreKeyStore.class);
    when(accountManager.getSignedPreKey()).thenReturn(null);

    CleanPreKeysJob cleanPreKeysJob = new CleanPreKeysJob(context);
    cleanPreKeysJob.onRun();

    verify(accountManager).getSignedPreKey();
    verifyNoMoreInteractions(signedPreKeyStore);
  }

  @Test
  public void testSignedPreKeyEviction() throws Exception {
    SignedPreKeyStore           signedPreKeyStore         = mock(SignedPreKeyStore.class);
    ForstaServiceAccountManager accountManager            = mock(ForstaServiceAccountManager.class);
    SignedPreKeyEntity          currentSignedPreKeyEntity = mock(SignedPreKeyEntity.class);

    when(currentSignedPreKeyEntity.getKeyId()).thenReturn(3133);
    when(accountManager.getSignedPreKey()).thenReturn(currentSignedPreKeyEntity);

    final SignedPreKeyRecord currentRecord = new SignedPreKeyRecord(3133, System.currentTimeMillis(), Curve.generateKeyPair(), new byte[64]);

    List<SignedPreKeyRecord> records = new LinkedList<SignedPreKeyRecord>() {{
      add(new SignedPreKeyRecord(2, 11, Curve.generateKeyPair(), new byte[32]));
      add(new SignedPreKeyRecord(4, System.currentTimeMillis() - 100, Curve.generateKeyPair(), new byte[64]));
      add(currentRecord);
      add(new SignedPreKeyRecord(3, System.currentTimeMillis() - 90, Curve.generateKeyPair(), new byte[64]));
      add(new SignedPreKeyRecord(1, 10, Curve.generateKeyPair(), new byte[32]));
    }};

    when(signedPreKeyStore.loadSignedPreKeys()).thenReturn(records);
    when(signedPreKeyStore.loadSignedPreKey(eq(3133))).thenReturn(currentRecord);

    CleanPreKeysJob cleanPreKeysJob = new CleanPreKeysJob(context);
    cleanPreKeysJob.onRun();

    verify(signedPreKeyStore).removeSignedPreKey(eq(1));
    verify(signedPreKeyStore, times(1)).removeSignedPreKey(anyInt());
  }

  @Test
  public void testSignedPreKeyNoEviction() throws Exception {
    SignedPreKeyStore           signedPreKeyStore         = mock(SignedPreKeyStore.class);
    ForstaServiceAccountManager accountManager            = mock(ForstaServiceAccountManager.class);
    SignedPreKeyEntity          currentSignedPreKeyEntity = mock(SignedPreKeyEntity.class);

    when(currentSignedPreKeyEntity.getKeyId()).thenReturn(3133);
    when(accountManager.getSignedPreKey()).thenReturn(currentSignedPreKeyEntity);

    final SignedPreKeyRecord currentRecord = new SignedPreKeyRecord(3133, System.currentTimeMillis(), Curve.generateKeyPair(), new byte[64]);

    List<SignedPreKeyRecord> records = new LinkedList<SignedPreKeyRecord>() {{
      add(currentRecord);
    }};

    when(signedPreKeyStore.loadSignedPreKeys()).thenReturn(records);
    when(signedPreKeyStore.loadSignedPreKey(eq(3133))).thenReturn(currentRecord);

    CleanPreKeysJob cleanPreKeysJob = new CleanPreKeysJob(context);
    verify(signedPreKeyStore, never()).removeSignedPreKey(anyInt());
  }

  @Test
  public void testConnectionError() throws Exception {
    SignedPreKeyStore        signedPreKeyStore = mock(SignedPreKeyStore.class);
    ForstaServiceAccountManager accountManager    = mock(ForstaServiceAccountManager.class);

    when(accountManager.getSignedPreKey()).thenThrow(new PushNetworkException("Connectivity error!"));

    CleanPreKeysJob cleanPreKeysJob = new CleanPreKeysJob(context);
    try {
      cleanPreKeysJob.onRun();
      throw new AssertionError("should have failed!");
    } catch (IOException e) {
      assertTrue(cleanPreKeysJob.onShouldRetry(e));
    }
  }
}
