// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.BundleCompat;
import androidx.core.os.ParcelCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.details.info.AppInfoFragment;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.self.SelfUriManager;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.UiUtils;

public class AppDetailsActivity extends BaseActivity {
    public static final String ALIAS_APP_INFO = "io.github.muntashirakon.AppManager.details.AppInfoActivity";

    /**
     * Fork: tab display order → {@link AppDetailsFragment.Property}. Snooping is
     * <b>first</b>, and therefore the page App details opens on (白い熊): it is
     * what this app is opened for. Its property id is still the last one (13) —
     * those ids key the view model's per-property state, so inserting one at
     * position 0 would shift every other property. Keep this array in lockstep
     * with {@code R.array.TAB_TITLES} — same length, same order.
     */
    @AppDetailsFragment.Property
    private static final int[] TAB_PROPERTIES = {
            AppDetailsFragment.SNOOPING,
            AppDetailsFragment.APP_INFO,
            AppDetailsFragment.ACTIVITIES,
            AppDetailsFragment.SERVICES,
            AppDetailsFragment.RECEIVERS,
            AppDetailsFragment.PROVIDERS,
            AppDetailsFragment.APP_OPS,
            AppDetailsFragment.USES_PERMISSIONS,
            AppDetailsFragment.PERMISSIONS,
            AppDetailsFragment.FEATURES,
            AppDetailsFragment.CONFIGURATIONS,
            AppDetailsFragment.SIGNATURES,
            AppDetailsFragment.SHARED_LIBRARIES,
            AppDetailsFragment.OVERLAYS,
    };

