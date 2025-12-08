/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.services;

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.DocumentsApplication.acquireUnstableProviderOrThrow;
import static com.android.documentsui.services.FileOperationService.EXTRA_CANCEL;
import static com.android.documentsui.services.FileOperationService.EXTRA_DIALOG_TYPE;
import static com.android.documentsui.services.FileOperationService.EXTRA_FAILED_DOCS;
import static com.android.documentsui.services.FileOperationService.EXTRA_FAILED_PATHS;
import static com.android.documentsui.services.FileOperationService.EXTRA_FAILED_URIS;
import static com.android.documentsui.services.FileOperationService.EXTRA_JOB_ID;
import static com.android.documentsui.services.FileOperationService.EXTRA_OPERATION_TYPE;
import static com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.icu.text.MessageFormat;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.DeadObjectException;
import android.os.FileUtils;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.documentsui.Metrics;
import com.android.documentsui.OperationDialogFragment;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Shared;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.services.FileOperationService.OpType;

import java.io.FileNotFoundException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A mashup of work item and ui progress update factory. Used by {@link FileOperationService}
 * to do work and show progress relating to this work.
 */
abstract public class Job implements Runnable {
    private static final String TAG = "Job";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_CREATED, STATE_STARTED, STATE_SET_UP, STATE_COMPLETED, STATE_CANCELED})
    public @interface State {}
    public static final int STATE_CREATED = 0;
    public static final int STATE_STARTED = 1;
    public static final int STATE_SET_UP = 2;
    public static final int STATE_COMPLETED = 3;
    /**
     * A job is in canceled state as long as {@link #cancel()} is called on it, even after it is
     * completed.
     */
    public static final int STATE_CANCELED = 4;

    static final String INTENT_TAG_WARNING = "warning";
    static final String INTENT_TAG_FAILURE = "failure";
    static final String INTENT_TAG_PROGRESS = "progress";
    static final String INTENT_TAG_CANCEL = "cancel";

    final Context service;
    final Context appContext;
    final Listener listener;

    final @OpType int operationType;
    final String id;

    /**
     * Don't modify the referenced DocumentStack object in place, in order to avoid any data race
     * condition between the job-running thread and the progress-reporting thread. If the stack
     * needs to be modified, create a new DocumentStack object and update the following reference.
     */
    volatile DocumentStack stack;

    final UrisSupplier mResourceUris;

    /**
     * Number of errors. It is modified by the main thread running setUp(), start() and finish(),
     * and it is read by the progress reporting thread running getJobProgress().
     */
    volatile int failureCount = 0;
    final ArrayList<DocumentInfo> failedDocs = new ArrayList<>();
    final ArrayList<Uri> failedUris = new ArrayList<>();
    final ArrayList<String> failedPaths = new ArrayList<>();

    final Notification.Builder mProgressBuilder;

    final CancellationSignal mSignal = new CancellationSignal();

    /** Map of URIs to cached clients. */
    private final Map<String, ContentProviderClient> mClients = new HashMap<>();
    private final Features mFeatures;

    private @State int mState = STATE_CREATED;

    /**
     * A simple progressable job, much like an AsyncTask, but with support
     * for providing various related notification, progress and navigation information.
     * @param service The service context in which this job is running.
     * @param listener
     * @param id Arbitrary string ID
     * @param stack The documents stack context relating to this request. This is the
     *     destination in the Files app where the user will be take when the
     *     navigation intent is invoked (presumably from notification).
     * @param srcs the list of docs to operate on
     */
    Job(Context service, Listener listener, String id,
            @OpType int opType, DocumentStack stack, UrisSupplier srcs, Features features) {

        assert(opType != OPERATION_UNKNOWN);

        this.service = service;
        this.appContext = service.getApplicationContext();
        this.listener = listener;
        this.operationType = opType;

        this.id = id;
        this.stack = stack;
        this.mResourceUris = srcs;

        mFeatures = features;

        mProgressBuilder = createProgressBuilder();
    }

    @Override
    public final void run() {
        synchronized (this) {
            if (mState == STATE_CANCELED) {
                // Canceled before running
                return;
            }

            mState = STATE_STARTED;
        }

        listener.onStart(this);

        try {
            boolean ok = setUp();

            if (ok) {
                synchronized (this) {
                    if (mState == STATE_CANCELED) {
                        ok = false;
                    } else {
                        mState = STATE_SET_UP;
                    }
                }

                if (ok) start();
            }
        } catch (RuntimeException e) {
            // No exceptions should be thrown here, as all calls to the provider must be
            // handled within Job implementations. However, just in case catch them here.
            Log.e(TAG, "Operation failed due to an unhandled runtime exception.", e);
            Metrics.logFileOperationErrors(operationType, failedDocs, failedUris);
        } finally {
            synchronized (this) {
                if (mState == STATE_STARTED || mState == STATE_SET_UP) mState = STATE_COMPLETED;
            }

            finish();
            listener.onFinished(this);

            // NOTE: If this details is a JumboClipDetails, and it's still referred in primary clip
            // at this point, user won't be able to paste it to anywhere else because the underlying
            mResourceUris.dispose();
        }
    }

    boolean setUp() {
        return true;
    }

    abstract void finish();

    abstract void start();

    public abstract Notification getSetupNotification();
    public abstract Notification getProgressNotification();
    public abstract Notification getFailureNotification();

    /** Must be implemented if hasWarnings() can return true. */
    Notification getWarningNotification() {
        throw new UnsupportedOperationException();
    }

    abstract JobProgress getJobProgress();

    Uri getDataUriForIntent(String tag) {
        return Uri.parse(String.format("data,%s-%s", tag, id));
    }

    /**
     * Gets or creates a ContentProviderClient for the given URI. The returned object is cached
     * by this Job and will be closed by Job.cleanup().
     */
    @NonNull ContentProviderClient getClient(Uri uri) throws RemoteException {
        ContentProviderClient client = mClients.get(uri.getAuthority());
        if (client == null) {
            // Acquire content providers.
            client = acquireUnstableProviderOrThrow(
                    getContentResolver(),
                    uri.getAuthority());

            mClients.put(uri.getAuthority(), client);
        }

        assert client != null;
        return client;
    }

    /**
     * Gets or creates a ContentProviderClient for the given document. The returned object is
     * cached by this Job and will be closed by Job.cleanup().
     */
    @NonNull ContentProviderClient getClient(DocumentInfo doc) throws RemoteException {
        return getClient(doc.derivedUri);
    }

    /**
     * Closes and releases the ContentProviderClient that was previously created and cached for
     * the given URI. Does nothing if there is no such ContentProviderClient.
     */
    void releaseClient(Uri uri) {
        ContentProviderClient client = mClients.get(uri.getAuthority());
        if (client != null) {
            client.close();
            mClients.remove(uri.getAuthority());
        }
    }

    /**
     * Closes and releases the ContentProviderClient that was previously created and cached for
     * the given document. Does nothing if there is no such ContentProviderClient.
     */
    void releaseClient(DocumentInfo doc) {
        releaseClient(doc.derivedUri);
    }

    /** Closes and releases all the ContentProviderClient objects currently cached by this Job. */
    final void cleanup() {
        for (ContentProviderClient client : mClients.values()) {
            FileUtils.closeQuietly(client);
        }
    }

    final synchronized @State int getState() {
        return mState;
    }

    /** Requests the cancellation of this job. Can be called from any thread. */
    final void cancel() {
        synchronized (this) {
            mState = STATE_CANCELED;
        }
        mSignal.cancel();
        Metrics.logFileOperationCancelled(operationType);
    }

    final synchronized boolean isCanceled() {
        return mState == STATE_CANCELED;
    }

    final synchronized boolean isFinished() {
        return mState == STATE_CANCELED || mState == STATE_COMPLETED;
    }

    final ContentResolver getContentResolver() {
        return service.getContentResolver();
    }

    @SuppressWarnings("NonAtomicVolatileUpdate")
    void onFileFailed(DocumentInfo file) {
        // Non-atomic operation is Ok since failureCount is only modified in one thread.
        // noinspection NonAtomicOperationOnVolatileField
        failureCount++;
        failedDocs.add(file);
    }

    @SuppressWarnings("NonAtomicVolatileUpdate")
    void onFileFailed(@NonNull Collection<? extends DocumentInfo> files) {
        // Non-atomic operation is Ok since failureCount is only modified in one thread.
        // noinspection NonAtomicOperationOnVolatileField
        failureCount += files.size();
        failedDocs.addAll(files);
    }

    @SuppressWarnings("NonAtomicVolatileUpdate")
    void onResolveFailed(Uri uri) {
        // Non-atomic operation is Ok since failureCount is only modified in one thread.
        // noinspection NonAtomicOperationOnVolatileField
        failureCount++;
        failedUris.add(uri);
    }

    @SuppressWarnings("NonAtomicVolatileUpdate")
    void onPathFailed(@NonNull String path) {
        // Non-atomic operation is Ok since failureCount is only modified in one thread.
        // noinspection NonAtomicOperationOnVolatileField
        failureCount++;
        failedPaths.add(path);
    }

    final boolean hasFailures() {
        return failureCount > 0;
    }

    boolean hasWarnings() {
        return false;
    }

    final void deleteDocument(DocumentInfo doc, @Nullable DocumentInfo parent)
            throws ResourceException {
        try {
            if (parent != null && doc.isRemoveSupported()) {
                DocumentsContract.removeDocument(wrap(getClient(doc)), doc.derivedUri,
                        parent.derivedUri);
            } else if (doc.isDeleteSupported()) {
                DocumentsContract.deleteDocument(wrap(getClient(doc)), doc.derivedUri);
            } else {
                throw new ResourceException("Unable to delete source document. "
                        + "File is not deletable or removable: %s.", doc.derivedUri);
            }
        } catch (FileNotFoundException | RemoteException | RuntimeException e) {
            if (e instanceof DeadObjectException) {
                releaseClient(doc);
            }
            throw new ResourceException("Failed to delete file %s due to an exception.",
                    doc.derivedUri, e);
        }
    }

    Notification getSetupNotification(String content) {
        mProgressBuilder.setProgress(0, 0, true)
                .setContentText(content);
        return mProgressBuilder.build();
    }

    Notification getFailureNotification(@NonNull String contentTitle, @DrawableRes int icon) {
        final Intent navigateIntent = buildNavigateIntent(INTENT_TAG_FAILURE);
        navigateIntent.putExtra(EXTRA_DIALOG_TYPE, OperationDialogFragment.DIALOG_TYPE_FAILURE);
        navigateIntent.putExtra(EXTRA_OPERATION_TYPE, operationType);

        // Limit the size of the lists getting passed with the failure notification.
        final int maxListSize = 100;
        navigateIntent.putParcelableArrayListExtra(EXTRA_FAILED_DOCS,
                failedDocs.size() <= maxListSize ? failedDocs
                        : new ArrayList<>(failedDocs.subList(0, maxListSize)));
        navigateIntent.putParcelableArrayListExtra(EXTRA_FAILED_URIS,
                failedUris.size() <= maxListSize ? failedUris
                        : new ArrayList<>(failedUris.subList(0, maxListSize)));
        navigateIntent.putStringArrayListExtra(EXTRA_FAILED_PATHS,
                failedPaths.size() <= maxListSize ? failedPaths
                        : new ArrayList<>(failedPaths.subList(0, maxListSize)));

        final Notification.Builder errorBuilder =
                createNotificationBuilder()
                        .setContentTitle(contentTitle)
                        .setContentText(
                                service.getString(getRes(R.string.notification_touch_for_details)))
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        appContext,
                                        0,
                                        navigateIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT
                                                | PendingIntent.FLAG_ONE_SHOT
                                                | PendingIntent.FLAG_MUTABLE))
                        .setCategory(Notification.CATEGORY_ERROR)
                        .setSmallIcon(icon)
                        .setAutoCancel(true);

        return errorBuilder.build();
    }

    abstract Builder createProgressBuilder();

    final Builder createProgressBuilder(
            String title, @DrawableRes int icon,
            String actionTitle, @DrawableRes int actionIcon) {
        Notification.Builder progressBuilder = createNotificationBuilder()
                .setContentTitle(title)
                .setContentIntent(
                        PendingIntent.getActivity(appContext, 0,
                                buildNavigateIntent(INTENT_TAG_PROGRESS),
                                PendingIntent.FLAG_IMMUTABLE))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setSmallIcon(icon)
                .setOngoing(true);

        final Intent cancelIntent = createCancelIntent();

        progressBuilder.addAction(
                actionIcon,
                actionTitle,
                PendingIntent.getService(
                        service,
                        0,
                        cancelIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT
                        | PendingIntent.FLAG_MUTABLE));

        return progressBuilder;
    }

    /**
     * Gets a formatted failure message from a string resource.
     *
     * @param stringId   The resource ID of the string to format.
     * @param formatArgs The map of arguments to use for formatting.
     * @return The formatted failure message.
     */
    String getFailureContentTitle(int stringId, @NonNull Map<String, Object> formatArgs) {
        formatArgs.put("count", failureCount);
        return new MessageFormat(service.getString(getRes(stringId)), Locale.getDefault()).format(
                formatArgs);
    }

    /**
     * Gets a formatted failure message from a string resource.
     *
     * @param stringId The resource ID of the string to format.
     * @return The formatted failure message
     */
    final String getFailureContentTitle(int stringId) {
        return getFailureContentTitle(stringId, new HashMap<>());
    }

    Notification.Builder createNotificationBuilder() {
        return mFeatures.isNotificationChannelEnabled()
                ? new Notification.Builder(service, FileOperationService.NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(service);
    }

    /**
     * Creates an intent for navigating back to the destination directory.
     */
    Intent buildNavigateIntent(String tag) {
        // TODO (b/35721285): Reuse an existing task rather than creating a new one every time.
        Intent intent = new Intent(service, FilesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(getDataUriForIntent(tag));
        intent.putExtra(Shared.EXTRA_STACK, (Parcelable) stack);
        return intent;
    }

    Intent createCancelIntent() {
        final Intent cancelIntent = new Intent(service, FileOperationService.class);
        cancelIntent.setData(getDataUriForIntent(INTENT_TAG_CANCEL));
        cancelIntent.putExtra(EXTRA_CANCEL, true);
        cancelIntent.putExtra(EXTRA_JOB_ID, id);
        return cancelIntent;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Job")
                .append("{")
                .append("id=" + id)
                .append("}")
                .toString();
    }

    /**
     * Listener interface employed by the service that owns us as well as tests.
     */
    public interface Listener {
        void onStart(Job job);
        void onFinished(Job job);
    }

    /**
     * Interface for tracking job progress.
     */
    interface ProgressTracker {
        default double getProgress() {  return -1; }
        default long getRemainingTimeEstimate() {
            return -1;
        }
    }
}
