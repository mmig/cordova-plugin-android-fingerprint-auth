/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.cordova.plugin.android.fingerprintauth;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;


/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
@TargetApi(23)
public class FingerprintHeadlessHelper extends FingerprintManager.AuthenticationCallback {

    static final long ERROR_TIMEOUT_MILLIS = 1600;
    static final long SUCCESS_DELAY_MILLIS = 1300;

    private final Context mContext;
    private final FingerprintManager mFingerprintManager;
    private final Callback mCallback;
    private CancellationSignal mCancellationSignal;
    private int mAttempts = 0;
    private static FingerprintManager.AuthenticationResult fingerprintResult;

    boolean mSelfCancelled;

    /**
     * Builder class for {@link FingerprintHeadlessHelper} in which injected fields from Dagger
     * holds its fields and takes other arguments in the {@link #build} method.
     */
    public static class FingerprintHeadlessHelperBuilder {
        private final FingerprintManager mFingerPrintManager;
        private final Context mContext;

        public FingerprintHeadlessHelperBuilder(Context context, FingerprintManager fingerprintManager) {
            mFingerPrintManager = fingerprintManager;
            mContext = context;
        }

        public FingerprintHeadlessHelper build(Callback callback) {
            return new FingerprintHeadlessHelper(mContext, mFingerPrintManager, callback);
        }
    }

    /**
     * Constructor for {@link FingerprintHeadlessHelper}. This method is expected to be called from
     * only the {@link FingerprintHeadlessHelperBuilder} class.
     */
    protected FingerprintHeadlessHelper(Context context, FingerprintManager fingerprintManager,
            Callback callback) {
        mFingerprintManager = fingerprintManager;
        mCallback = callback;
        mContext = context;
    }

    public boolean isFingerprintAuthAvailable() {
        return mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints();
    }

    public void startListening(FingerprintManager.CryptoObject cryptoObject) {
        if (!isFingerprintAuthAvailable()) {
            return;
        }
        mCancellationSignal = new CancellationSignal();
        mSelfCancelled = false;
        mFingerprintManager
                .authenticate(cryptoObject, mCancellationSignal, 0 /* flags */, this, null);
    }

    public void stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true;
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, final CharSequence errString) {
        if (!mSelfCancelled) {
            showError(errString);
        }
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        showError(helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        mAttempts++;
        int fingerprint_not_recognized_id = mContext.getResources()
                .getIdentifier("fingerprint_not_recognized", "string",
                        FingerprintAuth.packageName);
        int fingerprint_too_many_attempts_id = mContext.getResources()
                .getIdentifier("fingerprint_too_many_attempts", "string",
                        FingerprintAuth.packageName);
        final String too_many_attempts_string = mContext.getResources().getString(
                fingerprint_too_many_attempts_id);
        if (mAttempts > FingerprintAuth.mMaxAttempts) {
            showError(too_many_attempts_string);
        } else {
            showError(mContext.getResources().getString(
                    fingerprint_not_recognized_id));
        }
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        fingerprintResult = result;
        mCallback.onAuthenticated(fingerprintResult);
    }

    protected void showError(CharSequence error) {
        this.mCallback.onError(error);
    }

    public interface Callback {

        void onAuthenticated(FingerprintManager.AuthenticationResult result);

        void onError(CharSequence errString);
    }
}
