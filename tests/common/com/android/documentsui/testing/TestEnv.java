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
package com.android.documentsui.testing;

import android.os.Handler;
import android.os.Looper;

import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.TestModel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public class TestEnv {

    public static final String AUTHORITY = "hullabaloo";

    public final TestScheduledExecutorService mExecutor;
    public final State state = new State();
    public final TestRootsAccess roots = new TestRootsAccess();
    public final TestModel model = new TestModel(AUTHORITY);

    private TestEnv() {
        mExecutor = new TestScheduledExecutorService();
    }

    public static TestEnv create() {
        TestEnv env = new TestEnv();
        env.reset();
        return env;
    }

    public void reset() {
        model.update("a", "b", "c", "x", "y", "z");
        state.stack.push(model.getDocument("1"));
    }

    public void beforeAsserts() throws Exception {
        // We need to wait on all AsyncTasks to finish AND to post results back.
        // *** Results are posted on main thread ***, but tests run in their own
        // thread. So even with our test executor we still have races.
        //
        // To work around this issue post our own runnable to the main thread
        // which we presume will be the *last* runnable (after any from AsyncTasks)
        // and then wait for our runnable to be called.
        CountDownLatch latch = new CountDownLatch(1);
        mExecutor.runAll();
        new Handler(Looper.getMainLooper()).post(latch::countDown);
        latch.await();
    }

    public Executor lookupExecutor(String authority) {
        return mExecutor;
    }
}
