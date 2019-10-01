package io.forsta.librelay.jobmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.forsta.librelay.R;
import io.forsta.librelay.jobmanager.dependencies.ContextDependent;
import io.forsta.librelay.jobmanager.requirements.NetworkRequirement;
import io.forsta.librelay.service.GenericForegroundService;

import java.io.Serializable;
import java.util.Collections;
import java.util.UUID;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public abstract class Job extends Worker implements Serializable {

  private static final long serialVersionUID = -4658540468214421276L;

  private static final String TAG = Job.class.getSimpleName();

  private static final WorkLockManager WORK_LOCK_MANAGER = new WorkLockManager();

  static final String KEY_RETRY_COUNT        = "Job_retry_count";
  static final String KEY_RETRY_UNTIL        = "Job_retry_until";
  static final String KEY_SUBMIT_TIME        = "Job_submit_time";
  static final String KEY_FAILED             = "Job_failed";
  static final String KEY_REQUIRES_NETWORK   = "Job_requires_network";

  private JobParameters parameters;

  public Job(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  /**
   * Invoked when a job is first created in our own codebase.
   */
  @SuppressLint("RestrictedApi")
  protected Job(@NonNull Context context, @Nullable JobParameters parameters) {
    //noinspection ConstantConditions
    super(context, new WorkerParameters(null, null, Collections.emptySet(), null, 0, null, null, null));
    this.parameters = parameters;
  }

  @Override
  public @NonNull Result doWork() {
    log("doWork()" + logSuffix());

    try (WorkLockManager.WorkLock workLock = WORK_LOCK_MANAGER.acquire(getId())) {
      Result result = workLock.getResult();

      if (result == null) {
        result = doWorkInternal();
        workLock.setResult(result);
      } else {
        log("Using result from preempted run (" + result + ")." + logSuffix());
      }

      return result;
    }
  }

  private @NonNull Result doWorkInternal() {
    Data data = getInputData();

    log("doWorkInternal()" + logSuffix());

    if (this instanceof ContextDependent) {
      ((ContextDependent)this).setContext(getApplicationContext());
    }

    try {
      initialize(new SafeData(data));

      if (!withinRetryLimits(data)) {
        warn("Failing after hitting the retry limit." + logSuffix());
        return cancel();
      }

      if (!requirementsMet(data)) {
        log("Retrying due to unmet requirements." + logSuffix());
        return retry();
      }

      onRun();

      log("Successfully completed." + logSuffix());
      return Result.SUCCESS;
    } catch (Exception e) {
      if (onShouldRetry(e)) {
        log("Retrying after a retryable exception." + logSuffix(), e);
        return retry();
      }
      warn("Failing due to an exception." + logSuffix(), e);
      return cancel();
    }
  }

  @Override
  public void onStopped() {
    log("onStopped()" + logSuffix());
  }

  final void onSubmit(@NonNull Context context, @NonNull UUID id) {
    Log.i(TAG, buildLog(id, "onSubmit() network: " + (new NetworkRequirement(getApplicationContext()).isPresent())));

    if (this instanceof ContextDependent) {
      ((ContextDependent) this).setContext(context);
    }

    onAdded();
  }

  /**
   * Called after a run has finished and we've determined a retry is required, but before the next
   * attempt is run.
   */
  protected void onRetry() { }

  /**
   * Called after a job has been added to the JobManager queue. Invoked off the main thread, so its
   * safe to do longer-running work. However, work should finish relatively quickly, as it will
   * block the submission of future tasks.
   */
  protected void onAdded() { }

  /**
   * All instance state needs to be persisted in the provided {@link Data.Builder} so that it can
   * be restored in {@link #initialize(SafeData)}.
   * @param dataBuilder The builder where you put your state.
   * @return The result of {@code dataBuilder.build()}.
   */
  protected abstract @NonNull Data serialize(@NonNull Data.Builder dataBuilder);

  /**
   * Restore all of your instance state from the provided {@link Data}. It should contain all of
   * the data put in during {@link #serialize(Data.Builder)}.
   * @param data Where your data is stored.
   */
  protected abstract void initialize(@NonNull SafeData data);

  /**
   * Called to actually execute the job.
   * @throws Exception
   */
  public abstract void onRun() throws Exception;

  /**
   * Called if a job fails to run (onShouldRetry returned false, or the number of retries exceeded
   * the job's configured retry count.
   */
  protected abstract void onCanceled();

  /**
   * If onRun() throws an exception, this method will be called to determine whether the
   * job should be retried.
   *
   * @param exception The exception onRun() threw.
   * @return true if onRun() should be called again, false otherwise.
   */
  protected abstract boolean onShouldRetry(Exception exception);

  @Nullable JobParameters getJobParameters() {
    return parameters;
  }

  private Result retry() {
    onRetry();
    return Result.RETRY;
  }

  private Result cancel() {
    onCanceled();
    return Result.SUCCESS;
  }

  private boolean requirementsMet(@NonNull Data data) {
    boolean met = true;
    return met;
  }

  private boolean withinRetryLimits(@NonNull Data data) {
    int  retryCount = data.getInt(KEY_RETRY_COUNT, 0);
    long retryUntil = data.getLong(KEY_RETRY_UNTIL, 0);

    if (retryCount > 0) {
      return getRunAttemptCount() <= retryCount;
    }

    return System.currentTimeMillis() < retryUntil;
  }

  private void log(@NonNull String message) {
    log(message, null);
  }

  private void log(@NonNull String message, @Nullable Throwable t) {
    Log.i(TAG, buildLog(getId(), message), t);
  }

  private void warn(@NonNull String message) {
    warn(message, null);
  }

  private void warn(@NonNull String message, @Nullable Throwable t) {
    Log.w(TAG, buildLog(getId(), message), t);
  }

  private String buildLog(@NonNull UUID id, @NonNull String message) {
    return "[" + id + "] " + getClass().getSimpleName() + " :: " + message;
  }

  protected String logSuffix() {
    long timeSinceSubmission = System.currentTimeMillis() - getInputData().getLong(KEY_SUBMIT_TIME, 0);
    return " (Time since submission: " + timeSinceSubmission + " ms, Run attempt: " + getRunAttemptCount() + ", isStopped: " + isStopped() + ")";
  }
}