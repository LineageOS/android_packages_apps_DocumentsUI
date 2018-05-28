package com.android.documentsui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;

import com.android.documentsui.services.CopyJob.CopyJobProgressTracker;

import java.util.function.Function;
import java.util.function.LongSupplier;

class TestCopyJobProcessTracker<T extends CopyJobProgressTracker> {
    private T mProcessTracker;
    private Notification.Builder mProgressBuilder;
    private final Function<Double, String> mProgressFormatter;
    private final Function<Long, String> mRemainTimeFormatter;

    private static class TestLongSupplier implements LongSupplier {
        long mValue = 0;
        boolean mCalled;

        @Override
        public long getAsLong() {
            mCalled = true;
            return mValue;
        }
    }
    private TestLongSupplier mTimeSupplier = new TestLongSupplier();

    TestCopyJobProcessTracker(Class<T> trackerClass,
            long requiredData, CopyJob job, Function<Double, String> progressFormatter,
            Function<Long, String> remainTimeFormatter) throws Exception {

        mProcessTracker = trackerClass.getDeclaredConstructor(long.class,
                LongSupplier.class).newInstance(requiredData, mTimeSupplier);

        mProgressBuilder = job.mProgressBuilder;
        mProgressFormatter = progressFormatter;
        mRemainTimeFormatter = remainTimeFormatter;
    }

    T getProcessTracker() {
        return mProcessTracker;
    }

    void assertProgressTrackStarted() {
        assertTrue(mTimeSupplier.mCalled);
    }

    void assertStartedProgressEquals(int expectedProgress) {
        assertEquals(expectedProgress, (int) mProcessTracker.getProgress());
    }

    void assertStartedRemainingTimeEquals(long expectedRemainingTime) {
        assertEquals(expectedRemainingTime, mProcessTracker.getRemainingTimeEstimate());
    }

    void updateProgressAndRemainingTime(long elapsedTime) {
        mTimeSupplier.mValue = elapsedTime;
        mProcessTracker.update(mProgressBuilder, mRemainTimeFormatter);
    }

    void assertProgressEquals(double progress) {
        assertEquals(mProgressFormatter.apply(progress),
                mProgressBuilder.build().extras.get(Notification.EXTRA_SUB_TEXT));
    }

    void assertReminingTimeEquals(long remainingTime) {
        assertEquals(mRemainTimeFormatter.apply(remainingTime),
                mProgressBuilder.build().extras.get(Notification.EXTRA_TEXT));
    }

    void assertNoRemainingTime() {
        assertNull(mProgressBuilder.build().extras.get(Notification.EXTRA_TEXT));
    }
}
