/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.documentsui;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;
import static com.android.documentsui.util.Material3Config.getRes;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Objects;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresApi(Build.VERSION_CODES.S)
public interface UserManagerState {

    /**
     * Returns the {@link UserId} of each profile which should be queried for documents. This will
     * always include {@link UserId#CURRENT_USER}.
     */
    List<UserId> getUserIds();

    /** Returns mapping between the {@link UserId} and the label for the profile */
    Map<UserId, String> getUserIdToLabelMap();

    /**
     * Returns mapping between the {@link UserId} and the drawable badge for the profile
     *
     * <p>returns {@code null} for non-profile userId
     */
    Map<UserId, Drawable> getUserIdToBadgeMap();

    /**
     * Returns a map of {@link UserId} to boolean value indicating whether the {@link
     * UserId}.CURRENT_USER can forward {@link Intent} to that {@link UserId}
     */
    Map<UserId, Boolean> getCanForwardToProfileIdMap(Intent intent);

    /**
     * Updates the state of the list of userIds and all the associated maps according the intent
     * received in broadcast
     *
     * @param userId {@link UserId} for the profile for which the availability status changed
     * @param action {@link Intent}.ACTION_PROFILE_UNAVAILABLE and {@link
     *     Intent}.ACTION_PROFILE_AVAILABLE, {@link Intent}.ACTION_PROFILE_ADDED} and {@link
     *     Intent}.ACTION_PROFILE_REMOVED}
     */
    void onProfileActionStatusChange(String action, UserId userId);

    /** Sets the intent that triggered the launch of the DocsUI */
    void setCurrentStateIntent(Intent intent);

    /** Returns true if there are hidden profiles */
    boolean areHiddenInQuietModeProfilesPresent();

    /** Creates an implementation of {@link UserManagerState}. */
    // TODO: b/314746383 Make this class a singleton
    static UserManagerState create(Context context) {
        return new RuntimeUserManagerState(context);
    }

    /** Implementation of {@link UserManagerState} */
    final class RuntimeUserManagerState implements UserManagerState {

        private static final String TAG = "UserManagerState";
        private final Context mContext;
        private final UserId mCurrentUser;
        private final boolean mIsDeviceSupported;
        private final UserManager mUserManager;
        private final ConfigStore mConfigStore;

        /**
         * List of all the {@link UserId} that have the {@link UserProperties.ShowInSharingSurfaces}
         * set as `SHOW_IN_SHARING_SURFACES_SEPARATE` OR it is a system/personal user
         */
        @GuardedBy("mUserIds")
        private final List<UserId> mUserIds = new ArrayList<>();

        /** Mapping between the {@link UserId} to the corresponding profile label */
        @GuardedBy("mUserIdToLabelMap")
        private final Map<UserId, String> mUserIdToLabelMap = new HashMap<>();

        /** Mapping between the {@link UserId} to the corresponding profile badge */
        @GuardedBy("mUserIdToBadgeMap")
        private final Map<UserId, Drawable> mUserIdToBadgeMap = new HashMap<>();

        /**
         * Map containing {@link UserId}, other than that of the current user, as key and boolean
         * denoting whether it is accessible by the current user or not as value
         */
        @GuardedBy("mCanForwardToProfileIdMap")
        private final Map<UserId, Boolean> mCanForwardToProfileIdMap = new HashMap<>();

        private Intent mCurrentStateIntent;

