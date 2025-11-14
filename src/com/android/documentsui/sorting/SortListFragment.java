package com.android.documentsui.sorting;

import static com.android.documentsui.base.SharedMinimal.TAG;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.ListView;

import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortDimension.SortDirection;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class SortListFragment extends DialogFragment {

    private static final String TAG_MODEL = "sorting_model";
    private static final String TAG_SORTING_LIST = "sorting_list";

    private SortModel mModel;
    private List<SortItem> mSortingList;

    public static void show(FragmentManager fm, SortModel model) {
        if (fm.isStateSaved()) {
            Log.w(TAG, "Skip show sort dialog because state saved");
            return;
        }

        if (fm.findFragmentByTag(TAG_SORTING_LIST) == null) {
            SortListFragment fragment = new SortListFragment();
            Bundle args = new Bundle();
            args.putParcelable(TAG_MODEL, model);
            fragment.setArguments(args);
            fragment.show(fm, TAG_SORTING_LIST);
        }
    }

    public SortListFragment() {
        super();
    }

    private void onItemClicked (AdapterView<?> parent, View view, int position, long id) {
        SortItem item = mSortingList.get(position);
        mModel.sortByUser(item.id, item.direction);
        getDialog().dismiss();
    }

    private void setupSortingList() {
        mSortingList = new ArrayList<>();
        for (int i = 0; i < mModel.getSize(); ++i) {
            SortDimension dimension = mModel.getDimensionAt(i);
            if (dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
                final int id = dimension.getId();
                if (id == SortModel.SORT_DIMENSION_ID_TITLE
                        || id == SortModel.SORT_DIMENSION_ID_FILE_TYPE) {
                    addBothDirectionDimension(dimension, true);
                } else if (id == SortModel.SORT_DIMENSION_ID_DATE
                        || id == SortModel.SORT_DIMENSION_ID_SIZE) {
                    addBothDirectionDimension(dimension, false);
                } else {
                    mSortingList.add(new SortItem(dimension));
                }
            }
        }
    }

    private void addBothDirectionDimension(SortDimension source, boolean ascendingFirst) {
        SortItem ascending = new SortItem(source.getId(),
                SortDimension.SORT_DIRECTION_ASCENDING,
                getSheetLabelId(source, SortDimension.SORT_DIRECTION_ASCENDING));
        SortItem descending = new SortItem(source.getId(),
                SortDimension.SORT_DIRECTION_DESCENDING,
                getSheetLabelId(source, SortDimension.SORT_DIRECTION_DESCENDING));
        mSortingList.add(ascendingFirst ? ascending : descending);
        mSortingList.add(ascendingFirst ? descending : ascending);
    }

    public static @StringRes int getSheetLabelId(SortDimension dimension, @SortDirection int direction) {
        boolean isAscending = direction == SortDimension.SORT_DIRECTION_ASCENDING;
        final int id = dimension.getId();
        if (id == SortModel.SORT_DIMENSION_ID_TITLE) {
            return isAscending
                    ? getRes(R.string.sort_dimension_name_ascending)
                    : getRes(R.string.sort_dimension_name_descending);
        } else if (id == SortModel.SORT_DIMENSION_ID_DATE) {
            return isAscending
                    ? getRes(R.string.sort_dimension_date_ascending)
                    : getRes(R.string.sort_dimension_date_descending);
        } else if (id == SortModel.SORT_DIMENSION_ID_FILE_TYPE) {
            return isAscending
                    ? getRes(R.string.sort_dimension_file_type_ascending)
                    : getRes(R.string.sort_dimension_file_type_descending);
        } else if (id == SortModel.SORT_DIMENSION_ID_SIZE) {
            return isAscending
                    ? getRes(R.string.sort_dimension_size_ascending)
                    : getRes(R.string.sort_dimension_size_descending);
        }
        return dimension.getLabelId();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            Bundle args = getArguments();
            mModel = args.getParcelable(TAG_MODEL);
        } else {
            mModel = savedInstanceState.getParcelable(TAG_MODEL);
        }
        setupSortingList();

        BottomSheetDialog dialog =
                new BottomSheetDialog(getContext());
        dialog.setContentView(getRes(R.layout.dialog_sorting));

        // Workaround for solve issue about dialog not full expanded when landscape.
        FrameLayout bottomSheet = (FrameLayout)
                dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        BottomSheetBehavior.from(bottomSheet)
                .setState(BottomSheetBehavior.STATE_EXPANDED);

        ListView listView = dialog.findViewById(getRes(R.id.sorting_dialog_list));

        listView.setAdapter(new SortingListAdapter(getContext(), mSortingList));
        // When use_material flag is ON, we want to show hover effect on the list item, which
        // requires a "clickable:true" on the item level, this attribute will ignore the list level
        // click listener, we need to bind this click listener to the item level.
        if (!isUseMaterial3FlagEnabled()) {
            listView.setOnItemClickListener(this::onItemClicked);
        } else {
            // "clickable:true" also break the default onKey handler for behaviors like pressing
            // Enter/Space to select, so we need to explicitly handle it here.
            listView.setOnKeyListener(
                    (v, keyCode, event) -> {
                        if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE)
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            onItemClicked(null, v, listView.getSelectedItemPosition(), 0);
                            return true;
                        }
                        return false;
                    });
        }

        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(TAG_MODEL, mModel);
    }

    private class SortingListAdapter extends ArrayAdapter<SortItem> {

        public SortingListAdapter(Context context, List<SortItem> list) {
            super(context, getRes(R.layout.sort_list_item), list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            final SortItem item = getItem(position);
            final CheckedTextView text = view.findViewById(android.R.id.text1);
            text.setText(getString(item.labelId));

            boolean selected = item.id == mModel.getSortedDimensionId()
                    && item.direction == mModel.getCurrentSortDirection();
            text.setChecked(selected);
            // If use_material3 flag is ON, instead of using "android:checkMark" attribute in the
            // layout file, we programmatically set the checkmark icon here. This is because the
            // "android:checkMark" drawable acts as an icon button which has its own hover/ripple
            // effect, and we don't want that.
            if (isUseMaterial3FlagEnabled()) {
                // Note "Relative" version of "setCompoundDrawable" handles RTL automatically.
                text.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        0, 0, selected ? getRes(R.drawable.ic_done) : 0, 0);
                // "clickable=true" on the item level makes the list level click listener stop
                // working, we need to bind click listener on the item level.
                text.setOnClickListener(v -> onItemClicked(null, v, position, 0));
            }
            return view;
        }
    }

    private static class SortItem {

        final int id;
        @SortDirection final int direction;
        @StringRes final int labelId;

        SortItem(SortDimension dimension) {
            id = dimension.getId();
            direction = dimension.getDefaultSortDirection();
            labelId = dimension.getLabelId();
        }

        SortItem(int id, @SortDirection int direction, @StringRes int labelId) {
            this.id = id;
            this.direction = direction;
            this.labelId = labelId;
        }
    }
}