    private static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"; // Intent.EXTRA_PACKAGE_NAME
    private static final String EXTRA_APK_SOURCE = "src";
    private static final String EXTRA_USER_HANDLE = "user";
    private static final String EXTRA_BACK_TO_MAIN = "main";
    /** Fork: tab index to open on, so callers can land on 盗み見 directly. */
    private static final String EXTRA_TAB = "tab";
    /** Position of the Snooping tab in TAB_PROPERTIES — first, i.e. the default. */
    public static final int TAB_SNOOPING = 0;

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_USER_HANDLE, userId);
        return intent;
    }

    /** Fork: opens App details on a specific tab. */
    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName,
                                   @UserIdInt int userId, int tabIndex) {
        Intent intent = getIntent(context, packageName, userId);
        intent.putExtra(EXTRA_TAB, tabIndex);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId,
                                   boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_USER_HANDLE, userId);
        intent.putExtra(EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull ApkSource apkSource, boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        IntentCompat.putWrappedParcelableExtra(intent, EXTRA_APK_SOURCE, apkSource);
        intent.putExtra(EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull Path apkPath, boolean backToMainPage) {
        return getIntent(context, apkPath.getUri(), apkPath.getType(), backToMainPage);
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull Uri apkPath, @Nullable String mimeType, boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        if (mimeType != null) {
            intent.setDataAndType(apkPath, mimeType);
        } else {
            intent.setData(apkPath);
        }
        intent.putExtra(EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    public AppDetailsViewModel model;
    public AdvancedSearchView searchView;

    private ViewPager2 mViewPager;
    private TypedArray mTabTitleIds;
    private Fragment[] mTabFragments;

    private boolean mBackToMainPage;
    @Nullable
    private String mPackageName;
    @Nullable
    private ApkSource mApkSource;
    @Nullable
    private String mApkType;
    @UserIdInt
    private int mUserId;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_app_details);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle("…");
        model = new ViewModelProvider(this).get(AppDetailsViewModel.class);
        // Restore instance state
        SavedState ss = savedInstanceState != null ? BundleCompat.getParcelable(savedInstanceState, "ss", SavedState.class) : null;
        if (ss != null) {
            mBackToMainPage = ss.mBackToMainPage;
            mPackageName = ss.mPackageName;
            mApkSource = ss.mApkSource;
            mApkType = ss.mApkType;
            mUserId = ss.mUserId;
        } else {
            Intent intent = getIntent();
            mBackToMainPage = intent.getBooleanExtra(EXTRA_BACK_TO_MAIN, mBackToMainPage);
            UserPackagePair pair = SelfUriManager.getUserPackagePairFromUri(intent.getData());
            if (pair != null) {
                mPackageName = pair.getPackageName();
                mApkSource = null;
                mUserId = pair.getUserId();
            } else {
                mPackageName = getPackageNameFromExtras(intent);
                mApkSource = getApkSource(intent);
                mUserId = intent.getIntExtra(EXTRA_USER_HANDLE, UserHandleHidden.myUserId());
            }
            mApkType = intent.getType();
        }
        model.setUserId(mUserId);
        // Initialize tabs
        mTabTitleIds = getResources().obtainTypedArray(R.array.TAB_TITLES);
        mTabFragments = new Fragment[mTabTitleIds.length()];
        if (mPackageName == null && mApkSource == null) {
            UIUtils.displayLongToast(R.string.empty_package_name);
            finish();
            return;
        }
        // Set search
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            searchView = UIUtils.setupAdvancedSearchView(actionBar);
        }
        mViewPager = findViewById(R.id.pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        UiUtils.applyWindowInsetsAsPadding(tabLayout, false, true);
        final AlertDialog progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading), true);
        if (mPackageName == null) {
            // Display progress dialog only for external apk files
            progressDialog.show();
        }
        // Set tabs
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(new AppDetailsFragmentPagerAdapter(this));
        // Fork: land on the requested tab. Posted, because setCurrentItem before
        // the mediator has attached is silently dropped.
        int requestedTab = getIntent().getIntExtra(EXTRA_TAB, -1);
        if (requestedTab >= 0) {
            mViewPager.post(() -> mViewPager.setCurrentItem(requestedTab, false));
        }
        new TabLayoutMediator(tabLayout, mViewPager, (tab, position) -> tab.setText(mTabTitleIds.getString(position)))
                .attach();
        // Load package info
        (mPackageName != null
                ? model.setPackage(mPackageName)
                : model.setPackage(Objects.requireNonNull(mApkSource))
        ).observe(this, packageInfo -> {
            progressDialog.dismiss();
            if (packageInfo == null) {
                UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
                if (!isDestroyed()) {
                    finish();
                }
                return;
            }
            // Set title
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            // Set title as the package label
            setTitle(applicationInfo.loadLabel(getPackageManager()));
        });
        // Check for the existence of package
        model.getIsPackageExistLiveData().observe(this, isPackageExist -> {
            if (!isPackageExist) {
                if (!model.isExternalApk()) {
                    UIUtils.displayShortToast(R.string.app_not_installed);
                }
                finish();
            }
        });
        // Set subtitle as the username if more than one user exists
        model.getUserInfo().observe(this, userInfo -> getSupportActionBar()
                .setSubtitle(getString(R.string.user_profile_with_id, userInfo.name, userInfo.id)));
        // Check for package changes
        model.isPackageChanged().observe(this, isPackageChanged -> {
            if (isPackageChanged && model.isPackageExist()) {
                loadTabs();
            }
        });
    }

    @Nullable
    private String getPackageNameFromExtras(@NonNull Intent intent) {
        String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (pkg == null) {
            // Legacy argument, kept for compatibility
            pkg = intent.getStringExtra("pkg");
        }
        if (pkg != null) {
            // Package name needs to be sanitized since it's also a file
            return Paths.sanitizeFilename(pkg);
        }
        return null;
    }

    @Nullable
    private ApkSource getApkSource(@NonNull Intent intent) {
        Uri uri = intent.getData();
        if (uri != null) {
            return ApkSource.getApkSource(uri, intent.getType());
        }
        return IntentCompat.getUnwrappedParcelableExtra(intent, EXTRA_APK_SOURCE, ApkSource.class);
    }

    static class SavedState implements Parcelable {
        private boolean mBackToMainPage;
        @Nullable
        private String mPackageName;
        @Nullable
        private ApkSource mApkSource;
        @Nullable
        private String mApkType;
        private int mUserId;

        protected SavedState() {
        }

        public SavedState(Parcel source) {
            mBackToMainPage = ParcelCompat.readBoolean(source);
            mPackageName = source.readString();
            mApkSource = ParcelCompat.readParcelable(source, ApkSource.class.getClassLoader(), ApkSource.class);
            mApkType = source.readString();
            mUserId = source.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelCompat.writeBoolean(dest, mBackToMainPage);
            dest.writeString(mPackageName);
            dest.writeParcelable(mApkSource, flags);
            dest.writeString(mApkType);
            dest.writeInt(mUserId);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mApkSource != null || mPackageName != null) {
            SavedState ss = new SavedState();
            ss.mBackToMainPage = mBackToMainPage;
            ss.mPackageName = mPackageName;
            ss.mApkSource = mApkSource;
            ss.mApkType = mApkType;
            ss.mUserId = mUserId;
            outState.putParcelable("ss", ss);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            if (mBackToMainPage) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTabs() {
        Log.d("ADA - " + mTabTitleIds.getText(mViewPager.getCurrentItem()), "isPackageChanged called");
        for (int property : TAB_PROPERTIES) model.load(property);
    }

    // For tab layout
    private class AppDetailsFragmentPagerAdapter extends FragmentStateAdapter {
        AppDetailsFragmentPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int tabPosition) {
            if (mTabFragments[tabPosition] != null) {
                return mTabFragments[tabPosition];
            }
            @AppDetailsFragment.Property int position = TAB_PROPERTIES[tabPosition];
            switch (position) {
                case AppDetailsFragment.APP_INFO:
                    return mTabFragments[tabPosition] = new AppInfoFragment();
                case AppDetailsFragment.SNOOPING: {
                    AppDetailsSnoopingFragment fragment = new AppDetailsSnoopingFragment();
                    Bundle args = new Bundle();
                    args.putInt(AppDetailsFragment.ARG_TYPE, position);
                    fragment.setArguments(args);
                    return mTabFragments[tabPosition] = fragment;
                }
                case AppDetailsFragment.ACTIVITIES:
                case AppDetailsFragment.SERVICES:
                case AppDetailsFragment.RECEIVERS:
                case AppDetailsFragment.PROVIDERS: {
                    AppDetailsComponentsFragment fragment = new AppDetailsComponentsFragment();
                    Bundle args = new Bundle();
                    args.putInt(AppDetailsFragment.ARG_TYPE, position);
                    fragment.setArguments(args);
                    return mTabFragments[tabPosition] = fragment;
                }
                case AppDetailsFragment.APP_OPS:
                case AppDetailsFragment.PERMISSIONS:
                case AppDetailsFragment.USES_PERMISSIONS: {
                    AppDetailsPermissionsFragment fragment = new AppDetailsPermissionsFragment();
                    Bundle args = new Bundle();
                    args.putInt(AppDetailsFragment.ARG_TYPE, position);
                    fragment.setArguments(args);
                    return mTabFragments[tabPosition] = fragment;
                }
                case AppDetailsFragment.CONFIGURATIONS:
                case AppDetailsFragment.FEATURES:
                case AppDetailsFragment.SHARED_LIBRARIES:
                case AppDetailsFragment.SIGNATURES: {
                    AppDetailsOtherFragment fragment = new AppDetailsOtherFragment();
                    Bundle args = new Bundle();
                    args.putInt(AppDetailsFragment.ARG_TYPE, position);
                    fragment.setArguments(args);
                    return mTabFragments[tabPosition] = fragment;
                }
                case AppDetailsFragment.OVERLAYS:
                    AppDetailsOverlaysFragment fragment = new AppDetailsOverlaysFragment();
                    Bundle args = new Bundle();
                    args.putInt(AppDetailsFragment.ARG_TYPE, position);
                    fragment.setArguments(args);
                    return mTabFragments[tabPosition] = fragment;
            }
            return mTabFragments[tabPosition];
        }

        @Override
        public int getItemCount() {
            return mTabTitleIds.length();
        }
    }
}