        private final BroadcastReceiver mIntentReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        synchronized (mUserIds) {
                            mUserIds.clear();
                        }
                        synchronized (mUserIdToLabelMap) {
                            mUserIdToLabelMap.clear();
                        }
                        synchronized (mUserIdToBadgeMap) {
                            mUserIdToBadgeMap.clear();
                        }
                        synchronized (mCanForwardToProfileIdMap) {
                            mCanForwardToProfileIdMap.clear();
                        }
                    }
                };

        private RuntimeUserManagerState(Context context) {
            this(
                    context,
                    UserId.CURRENT_USER,
                    Features.CROSS_PROFILE_TABS && isDeviceSupported(context),
                    DocumentsApplication.getConfigStore());
        }

        @VisibleForTesting
        RuntimeUserManagerState(
                Context context,
                UserId currentUser,
                boolean isDeviceSupported,
                ConfigStore configStore) {
            mContext = context.getApplicationContext();
            mCurrentUser = checkNotNull(currentUser);
            mIsDeviceSupported = isDeviceSupported;
            mUserManager = mContext.getSystemService(UserManager.class);
            mConfigStore = configStore;

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
            if (SdkLevel.isAtLeastV() && mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
                filter.addAction(Intent.ACTION_PROFILE_ADDED);
                filter.addAction(Intent.ACTION_PROFILE_REMOVED);
            }
            mContext.registerReceiver(mIntentReceiver, filter);
        }

        @Override
        public List<UserId> getUserIds() {
            synchronized (mUserIds) {
                if (mUserIds.isEmpty()) {
                    mUserIds.addAll(getUserIdsInternal());
                }
                return mUserIds;
            }
        }

        @Override
        public Map<UserId, String> getUserIdToLabelMap() {
            synchronized (mUserIdToLabelMap) {
                if (mUserIdToLabelMap.isEmpty()) {
                    getUserIdToLabelMapInternal();
                }
                return mUserIdToLabelMap;
            }
        }

        @Override
        public Map<UserId, Drawable> getUserIdToBadgeMap() {
            synchronized (mUserIdToBadgeMap) {
                if (mUserIdToBadgeMap.isEmpty()) {
                    getUserIdToBadgeMapInternal();
                }
                return mUserIdToBadgeMap;
            }
        }

        @Override
        public Map<UserId, Boolean> getCanForwardToProfileIdMap(Intent intent) {
            synchronized (mCanForwardToProfileIdMap) {
                if (mCanForwardToProfileIdMap.isEmpty()) {
                    getCanForwardToProfileIdMapInternal(intent);
                }
                return mCanForwardToProfileIdMap;
            }
        }

        @Override
        @SuppressLint("NewApi")
        public void onProfileActionStatusChange(String action, UserId userId) {
            if (!SdkLevel.isAtLeastV()) return;
            UserProperties userProperties =
                    mUserManager.getUserProperties(UserHandle.of(userId.getIdentifier()));
            if (userProperties.getShowInQuietMode() != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN) {
                return;
            }
            if (Intent.ACTION_PROFILE_UNAVAILABLE.equals(action)
                    || Intent.ACTION_PROFILE_REMOVED.equals(action)) {
                synchronized (mUserIds) {
                    mUserIds.remove(userId);
                }
            } else if (Intent.ACTION_PROFILE_AVAILABLE.equals(action)
                    || Intent.ACTION_PROFILE_ADDED.equals(action)) {
                synchronized (mUserIds) {
                    if (!mUserIds.contains(userId)) {
                        mUserIds.add(userId);
                    }
                }
                synchronized (mUserIdToLabelMap) {
                    if (!mUserIdToLabelMap.containsKey(userId)) {
                        mUserIdToLabelMap.put(userId, getProfileLabel(userId));
                    }
                }
                synchronized (mUserIdToBadgeMap) {
                    if (!mUserIdToBadgeMap.containsKey(userId)) {
                        mUserIdToBadgeMap.put(userId, getProfileBadge(userId));
                    }
                }
                synchronized (mCanForwardToProfileIdMap) {
                    if (!mCanForwardToProfileIdMap.containsKey(userId)) {
                        mCanForwardToProfileIdMap.put(
                                userId,
                                isCrossProfileAllowedToUser(
                                        mContext,
                                        mCurrentStateIntent,
                                        UserId.CURRENT_USER,
                                        userId));
                    }
                }
            } else {
                Log.e(TAG, "Unexpected action received: " + action);
            }
        }

        @Override
        public void setCurrentStateIntent(Intent intent) {
            mCurrentStateIntent = intent;
        }

        @Override
        public boolean areHiddenInQuietModeProfilesPresent() {
            if (!SdkLevel.isAtLeastV()) {
                return false;
            }

            for (UserId userId : getUserIds()) {
                if (mUserManager
                                .getUserProperties(UserHandle.of(userId.getIdentifier()))
                                .getShowInQuietMode()
                        == UserProperties.SHOW_IN_QUIET_MODE_HIDDEN) {
                    return true;
                }
            }
            return false;
        }

        private List<UserId> getUserIdsInternal() {
            final List<UserId> result = new ArrayList<>();

            if (!mIsDeviceSupported) {
                result.add(mCurrentUser);
                return result;
            }

            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return result;
            }

            final List<UserHandle> userProfiles = mUserManager.getUserProfiles();

            boolean currentUserIsManaged =
                    mUserManager.isManagedProfile(mCurrentUser.getIdentifier());

            for (UserHandle handle : userProfiles) {
                if (SdkLevel.isAtLeastV()) {
                    if (!isProfileAllowed(handle)) {
                        continue;
                    }
                } else {
                    // On Android U and below, ensure the following profiles are included:
                    //  - The currently active profile
                    //  - The currently active profile's parent
                    //  - All managed profiles
                    if (mCurrentUser.getIdentifier() == handle.getIdentifier()) {
                        // Intentionally empty so that this profile gets added.
                    } else if (currentUserIsManaged
                            && mUserManager.getProfileParent(mCurrentUser.getUserHandle())
                                    == handle) {
                        // Intentionally empty so that this profile gets added.
                    } else if (!mUserManager.isManagedProfile(handle.getIdentifier())) {
                        continue;
                    }
                }

                // Ensure the system user doesn't get added twice.
                if (result.contains(UserId.of(handle))) continue;
                result.add(UserId.of(handle));
            }

            return result;
        }

        /**
         * Checks if a package is installed for a given user.
         *
         * @param userHandle The ID of the user.
         * @return {@code true} if the package is installed for the user, {@code false} otherwise.
         */
        @RequiresPermission(
                anyOf = {
                    "android.permission.MANAGE_USERS",
                    "android.permission.INTERACT_ACROSS_USERS"
                })
        private boolean isPackageInstalledForUser(UserHandle userHandle) {
            String packageName = mContext.getPackageName();
            try {
                Context userPackageContext =
                        mContext.createPackageContextAsUser(
                                mContext.getPackageName(), 0 /* flags */, userHandle);
                return userPackageContext != null;
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package " + packageName + " not found for user " + userHandle);
                return false;
            }
        }

        /**
         * Checks if quiet mode is enabled for a given user.
         *
         * @param userHandle The UserHandle of the profile to check.
         * @return {@code true} if quiet mode is enabled, {@code false} otherwise.
         */
        private boolean isQuietModeEnabledForUser(UserHandle userHandle) {
            return UserId.of(userHandle.getIdentifier()).isQuietModeEnabled(mContext);
        }

        /**
         * Checks if a profile should be allowed, taking into account quiet mode and package
         * installation.
         *
         * @param userHandle The UserHandle of the profile to check.
         * @return {@code true} if the profile should be allowed, {@code false} otherwise.
         */
        @SuppressLint("NewApi")
        @RequiresPermission(
                anyOf = {
                    "android.permission.MANAGE_USERS",
                    "android.permission.INTERACT_ACROSS_USERS"
                })
        private boolean isProfileAllowed(UserHandle userHandle) {
            final UserProperties userProperties = mUserManager.getUserProperties(userHandle);

            // 1. Check if the package is installed for the user
            if (!isPackageInstalledForUser(userHandle)) {
                Log.w(
                        TAG,
                        "Package "
                                + mContext.getPackageName()
                                + " is not installed for user "
                                + userHandle);
                return false;
            }

            // 2. Check user properties and quiet mode
            if (userProperties.getShowInSharingSurfaces()
                    == UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE) {
                // Return true if profile is not in quiet mode or if it is in quiet mode
                // then its user properties do not require it to be hidden
                return !isQuietModeEnabledForUser(userHandle)
                        || userProperties.getShowInQuietMode()
                                != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN;
            }

            return false;
        }

        private void getUserIdToLabelMapInternal() {
            if (SdkLevel.isAtLeastV()) {
                getUserIdToLabelMapInternalPostV();
            } else {
                getUserIdToLabelMapInternalPreV();
            }
        }

        @SuppressLint("NewApi")
        private void getUserIdToLabelMapInternalPostV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mUserIdToLabelMap) {
                    mUserIdToLabelMap.put(userId, getProfileLabel(userId));
                }
            }
        }

        private void getUserIdToLabelMapInternalPreV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                if (mUserManager.isManagedProfile(userId.getIdentifier())) {
                    synchronized (mUserIdToLabelMap) {
                        mUserIdToLabelMap.put(
                                userId, getEnterpriseString(WORK_TAB, getRes(R.string.work_tab)));
                    }
                } else {
                    synchronized (mUserIdToLabelMap) {
                        mUserIdToLabelMap.put(
                                userId,
                                getEnterpriseString(PERSONAL_TAB, getRes(R.string.personal_tab)));
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private String getProfileLabel(UserId userId) {
            if (userId.getIdentifier() == ActivityManager.getCurrentUser()) {
                return getEnterpriseString(PERSONAL_TAB, getRes(R.string.personal_tab));
            }
            try {
                Context userContext =
                        mContext.createContextAsUser(
                                UserHandle.of(userId.getIdentifier()), 0 /* flags */);
                UserManager userManagerAsUser = userContext.getSystemService(UserManager.class);
                if (userManagerAsUser == null) {
                    Log.e(TAG, "cannot obtain user manager");
                    return null;
                }
                return userManagerAsUser.getProfileLabel();
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while trying to get profile label:\n" + e);
                return null;
            }
        }

        private String getEnterpriseString(String updatableStringId, int defaultStringId) {
            if (SdkLevel.isAtLeastT()) {
                return getUpdatableEnterpriseString(updatableStringId, defaultStringId);
            } else {
                return mContext.getString(defaultStringId);
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private String getUpdatableEnterpriseString(String updatableStringId, int defaultStringId) {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            if (Objects.equal(dpm, null)) {
                Log.e(TAG, "can not get device policy manager");
                return mContext.getString(defaultStringId);
            }
            return dpm.getResources()
                    .getString(updatableStringId, () -> mContext.getString(defaultStringId));
        }

        private void getUserIdToBadgeMapInternal() {
            if (SdkLevel.isAtLeastV()) {
                getUserIdToBadgeMapInternalPostV();
            } else {
                getUserIdToBadgeMapInternalPreV();
            }
        }

        @SuppressLint("NewApi")
        private void getUserIdToBadgeMapInternalPostV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mUserIdToBadgeMap) {
                    mUserIdToBadgeMap.put(userId, getProfileBadge(userId));
                }
            }
        }

        private void getUserIdToBadgeMapInternalPreV() {
            if (!SdkLevel.isAtLeastR()) return;
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                if (mUserManager.isManagedProfile(userId.getIdentifier())) {
                    synchronized (mUserIdToBadgeMap) {
                        mUserIdToBadgeMap.put(
                                userId,
                                SdkLevel.isAtLeastT()
                                        ? getWorkProfileBadge()
                                        : mContext.getDrawable(getRes(R.drawable.ic_briefcase)));
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private Drawable getProfileBadge(UserId userId) {
            if (userId.getIdentifier() == ActivityManager.getCurrentUser()) {
                return null;
            }
            try {
                Context userContext =
                        mContext.createContextAsUser(
                                UserHandle.of(userId.getIdentifier()), 0 /* flags */);
                UserManager userManagerAsUser = userContext.getSystemService(UserManager.class);
                if (userManagerAsUser == null) {
                    Log.e(TAG, "cannot obtain user manager");
                    return null;
                }
                return userManagerAsUser.getUserBadge();
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while trying to get profile badge:\n" + e);
                return null;
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private Drawable getWorkProfileBadge() {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            Drawable drawable =
                    dpm.getResources()
                            .getDrawable(
                                    WORK_PROFILE_ICON,
                                    SOLID_COLORED,
                                    () -> mContext.getDrawable(getRes(R.drawable.ic_briefcase)));
            return drawable;
        }

        /**
         * Updates Cross Profile access for all UserProfiles in {@code getUserIds()}
         *
         * <p>This method looks at a variety of situations for each Profile and decides if the
         * profile's content is accessible by the current process owner user id.
         *
         * <ol>
         *   <li>UserProperties attributes for CrossProfileDelegation are checked first. When the
         *       profile delegates to the parent profile, the parent's access is used.
         *   <li>{@link CrossProfileIntentForwardingActivity}s are resolved via the process owner's
         *       PackageManager, and are considered when evaluating cross profile to the target
         *       profile.
         * </ol>
         *
         * <p>In the event none of the above checks succeeds, the profile is considered to be
         * inaccessible to the current process user.
         *
         * @param intent The intent Photopicker is currently running under, for
         *     CrossProfileForwardActivity checking.
         */
        private void getCanForwardToProfileIdMapInternal(Intent intent) {

            synchronized (mCanForwardToProfileIdMap) {
                mCanForwardToProfileIdMap.clear();
                for (UserId userId : getUserIds()) {
                    mCanForwardToProfileIdMap.put(
                            userId,
                            isCrossProfileAllowedToUser(
                                    mContext, intent, mCurrentUser, userId));
                }
            }
        }

        /**
         * Determines if the provided UserIds support CrossProfile content sharing.
         *
         * <p>This method accepts a pair of user handles (from/to) and determines if CrossProfile
         * access is permitted between those two profiles.
         *
         * <p>There are differences is on how the access is determined based on the platform SDK:
         *
         * <p>For Platform SDK < V:
         *
         * <p>A check for CrossProfileIntentForwarders in the origin (from) profile that target the
         * destination (to) profile. If such a forwarder exists, then access is allowed, and denied
         * otherwise.
         *
         * <p>For Platform SDK >= V:
         *
         * <p>The method now takes into account access delegation, which was first added in Android
         * V.
         *
         * <p>For profiles that set the [CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT]
         * property in its [UserProperties], its parent profile will be substituted in for its side
         * of the check.
         *
         * <p>ex. For access checks between a Managed (from) and Private (to) profile, where: -
         * Managed does not delegate to its parent - Private delegates to its parent
         *
         * <p>The following logic is performed: Managed -> parent(Private)
         *
         * <p>The same check in the other direction would yield: parent(Private) -> Managed
         *
         * <p>Note how the private profile is never actually used for either side of the check,
         * since it is delegating its access check to the parent. And thus, if Managed can access
         * the parent, it can also access the private.
         *
         * @param context Current context object, for switching user contexts.
         * @param intent The current intent the Photopicker is running under.
         * @param fromUser The Origin profile, where the user is coming from
         * @param toUser The destination profile, where the user is attempting to go to.
         * @return Whether CrossProfile content sharing is supported in this handle.
         */
        private boolean isCrossProfileAllowedToUser(
                Context context, Intent intent, UserId fromUser, UserId toUser) {

            // Early exit conditions, accessing self.
            // NOTE: It is also possible to reach this state if this method is recursively checking
            // from: parent(A) to:parent(B) where A and B are both children of the same parent.
            if (fromUser.getIdentifier() == toUser.getIdentifier()) {
                return true;
            }

            // Decide if we should use actual from or parent(from)
            UserHandle currentFromUser =
                    getProfileToCheckCrossProfileAccess(fromUser.getUserHandle());

            // Decide if we should use actual to or parent(to)
            UserHandle currentToUser = getProfileToCheckCrossProfileAccess(toUser.getUserHandle());

            // When the from/to has changed from the original parameters, recursively restart the
            // checks with the new from/to handles.
            if (fromUser.getIdentifier() != currentFromUser.getIdentifier()
                    || toUser.getIdentifier() != currentToUser.getIdentifier()) {
                return isCrossProfileAllowedToUser(
                        context, intent, UserId.of(currentFromUser), UserId.of(currentToUser));
            }

            PackageManager pm = context.getPackageManager();
            return doesCrossProfileIntentForwarderExist(intent, pm, fromUser, toUser);
        }

        /**
         * Determines if the target UserHandle delegates its content sharing to its parent.
         *
         * @param userHandle The target handle to check delegation for.
         * @return TRUE if V+ and the handle delegates to parent. False otherwise.
         */
        private boolean isCrossProfileStrategyDelegatedToParent(UserHandle userHandle) {
            if (SdkLevel.isAtLeastV()) {
                if (mUserManager == null) {
                    Log.e(TAG, "Cannot obtain user manager");
                    return false;
                }
                UserProperties userProperties = mUserManager.getUserProperties(userHandle);
                if (userProperties.getCrossProfileContentSharingStrategy()
                        == userProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Acquires the correct {@link UserHandle} which should be used for CrossProfile access
         * checks.
         *
         * @param userHandle the origin handle.
         * @return The UserHandle that should be used for cross profile access checks. In the event
         *     the origin handle delegates its access, this may not be the same handle as the origin
         *     handle.
         */
        private UserHandle getProfileToCheckCrossProfileAccess(UserHandle userHandle) {
            if (mUserManager == null) {
                Log.e(TAG, "Cannot obtain user manager");
                return null;
            }
            return isCrossProfileStrategyDelegatedToParent(userHandle)
                    ? mUserManager.getProfileParent(userHandle)
                    : userHandle;
        }

        /**
         * Looks for a matching CrossProfileIntentForwardingActivity in the targetUserId for the
         * given intent.
         *
         * @param intent The intent the forwarding activity needs to match.
         * @param targetUserId The target user to check for.
         * @return whether a CrossProfileIntentForwardingActivity could be found for the given
         *     intent, and user.
         */
        private boolean doesCrossProfileIntentForwarderExist(
                Intent intent, PackageManager pm, UserId fromUser, UserId targetUserId) {

            final Intent intentToCheck = (Intent) intent.clone();
            intentToCheck.setComponent(null);
            intentToCheck.setPackage(null);

            for (ResolveInfo resolveInfo :
                    pm.queryIntentActivitiesAsUser(
                            intentToCheck,
                            PackageManager.MATCH_DEFAULT_ONLY,
                            fromUser.getUserHandle())) {

                if (resolveInfo.isCrossProfileIntentForwarderActivity()) {
                    /*
                     * IMPORTANT: This is a reflection based hack to ensure the profile is
                     * actually the installer of the CrossProfileIntentForwardingActivity.
                     *
                     * ResolveInfo.targetUserId exists, but is a hidden API not available to
                     * mainline modules, and no such API exists, so it is accessed via
                     * reflection below. All exceptions are caught to protect against
                     * reflection related issues such as:
                     * NoSuchFieldException / IllegalAccessException / SecurityException.
                     *
                     * In the event of an exception, the code fails "closed" for the current
                     * profile to avoid showing content that should not be visible.
                     */
                    try {
                        Field targetUserIdField =
                                resolveInfo.getClass().getDeclaredField("targetUserId");
                        targetUserIdField.setAccessible(true);
                        int activityTargetUserId = (int) targetUserIdField.get(resolveInfo);

                        if (activityTargetUserId == targetUserId.getIdentifier()) {

                            // Found a match for this profile
                            return true;
                        }

                    } catch (NoSuchFieldException | IllegalAccessException | SecurityException ex) {
                        // Couldn't check the targetUserId via reflection, so fail without
                        // further iterations.
                        Log.e(TAG, "Could not access targetUserId via reflection.", ex);
                        return false;
                    } catch (Exception ex) {
                        Log.e(TAG, "Exception occurred during cross profile checks", ex);
                    }
                }
            }

            // No match found, so return false.
            return false;
        }

        @SuppressLint("NewApi")
        private boolean isCrossProfileContentSharingStrategyDelegatedFromParent(
                UserHandle userHandle) {
            if (mUserManager == null) {
                Log.e(TAG, "can not obtain user manager");
                return false;
            }
            UserProperties userProperties = mUserManager.getUserProperties(userHandle);
            if (java.util.Objects.equals(userProperties, null)) {
                Log.e(TAG, "can not obtain user properties");
                return false;
            }

            return userProperties.getCrossProfileContentSharingStrategy()
                    == UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT;
        }

        private static boolean isDeviceSupported(Context context) {
            // The feature requires Android R DocumentsContract APIs and
            // INTERACT_ACROSS_USERS_FULL permission.
            return VersionUtils.isAtLeastR()
                    && context.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS)
                            == PackageManager.PERMISSION_GRANTED;
        }
    }
}
