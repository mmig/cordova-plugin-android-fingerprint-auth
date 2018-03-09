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

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Activates fingerprint-detection (without dialog) using fingerprint APIs to authenticate the user,
 * and falls back to password authentication if fingerprint is not available.
 */
public class FingerprintAuthenticationHeadless
        implements FingerprintHeadlessHelper.Callback {

    private static final String TAG = "FingerprintAuthHeadless";

    private Stage mStage = Stage.FINGERPRINT;

    private KeyguardManager mKeyguardManager;
    private FingerprintManager.CryptoObject mCryptoObject;
    private FingerprintHeadlessHelper mFingerprintHeadlessHelper;
    FingerprintHeadlessHelper.FingerprintHeadlessHelperBuilder mFingerprintHeadlessHelperBuilder;

    private Context mContext;
    private boolean mListening = false;

    public FingerprintAuthenticationHeadless(Context context) {
        this.mContext = context;
    }

    public void handleCreate(){

        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mFingerprintHeadlessHelperBuilder = new FingerprintHeadlessHelper.FingerprintHeadlessHelperBuilder(
                mContext, mContext.getSystemService(FingerprintManager.class));

    }

    public void handleCreateView() {

        Log.d(TAG, "disableBackup: " + FingerprintAuth.mDisableBackup);

        mFingerprintHeadlessHelper = mFingerprintHeadlessHelperBuilder.build(this);

        updateStage();

        // If fingerprint authentication is not available, switch immediately to the backup
        // (password) screen.
        if (!mFingerprintHeadlessHelper.isFingerprintAuthAvailable()) {
            goToBackup();
        }
    }


    public void handleResume() {
        if (mStage == Stage.FINGERPRINT) {
            mFingerprintHeadlessHelper.startListening(mCryptoObject);
            mListening = true;
        }
    }

    public void setStage(Stage stage) {
        mStage = stage;
    }

    public void handlePause() {
        mFingerprintHeadlessHelper.stopListening();
        mListening = false;
    }

    /**
     * Sets the crypto object to be passed in when authenticating with fingerprint.
     */
    public void setCryptoObject(FingerprintManager.CryptoObject cryptoObject) {
        mCryptoObject = cryptoObject;
    }

    /**
     * Switches to backup (password) screen. This either can happen when fingerprint is not
     * available or the user chooses to use the password authentication method by pressing the
     * button. This can also happen when the user had too many fingerprint attempts.
     */
    private void goToBackup() {
        mStage = Stage.BACKUP;
        updateStage();
    }

    private void updateStage() {
//        int cancel_id = getResources()
//                .getIdentifier("cancel", "string", FingerprintAuth.packageName);
        switch (mStage) {
        case FINGERPRINT:
//            mCancelButton.setText(cancel_id);
//            int use_backup_id = getResources()
//                    .getIdentifier("use_backup", "string", FingerprintAuth.packageName);
//            mSecondDialogButton.setText(use_backup_id);
//            mFingerprintContent.setVisibility(View.VISIBLE);
            break;
        case NEW_FINGERPRINT_ENROLLED:
            // Intentional fall through
        case BACKUP:
            if (mStage == Stage.NEW_FINGERPRINT_ENROLLED) {

            }
            if (!mKeyguardManager.isKeyguardSecure()) {
                // Show a message that the user hasn't set up a lock screen.
                int secure_lock_screen_required_id = mContext.getResources()
                        .getIdentifier("secure_lock_screen_required", "string",
                                FingerprintAuth.packageName);
                Toast.makeText(mContext,
                        mContext.getString(secure_lock_screen_required_id),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (FingerprintAuth.mDisableBackup) {
                FingerprintAuth.onError("backup disabled");
                return;
            }
            showAuthenticationScreen();
            break;
        }
    }

    private void showAuthenticationScreen() {
        this.handleResume();
    }

    @Override
    public void onAuthenticated(FingerprintManager.AuthenticationResult result) {
        // Callback from FingerprintHeadlessHelper. Let the activity know that authentication was
        // successful.
        FingerprintAuth.onAuthenticated(true /* withFingerprint */, result);
        handleCompleted();
    }

    @Override
    public void onError(CharSequence errString) {
        if (!FingerprintAuth.mDisableBackup) {
            if (mContext.getApplicationContext() != null && isActive()) {
                goToBackup();
            }
        } else {
            FingerprintAuth.onError(errString);
            handleCompleted();
        }
    }

    public void cancel() {
        FingerprintAuth.onCancelled();
        handleCompleted();
    }

    public boolean isActive(){
        return mListening;
    }

    private void handleCompleted(){
        mFingerprintHeadlessHelper.stopListening();
        FingerprintAuth.onHeadlessCompleted();
    }

    /**
     * Enumeration to indicate which authentication method the user is trying to authenticate with.
     */
    public enum Stage {
        FINGERPRINT,
        NEW_FINGERPRINT_ENROLLED,
        BACKUP
    }
}
