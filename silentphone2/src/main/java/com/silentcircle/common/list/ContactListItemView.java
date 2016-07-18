/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.silentcircle.common.list;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.SelectionBoundsAdjuster;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.silentcircle.common.ContactStatusUtil;
import com.silentcircle.common.format.TextHighlighter;
import com.silentcircle.common.util.ContactDisplayUtils;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPresenceIconUtil;
import com.silentcircle.contacts.list.ScDefaultContactListAdapter;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A custom view for an item in the contact list.
 * The view contains the contact's photo, a set of text views (for name, status, etc...) and
 * icons for presence and call.
 * The view uses no XML file for layout and all the measurements and layouts are done
 * in the onMeasure and onLayout methods.
 *
 * The layout puts the contact's photo on the right side of the view, the call icon (if present)
 * to the left of the photo, the text lines are aligned to the left and the presence icon (if
 * present) is set to the left of the status line.
 *
 * The layout also supports a header (used as a header of a group of contacts) that is above the
 * contact's data and a divider between contact view.
 */

public class ContactListItemView extends ViewGroup
        implements SelectionBoundsAdjuster {

    // Style values for layout and appearance
    // The initialized values are defaults if none is provided through xml.
    private int mPreferredHeight = 0;
    private int mGapBetweenImageAndText = 0;
    private int mGapBetweenLabelAndData = 0;
    private int mPresenceIconMargin = 4;
    private int mPresenceIconSize = 16;
    private int mTextIndent = 0;
    private int mTextOffsetTop;
    private int mNameTextViewTextSize;
    private int mHeaderWidth;
    private Drawable mActivatedBackgroundDrawable;

    // Set in onLayout. Represent left and right position of the View on the screen.
    private int mLeftOffset;
    private int mRightOffset;

    /**
     * Used with {@link #mLabelView}, specifying the width ratio between label and data.
     */
    private int mLabelViewWidthWeight = 3;
    /**
     * Used with {@link #mDataView}, specifying the width ratio between label and data.
     */
    private int mDataViewWidthWeight = 5;

    protected static class HighlightSequence {
        private final int start;
        private final int end;

        HighlightSequence(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private ArrayList<HighlightSequence> mNameHighlightSequence;
    private ArrayList<HighlightSequence> mNumberHighlightSequence;

    // Highlighting prefix for names.
    private String mHighlightedPrefix;

    /**
     * Where to put contact photo. This affects the other Views' layout or look-and-feel.
     *
     * TODO: replace enum with int constants
     */
    public enum PhotoPosition {
        LEFT,
        RIGHT
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    static public final PhotoPosition getDefaultPhotoPosition(boolean opposite) {
        final Locale locale = Locale.getDefault();
        final int layoutDirection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)?
                TextUtils.getLayoutDirectionFromLocale(locale) : View.LAYOUT_DIRECTION_LTR;
        switch (layoutDirection) {
            case View.LAYOUT_DIRECTION_RTL:
                return (opposite ? PhotoPosition.LEFT : PhotoPosition.RIGHT);
            case View.LAYOUT_DIRECTION_LTR:
            default:
                return (opposite ? PhotoPosition.RIGHT : PhotoPosition.LEFT);
        }
    }

    private PhotoPosition mPhotoPosition = getDefaultPhotoPosition(false /* normal/non opposite */);

    // Header layout data
    private TextView mHeaderTextView;
    private boolean mIsSectionHeaderEnabled;

    // The views inside the contact view
    private boolean mQuickContactEnabled = true;
    private QuickContactBadge mQuickContact;
    private ImageView mPhotoView;
    private TextView mNameTextView;
    private TextView mPhoneticNameTextView;
    private TextView mLabelView;
    private TextView mDataView;
    private TextView mSnippetView;
    private TextView mStatusView;
    private ImageView mPresenceIcon;

//    private ColorStateList mSecondaryTextColor;
    private int mSecondaryTextColor;


    private int mDefaultPhotoViewSize = 0;
    /**
     * Can be effective even when {@link #mPhotoView} is null, as we want to have horizontal padding
     * to align other data in this View.
     */
    private int mPhotoViewWidth;
    /**
     * Can be effective even when {@link #mPhotoView} is null, as we want to have vertical padding.
     */
    private int mPhotoViewHeight;

    /**
     * Only effective when {@link #mPhotoView} is null.
     * When true all the Views on the right side of the photo should have horizontal padding on
     * those left assuming there is a photo.
     */
    private boolean mKeepHorizontalPaddingForPhotoView;
    /**
     * Only effective when {@link #mPhotoView} is null.
     */
    private boolean mKeepVerticalPaddingForPhotoView;

    /**
     * True when {@link #mPhotoViewWidth} and {@link #mPhotoViewHeight} are ready for being used.
     * False indicates those values should be updated before being used in position calculation.
     */
    private boolean mPhotoViewWidthAndHeightAreReady = false;

    private int mNameTextViewHeight;
    private int mNameTextViewTextColor = Color.BLACK;
    private int mPhoneticNameTextViewHeight;
    private int mLabelViewHeight;
    private int mDataViewHeight;
    private int mSnippetTextViewHeight;
    private int mStatusTextViewHeight;

    // Holds Math.max(mLabelTextViewHeight, mDataViewHeight), assuming Label and Data share the
    // same row.
    private int mLabelAndDataViewMaxHeight;

    // TODO: some TextView fields are using CharArrayBuffer while some are not. Determine which is
    // more efficient for each case or in general, and simplify the whole implementation.
    // Note: if we're sure MARQUEE will be used every time, there's no reason to use
    // CharArrayBuffer, since MARQUEE requires Span and thus we need to copy characters inside the
    // buffer to Spannable once, while CharArrayBuffer is for directly applying char array to
    // TextView without any modification.
    private final CharArrayBuffer mDataBuffer = new CharArrayBuffer(128);
    private final CharArrayBuffer mPhoneticNameBuffer = new CharArrayBuffer(128);

    private boolean mActivatedStateSupported;
    private boolean mAdjustSelectionBoundsEnabled = true;

    private Rect mBoundsWithoutHeader = new Rect();

    /** A helper used to highlight a prefix in a text field. */
    private final TextHighlighter mTextHighlighter;
    private CharSequence mUnknownNameText;

    public ContactListItemView(Context context) {
        super(context);

        mTextHighlighter = new TextHighlighter(Typeface.BOLD);
        mNameHighlightSequence = new ArrayList<HighlightSequence>();
        mNumberHighlightSequence = new ArrayList<HighlightSequence>();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ContactListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Read all style values
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);
        mPreferredHeight = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_height, mPreferredHeight);
        mActivatedBackgroundDrawable = a.getDrawable(
                R.styleable.ContactListItemView_activated_background);

//        mActivatedBackgroundDrawable = context.getResources().getDrawable(R.drawable.black_dark_gray);

        mGapBetweenImageAndText = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_gap_between_image_and_text,
                mGapBetweenImageAndText);
        mGapBetweenLabelAndData = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_gap_between_label_and_data,
                mGapBetweenLabelAndData);
        mPresenceIconMargin = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_presence_icon_margin,
                mPresenceIconMargin);
        mPresenceIconSize = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_presence_icon_size, mPresenceIconSize);
        mDefaultPhotoViewSize = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_photo_size, mDefaultPhotoViewSize);
        mTextIndent = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_text_indent, mTextIndent);
        mTextOffsetTop = a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_text_offset_top, mTextOffsetTop);
        mDataViewWidthWeight = a.getInteger(
                R.styleable.ContactListItemView_list_item_data_width_weight, mDataViewWidthWeight);
        mLabelViewWidthWeight = a.getInteger(
                R.styleable.ContactListItemView_list_item_label_width_weight,
                mLabelViewWidthWeight);
        mNameTextViewTextColor = a.getColor(
                R.styleable.ContactListItemView_list_item_name_text_color, mNameTextViewTextColor);
        mNameTextViewTextSize = (int) a.getDimension(
                R.styleable.ContactListItemView_list_item_name_text_size,
                (int) getResources().getDimension(R.dimen.contact_browser_list_item_text_size));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            setPaddingRelative(
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_left, 0),
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_top, 0),
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_right, 0),
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_bottom, 0));
        }
        else {
            setPadding(
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_left, 0),
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_top, 0),
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_right, 0),
                    a.getDimensionPixelOffset(
                            R.styleable.ContactListItemView_list_item_padding_bottom, 0));
        }

        mTextHighlighter = new TextHighlighter(Typeface.BOLD);
        mSecondaryTextColor = a.getColor(R.styleable.ContactListItemView_list_item_textColorSecondary, 0);

        a.recycle();

//        a = getContext().obtainStyledAttributes(R.styleable.Theme);
//        mSecondaryTextColor = a.getColorStateList(R.styleable.Theme_android_textColorSecondary);
//        a.recycle();

        mHeaderWidth =
                getResources().getDimensionPixelSize(R.dimen.contact_list_section_header_width);

        if (mActivatedBackgroundDrawable != null) {
            mActivatedBackgroundDrawable.setCallback(this);
        }

        mNameHighlightSequence = new ArrayList<HighlightSequence>();
        mNumberHighlightSequence = new ArrayList<HighlightSequence>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
    }

    public void setUnknownNameText(CharSequence unknownNameText) {
        mUnknownNameText = unknownNameText;
    }

    public void setQuickContactEnabled(boolean flag) {
        mQuickContactEnabled = flag;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We will match parent's width and wrap content vertically, but make sure
        // height is no less than listPreferredItemHeight.
        final int specWidth = resolveSize(0, widthMeasureSpec);
        final int preferredHeight = mPreferredHeight;

        mNameTextViewHeight = 0;
        mPhoneticNameTextViewHeight = 0;
        mLabelViewHeight = 0;
        mDataViewHeight = 0;
        mLabelAndDataViewMaxHeight = 0;
        mSnippetTextViewHeight = 0;
        mStatusTextViewHeight = 0;

        ensurePhotoViewSize();

        // Width each TextView is able to use.
        int effectiveWidth;
        // All the other Views will honor the photo, so available width for them may be shrunk.
        if (mPhotoViewWidth > 0 || mKeepHorizontalPaddingForPhotoView) {
            effectiveWidth = specWidth - getPaddingLeft() - getPaddingRight()
                    - (mPhotoViewWidth + mGapBetweenImageAndText);
        } else {
            effectiveWidth = specWidth - getPaddingLeft() - getPaddingRight();
        }

        if (mIsSectionHeaderEnabled) {
            effectiveWidth -= mHeaderWidth + mGapBetweenImageAndText;
        }

        // Go over all visible text views and measure actual width of each of them.
        // Also calculate their heights to get the total height for this entire view.

        if (isVisible(mNameTextView)) {
            // Calculate width for name text - this parallels similar measurement in onLayout.
            int nameTextWidth = effectiveWidth;
            if (mPhotoPosition != PhotoPosition.LEFT) {
                nameTextWidth -= mTextIndent;
            }
            mNameTextView.measure(
                    MeasureSpec.makeMeasureSpec(nameTextWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mNameTextViewHeight = mNameTextView.getMeasuredHeight();
        }

        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.measure(
                    MeasureSpec.makeMeasureSpec(effectiveWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mPhoneticNameTextViewHeight = mPhoneticNameTextView.getMeasuredHeight();
        }

        // If both data (phone number/email address) and label (type like "MOBILE") are quite long,
        // we should ellipsize both using appropriate ratio.
        final int dataWidth;
        final int labelWidth;
        if (isVisible(mDataView)) {
            if (isVisible(mLabelView)) {
                final int totalWidth = effectiveWidth - mGapBetweenLabelAndData;
                dataWidth = ((totalWidth * mDataViewWidthWeight)
                        / (mDataViewWidthWeight + mLabelViewWidthWeight));
                labelWidth = ((totalWidth * mLabelViewWidthWeight) /
                        (mDataViewWidthWeight + mLabelViewWidthWeight));
            } else {
                dataWidth = effectiveWidth;
                labelWidth = 0;
            }
        } else {
            dataWidth = 0;
            if (isVisible(mLabelView)) {
                labelWidth = effectiveWidth;
            } else {
                labelWidth = 0;
            }
        }

        if (isVisible(mDataView)) {
            mDataView.measure(MeasureSpec.makeMeasureSpec(dataWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mDataViewHeight = mDataView.getMeasuredHeight();
        }

        if (isVisible(mLabelView)) {
            // For performance reason we don't want AT_MOST usually, but when the picture is
            // on right, we need to use it anyway because mDataView is next to mLabelView.
            final int mode = (mPhotoPosition == PhotoPosition.LEFT
                    ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
            mLabelView.measure(MeasureSpec.makeMeasureSpec(labelWidth, mode),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mLabelViewHeight = mLabelView.getMeasuredHeight();
        }
        mLabelAndDataViewMaxHeight = Math.max(mLabelViewHeight, mDataViewHeight);

        if (isVisible(mSnippetView)) {
            mSnippetView.measure(
                    MeasureSpec.makeMeasureSpec(effectiveWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mSnippetTextViewHeight = mSnippetView.getMeasuredHeight();
        }

        // Status view height is the biggest of the text view and the presence icon
        if (isVisible(mPresenceIcon)) {
            mPresenceIcon.measure(
                    MeasureSpec.makeMeasureSpec(mPresenceIconSize, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mPresenceIconSize, MeasureSpec.EXACTLY));
            mStatusTextViewHeight = mPresenceIcon.getMeasuredHeight();
        }

        if (isVisible(mStatusView)) {
            // Presence and status are in a same row, so status will be affected by icon size.
            final int statusWidth;
            if (isVisible(mPresenceIcon)) {
                statusWidth = (effectiveWidth - mPresenceIcon.getMeasuredWidth()
                        - mPresenceIconMargin);
            } else {
                statusWidth = effectiveWidth;
            }
            mStatusView.measure(MeasureSpec.makeMeasureSpec(statusWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mStatusTextViewHeight =
                    Math.max(mStatusTextViewHeight, mStatusView.getMeasuredHeight());
        }

        // Calculate height including padding.
        int height = (mNameTextViewHeight + mPhoneticNameTextViewHeight +
                mLabelAndDataViewMaxHeight +
                mSnippetTextViewHeight + mStatusTextViewHeight);

        // Make sure the height is at least as high as the photo
        height = Math.max(height, mPhotoViewHeight + getPaddingBottom() + getPaddingTop());

        // Make sure height is at least the preferred height
        height = Math.max(height, preferredHeight);

        // Measure the header if it is visible.
        if (mHeaderTextView != null && mHeaderTextView.getVisibility() == VISIBLE) {
            mHeaderTextView.measure(
                    MeasureSpec.makeMeasureSpec(mHeaderWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }

        setMeasuredDimension(specWidth, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int height = bottom - top;
        final int width = right - left;

        // Determine the vertical bounds by laying out the header first.
        int topBound = 0;
        int bottomBound = height;
        int leftBound = getPaddingLeft();
        int rightBound = width - getPaddingRight();

        final boolean isLayoutRtl = ViewUtil.isViewLayoutRtl(this);

        // Put the section header on the left side of the contact view.
        if (mIsSectionHeaderEnabled) {
            if (mHeaderTextView != null) {
                int headerHeight = mHeaderTextView.getMeasuredHeight();
                int headerTopBound = (bottomBound + topBound - headerHeight) / 2 + mTextOffsetTop;

                mHeaderTextView.layout(
                        isLayoutRtl ? rightBound - mHeaderWidth : leftBound,
                        headerTopBound,
                        isLayoutRtl ? rightBound : leftBound + mHeaderWidth,
                        headerTopBound + headerHeight);
            }
            if (isLayoutRtl) {
                rightBound -= mHeaderWidth;
            } else {
                leftBound += mHeaderWidth;
            }
        }

        mBoundsWithoutHeader.set(left + leftBound, topBound, left + rightBound, bottomBound);
        mLeftOffset = left + leftBound;
        mRightOffset = left + rightBound;
        if (mIsSectionHeaderEnabled) {
            if (isLayoutRtl) {
                rightBound -= mGapBetweenImageAndText;
            } else {
                leftBound += mGapBetweenImageAndText;
            }
        }

        if (mActivatedStateSupported && isActivated()) {
            mActivatedBackgroundDrawable.setBounds(mBoundsWithoutHeader);
        }

        final View photoView = mQuickContact != null ? mQuickContact : mPhotoView;
        if (mPhotoPosition == PhotoPosition.LEFT) {
            // Photo is the left most view. All the other Views should on the right of the photo.
            if (photoView != null) {
                // Center the photo vertically
                final int photoTop = topBound + (bottomBound - topBound - mPhotoViewHeight) / 2;
                photoView.layout(
                        leftBound,
                        photoTop,
                        leftBound + mPhotoViewWidth,
                        photoTop + mPhotoViewHeight);
                leftBound += mPhotoViewWidth + mGapBetweenImageAndText;
            } else if (mKeepHorizontalPaddingForPhotoView) {
                // Draw nothing but keep the padding.
                leftBound += mPhotoViewWidth + mGapBetweenImageAndText;
            }
        } else {
            // Photo is the right most view. Right bound should be adjusted that way.
            if (photoView != null) {
                // Center the photo vertically
                final int photoTop = topBound + (bottomBound - topBound - mPhotoViewHeight) / 2;
                photoView.layout(
                        rightBound - mPhotoViewWidth,
                        photoTop,
                        rightBound,
                        photoTop + mPhotoViewHeight);
                rightBound -= (mPhotoViewWidth + mGapBetweenImageAndText);
            } else if (mKeepHorizontalPaddingForPhotoView) {
                // Draw nothing but keep the padding.
                rightBound -= (mPhotoViewWidth + mGapBetweenImageAndText);
            }

            // Add indent between left-most padding and texts.
            leftBound += mTextIndent;
        }

        // Center text vertically, then apply the top offset.
        final int totalTextHeight = mNameTextViewHeight + mPhoneticNameTextViewHeight +
                mLabelAndDataViewMaxHeight + mSnippetTextViewHeight + mStatusTextViewHeight;
        int textTopBound = (bottomBound + topBound - totalTextHeight) / 2 + mTextOffsetTop;

        // Layout all text view and presence icon
        // Put name TextView first
        if (isVisible(mNameTextView)) {
            mNameTextView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mNameTextViewHeight);
            textTopBound += mNameTextViewHeight;
        }

        // Presence and status
        if (isLayoutRtl) {
            int statusRightBound = rightBound;
            if (isVisible(mPresenceIcon)) {
                int iconWidth = mPresenceIcon.getMeasuredWidth();
                mPresenceIcon.layout(
                        rightBound - iconWidth,
                        textTopBound,
                        rightBound,
                        textTopBound + mStatusTextViewHeight);
                statusRightBound -= (iconWidth + mPresenceIconMargin);
            }

            if (isVisible(mStatusView)) {
                mStatusView.layout(leftBound,
                        textTopBound,
                        statusRightBound,
                        textTopBound + mStatusTextViewHeight);
            }
        } else {
            int statusLeftBound = leftBound;
            if (isVisible(mPresenceIcon)) {
                int iconWidth = mPresenceIcon.getMeasuredWidth();
                mPresenceIcon.layout(
                        leftBound,
                        textTopBound,
                        leftBound + iconWidth,
                        textTopBound + mStatusTextViewHeight);
                statusLeftBound += (iconWidth + mPresenceIconMargin);
            }

            if (isVisible(mStatusView)) {
                mStatusView.layout(statusLeftBound,
                        textTopBound,
                        rightBound,
                        textTopBound + mStatusTextViewHeight);
            }
        }

        if (isVisible(mStatusView) || isVisible(mPresenceIcon)) {
            textTopBound += mStatusTextViewHeight;
        }

        // Rest of text views
        int dataLeftBound = leftBound;
        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mPhoneticNameTextViewHeight);
            textTopBound += mPhoneticNameTextViewHeight;
        }

        // Label and Data align bottom.
        if (isVisible(mLabelView)) {
            if (mPhotoPosition == PhotoPosition.LEFT) {
                // When photo is on left, label is placed on the right edge of the list item.
                mLabelView.layout(rightBound - mLabelView.getMeasuredWidth(),
                        textTopBound + mLabelAndDataViewMaxHeight - mLabelViewHeight,
                        rightBound,
                        textTopBound + mLabelAndDataViewMaxHeight);
                rightBound -= mLabelView.getMeasuredWidth();
            } else {
                // When photo is on right, label is placed on the left of data view.
                dataLeftBound = leftBound + mLabelView.getMeasuredWidth();
                mLabelView.layout(leftBound,
                        textTopBound + mLabelAndDataViewMaxHeight - mLabelViewHeight,
                        dataLeftBound,
                        textTopBound + mLabelAndDataViewMaxHeight);
                dataLeftBound += mGapBetweenLabelAndData;
            }
        }

        if (isVisible(mDataView)) {
            mDataView.layout(dataLeftBound,
                    textTopBound + mLabelAndDataViewMaxHeight - mDataViewHeight,
                    rightBound,
                    textTopBound + mLabelAndDataViewMaxHeight);
        }
        if (isVisible(mLabelView) || isVisible(mDataView)) {
            textTopBound += mLabelAndDataViewMaxHeight;
        }

        if (isVisible(mSnippetView)) {
            mSnippetView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mSnippetTextViewHeight);
        }
    }

    @Override
    public void adjustListItemSelectionBounds(Rect bounds) {
        if (mAdjustSelectionBoundsEnabled) {
            bounds.top += mBoundsWithoutHeader.top;
            bounds.bottom = bounds.top + mBoundsWithoutHeader.height();
            bounds.left = mBoundsWithoutHeader.left;
            bounds.right = mBoundsWithoutHeader.right;
        }
    }

    protected boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /**
     * Extracts width and height from the style
     */
    private void ensurePhotoViewSize() {
        if (!mPhotoViewWidthAndHeightAreReady) {
            mPhotoViewWidth = mPhotoViewHeight = getDefaultPhotoViewSize();
            if (!mQuickContactEnabled && mPhotoView == null) {
                if (!mKeepHorizontalPaddingForPhotoView) {
                    mPhotoViewWidth = 0;
                }
                if (!mKeepVerticalPaddingForPhotoView) {
                    mPhotoViewHeight = 0;
                }
            }

            mPhotoViewWidthAndHeightAreReady = true;
        }
    }

    protected int getDefaultPhotoViewSize() {
        return mDefaultPhotoViewSize;
    }

    /**
     * Gets a LayoutParam that corresponds to the default photo size.
     *
     * @return A new LayoutParam.
     */
    private LayoutParams getDefaultPhotoLayoutParams() {
        LayoutParams params = generateDefaultLayoutParams();
        params.width = getDefaultPhotoViewSize();
        params.height = params.width;
        return params;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mActivatedStateSupported) {
            mActivatedBackgroundDrawable.setState(getDrawableState());
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mActivatedBackgroundDrawable || super.verifyDrawable(who);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mActivatedStateSupported) {
            mActivatedBackgroundDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        if (mActivatedStateSupported && isActivated()) {
            mActivatedBackgroundDrawable.draw(canvas);
        }

        super.dispatchDraw(canvas);
    }

    /**
     * Sets section header or makes it invisible if the title is null.
     */
    public void setSectionHeader(String title, int style) {
        if (!TextUtils.isEmpty(title)) {
            if (mHeaderTextView == null) {
                mHeaderTextView = new TextView(getContext());
                mHeaderTextView.setTextColor(ViewUtil.getColorIdFromAttributeId(getContext(), R.attr.sp_activity_primary_text_color));
                mHeaderTextView.setTextAppearance(getContext(), style > 0 ? style : R.style.SectionHeaderStyle);
                mHeaderTextView.setGravity(
                        ViewUtil.isViewLayoutRtl(this) ? Gravity.RIGHT : Gravity.LEFT);
                addView(mHeaderTextView);
            }
            setMarqueeText(mHeaderTextView, title);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderTextView.setAllCaps(true);
        } else if (mHeaderTextView != null) {
            mHeaderTextView.setVisibility(View.GONE);
        }
    }

    public void setIsSectionHeaderEnabled(boolean isSectionHeaderEnabled) {
        mIsSectionHeaderEnabled = isSectionHeaderEnabled;
    }

    /**
     * Returns the quick contact badge, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public QuickContactBadge getQuickContact() {
        if (!mQuickContactEnabled) {
            throw new IllegalStateException("QuickContact is disabled for this view");
        }
        if (mQuickContact == null) {
            mQuickContact = new QuickContactBadge(getContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                mQuickContact.setOverlay(null);
            mQuickContact.setLayoutParams(getDefaultPhotoLayoutParams());
            if (mNameTextView != null) {
                mQuickContact.setContentDescription(getContext().getString(
                        R.string.description_quick_contact_for, mNameTextView.getText()));
            }

            addView(mQuickContact);
            mPhotoViewWidthAndHeightAreReady = false;
        }
        return mQuickContact;
    }

    /**
     * Returns the photo view, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @SuppressWarnings("deprecation")
    public ImageView getPhotoView() {
        if (mPhotoView == null) {
            mPhotoView = new ImageView(getContext());
            mPhotoView.setLayoutParams(getDefaultPhotoLayoutParams());
            // Quick contact style used above will set a background - remove it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                mPhotoView.setBackground(null);
            else
                mPhotoView.setBackgroundDrawable(null);
            addView(mPhotoView);
            mPhotoViewWidthAndHeightAreReady = false;
        }
        return mPhotoView;
    }

    /**
     * Removes the photo view.
     */
    public void removePhotoView() {
        removePhotoView(false, true);
    }

    /**
     * Removes the photo view.
     *
     * @param keepHorizontalPadding True means data on the right side will have
     *            padding on left, pretending there is still a photo view.
     * @param keepVerticalPadding True means the View will have some height
     *            enough for accommodating a photo view.
     */
    public void removePhotoView(boolean keepHorizontalPadding, boolean keepVerticalPadding) {
        mPhotoViewWidthAndHeightAreReady = false;
        mKeepHorizontalPaddingForPhotoView = keepHorizontalPadding;
        mKeepVerticalPaddingForPhotoView = keepVerticalPadding;
        if (mPhotoView != null) {
            removeView(mPhotoView);
            mPhotoView = null;
        }
        if (mQuickContact != null) {
            removeView(mQuickContact);
            mQuickContact = null;
        }
    }

    /**
     * Sets a word prefix that will be highlighted if encountered in fields like
     * name and search snippet. This will disable the mask highlighting for names.
     * <p>
     * NOTE: must be all upper-case
     */
    public void setHighlightedPrefix(String upperCasePrefix) {
        mHighlightedPrefix = upperCasePrefix;
    }

    /**
     * Clears previously set highlight sequences for the view.
     */
    public void clearHighlightSequences() {
        mNameHighlightSequence.clear();
        mNumberHighlightSequence.clear();
        mHighlightedPrefix = null;
    }

    /**
     * Adds a highlight sequence to the name highlighter.
     * @param start The start position of the highlight sequence.
     * @param end The end position of the highlight sequence.
     */
    public void addNameHighlightSequence(int start, int end) {
        mNameHighlightSequence.add(new HighlightSequence(start, end));
    }

    /**
     * Adds a highlight sequence to the number highlighter.
     * @param start The start position of the highlight sequence.
     * @param end The end position of the highlight sequence.
     */
    public void addNumberHighlightSequence(int start, int end) {
        mNumberHighlightSequence.add(new HighlightSequence(start, end));
    }

    /**
     * Returns the text view for the contact name, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public TextView getNameTextView() {
        if (mNameTextView == null) {
            mNameTextView = new TextView(getContext());
            mNameTextView.setSingleLine(true);
            mNameTextView.setEllipsize(getTextEllipsis());
            mNameTextView.setTextColor(mNameTextViewTextColor);
            mNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mNameTextViewTextSize);
            // Manually call setActivated() since this view may be added after the first
            // setActivated() call toward this whole item view.
            mNameTextView.setActivated(isActivated());
            mNameTextView.setGravity(Gravity.CENTER_VERTICAL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                mNameTextView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            mNameTextView.setId(R.id.cliv_name_textview);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                mNameTextView.setElegantTextHeight(false);
            addView(mNameTextView);
        }
        return mNameTextView;
    }

    /**
     * Adds or updates a text view for the phonetic name.
     */
    public void setPhoneticName(char[] text, int size) {
        if (text == null || size == 0) {
            if (mPhoneticNameTextView != null) {
                mPhoneticNameTextView.setVisibility(View.GONE);
            }
        } else {
            getPhoneticNameTextView();
            setMarqueeText(mPhoneticNameTextView, text, size);
            mPhoneticNameTextView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the phonetic name, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public TextView getPhoneticNameTextView() {
        if (mPhoneticNameTextView == null) {
            mPhoneticNameTextView = new TextView(getContext());
            mPhoneticNameTextView.setSingleLine(true);
            mPhoneticNameTextView.setEllipsize(getTextEllipsis());
            mPhoneticNameTextView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                mPhoneticNameTextView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            mPhoneticNameTextView.setTypeface(mPhoneticNameTextView.getTypeface(), Typeface.BOLD);
            mPhoneticNameTextView.setActivated(isActivated());
            mPhoneticNameTextView.setId(R.id.cliv_phoneticname_textview);
            addView(mPhoneticNameTextView);
        }
        return mPhoneticNameTextView;
    }

    /**
     * Adds or updates a text view for the data label.
     */
    public void setLabel(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mLabelView != null) {
                mLabelView.setVisibility(View.GONE);
            }
        } else {
            getLabelView();
            setMarqueeText(mLabelView, text);
            mLabelView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the data label, creating it if necessary.
     */
    public TextView getLabelView() {
        if (mLabelView == null) {
            mLabelView = new TextView(getContext());
            mLabelView.setSingleLine(true);
            mLabelView.setEllipsize(getTextEllipsis());
            mLabelView.setTextAppearance(getContext(), R.style.TextAppearanceSmall);
            if (mPhotoPosition == PhotoPosition.LEFT) {
                mLabelView.setAllCaps(true);
                mLabelView.setGravity(Gravity.END);
            } else {
                mLabelView.setTypeface(mLabelView.getTypeface(), Typeface.BOLD);
            }
            mLabelView.setActivated(isActivated());
            mLabelView.setId(R.id.cliv_label_textview);
            addView(mLabelView);
        }
        return mLabelView;
    }

    /**
     * Adds or updates a text view for the data element.
     */
    public void setData(char[] text, int size) {
        if (text == null || size == 0) {
            if (mDataView != null) {
                mDataView.setVisibility(View.GONE);
            }
        } else {
            getDataView();
            setMarqueeText(mDataView, text, size);
            mDataView.setVisibility(VISIBLE);
        }
    }

    /**
     * Sets phone number for a list item. This takes care of number highlighting if the highlight
     * mask exists.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void setPhoneNumber(String text, String countryIso) {
        if (text == null) {
            if (mDataView != null) {
                mDataView.setVisibility(View.GONE);
            }
        } else {
            getDataView();

            // TODO: Format number using PhoneNumberUtils.formatNumber before assigning it to
            // mDataView. Make sure that determination of the highlight sequences are done only
            // after number formatting.

            // Sets phone number texts for display after highlighting it, if applicable.
            // CharSequence textToSet = text;
            final SpannableString textToSet = new SpannableString(text);

            if (mNumberHighlightSequence.size() != 0) {
                final HighlightSequence highlightSequence = mNumberHighlightSequence.get(0);
                mTextHighlighter.applyMaskingHighlight(textToSet, highlightSequence.start,
                        highlightSequence.end);
            }

            setMarqueeText(mDataView, textToSet);
            mDataView.setVisibility(VISIBLE);

            // We have a phone number as "mDataView" so make it always LTR and VIEW_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mDataView.setTextDirection(View.TEXT_DIRECTION_LTR);
                mDataView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            }
        }
    }

    private void setMarqueeText(TextView textView, char[] text, int size) {
        if (getTextEllipsis() == TruncateAt.MARQUEE) {
            setMarqueeText(textView, new String(text, 0, size));
        } else {
            textView.setText(text, 0, size);
        }
    }

    private void setMarqueeText(TextView textView, CharSequence text) {
        if (getTextEllipsis() == TruncateAt.MARQUEE) {
            // To show MARQUEE correctly (with END effect during non-active state), we need
            // to build Spanned with MARQUEE in addition to TextView's ellipsize setting.
            final SpannableString spannable = new SpannableString(text);
            spannable.setSpan(TruncateAt.MARQUEE, 0, spannable.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannable);
        } else {
            textView.setText(text);
        }
    }

    /**
     * Returns the text view for the data text, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public TextView getDataView() {
        if (mDataView == null) {
            mDataView = new TextView(getContext());
            mDataView.setSingleLine(true);
            mDataView.setEllipsize(getTextEllipsis());
            mDataView.setTextAppearance(getContext(), R.style.TextAppearanceSmall);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                mDataView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            mDataView.setActivated(isActivated());
            mDataView.setId(R.id.cliv_data_view);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                mDataView.setElegantTextHeight(false);
            addView(mDataView);
        }
        return mDataView;
    }

    /**
     * Adds or updates a text view for the search snippet.
     */
    public void setSnippet(String text) {
        if (TextUtils.isEmpty(text)) {
            if (mSnippetView != null) {
                mSnippetView.setVisibility(View.GONE);
            }
        } else {
            mTextHighlighter.setPrefixText(getSnippetView(), text, mHighlightedPrefix);
            mSnippetView.setVisibility(VISIBLE);
            if (ContactDisplayUtils.isPossiblePhoneNumber(text)) {
                // Give the text-to-speech engine a hint that it's a phone number
                mSnippetView.setContentDescription(
                        ContactDisplayUtils.getTelephoneTtsSpannable(text));
            } else {
                mSnippetView.setContentDescription(null);
            }
        }
    }

    /**
     * Returns the text view for the search snippet, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public TextView getSnippetView() {
        if (mSnippetView == null) {
            mSnippetView = new TextView(getContext());
            mSnippetView.setSingleLine(true);
            mSnippetView.setEllipsize(getTextEllipsis());
            mSnippetView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                mSnippetView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            mSnippetView.setActivated(isActivated());
            addView(mSnippetView);
        }
        return mSnippetView;
    }

    /**
     * Returns the text view for the status, creating it if necessary.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public TextView getStatusView() {
        if (mStatusView == null) {
            mStatusView = new TextView(getContext());
            mStatusView.setSingleLine(true);
            mStatusView.setEllipsize(getTextEllipsis());
            mStatusView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small);
            mStatusView.setTextColor(mSecondaryTextColor);
            mStatusView.setActivated(isActivated());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                mStatusView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            addView(mStatusView);
        }
        return mStatusView;
    }

    /**
     * Adds or updates a text view for the status.
     */
    public void setStatus(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mStatusView != null) {
                mStatusView.setVisibility(View.GONE);
            }
        } else {
            getStatusView();
            setMarqueeText(mStatusView, text);
            mStatusView.setVisibility(VISIBLE);
        }
    }

    /**
     * Adds or updates the presence icon view.
     */
    public void setPresence(Drawable icon) {
        if (icon != null) {
            if (mPresenceIcon == null) {
                mPresenceIcon = new ImageView(getContext());
                addView(mPresenceIcon);
            }
            mPresenceIcon.setImageDrawable(icon);
            mPresenceIcon.setScaleType(ScaleType.CENTER);
            mPresenceIcon.setVisibility(View.VISIBLE);
        } else {
            if (mPresenceIcon != null) {
                mPresenceIcon.setVisibility(View.GONE);
            }
        }
    }

    private TruncateAt getTextEllipsis() {
        return TruncateAt.MARQUEE;
    }

    public void showDisplayName(Cursor cursor, int nameColumnIndex, int displayOrder) {
        CharSequence name = cursor.getString(nameColumnIndex);
        setDisplayName(name);

        // Since the quick contact content description is derived from the display name and there is
        // no guarantee that when the quick contact is initialized the display name is already set,
        // do it here too.
        if (mQuickContact != null) {
            mQuickContact.setContentDescription(getContext().getString(
                    R.string.description_quick_contact_for, mNameTextView.getText()));
        }
    }

    public void setDisplayName(CharSequence name, boolean highlight) {
        if (!TextUtils.isEmpty(name) && highlight) {
            clearHighlightSequences();
            addNameHighlightSequence(0, name.length());
        }
        setDisplayName(name);
    }

    public void setDisplayName(CharSequence name) {
        if (!TextUtils.isEmpty(name)) {
            // Chooses the available highlighting method for highlighting.
            if (mHighlightedPrefix != null) {
                name = mTextHighlighter.applyPrefixHighlight(name, mHighlightedPrefix);
            } else if (mNameHighlightSequence.size() != 0) {
                final SpannableString spannableName = new SpannableString(name);
                for (HighlightSequence highlightSequence : mNameHighlightSequence) {
                    mTextHighlighter.applyMaskingHighlight(spannableName, highlightSequence.start,
                            highlightSequence.end);
                }
                name = spannableName;
            }
        } else {
            name = mUnknownNameText;
        }
        setMarqueeText(getNameTextView(), name);

        if (ContactDisplayUtils.isPossiblePhoneNumber(name)) {
            // Give the text-to-speech engine a hint that it's a phone number
            mNameTextView.setContentDescription(
                    ContactDisplayUtils.getTelephoneTtsSpannable(name.toString()));
        } else {
            mNameTextView.setContentDescription(null);
        }
    }

    public void hideDisplayName() {
        if (mNameTextView != null) {
            removeView(mNameTextView);
            mNameTextView = null;
        }
    }

    public void showPhoneticName(Cursor cursor, int phoneticNameColumnIndex) {
        cursor.copyStringToBuffer(phoneticNameColumnIndex, mPhoneticNameBuffer);
        int phoneticNameSize = mPhoneticNameBuffer.sizeCopied;
        if (phoneticNameSize != 0) {
            setPhoneticName(mPhoneticNameBuffer.data, phoneticNameSize);
        } else {
            setPhoneticName(null, 0);
        }
    }

    public void hidePhoneticName() {
        if (mPhoneticNameTextView != null) {
            removeView(mPhoneticNameTextView);
            mPhoneticNameTextView = null;
        }
    }

    /**
     * Sets the proper icon (star or presence or nothing) and/or status message.
     */
    public void showPresenceAndStatusMessage(Cursor cursor, int presenceColumnIndex,
            int contactStatusColumnIndex) {
        Drawable icon = null;
        int presence = 0;
        if (!cursor.isNull(presenceColumnIndex)) {
            presence = cursor.getInt(presenceColumnIndex);
            icon = ContactPresenceIconUtil.getPresenceIcon(getContext(), presence);
        }
        setPresence(icon);

        String statusMessage = null;
        if (contactStatusColumnIndex != 0 && !cursor.isNull(contactStatusColumnIndex)) {
            statusMessage = cursor.getString(contactStatusColumnIndex);
        }
        // If there is no status message from the contact, but there was a presence value, then use
        // the default status message string
        if (statusMessage == null && presence != 0) {
            statusMessage = ContactStatusUtil.getStatusString(getContext(), presence);
        }
        setStatus(statusMessage);
    }

    /**
     * Shows search snippet.
     */
    public void showSnippet(Cursor cursor, int summarySnippetColumnIndex) {
        if (cursor.getColumnCount() <= summarySnippetColumnIndex) {
            setSnippet(null);
            return;
        }

        String snippet = cursor.getString(summarySnippetColumnIndex);

        // Do client side snippeting if provider didn't do it
        final Bundle extras = cursor.getExtras();
        if (extras.getBoolean(ContactsContract.DEFERRED_SNIPPETING)) {

            final String query = extras.getString(ContactsContract.DEFERRED_SNIPPETING_QUERY);

            String displayName = null;
            int displayNameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            if (displayNameIndex >= 0) {
                displayName = cursor.getString(displayNameIndex);
            }

            snippet = updateSnippet(snippet, query, displayName);

        } else {
            if (snippet != null) {
                int from = 0;
                int to = snippet.length();
                int start = snippet.indexOf(ScDefaultContactListAdapter.SNIPPET_START_MATCH);
                if (start == -1) {
                    snippet = null;
                } else {
                    int firstNl = snippet.lastIndexOf('\n', start);
                    if (firstNl != -1) {
                        from = firstNl + 1;
                    }
                    int end = snippet.lastIndexOf(ScDefaultContactListAdapter.SNIPPET_END_MATCH);
                    if (end != -1) {
                        int lastNl = snippet.indexOf('\n', end);
                        if (lastNl != -1) {
                            to = lastNl;
                        }
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = from; i < to; i++) {
                        char c = snippet.charAt(i);
                        if (c != ScDefaultContactListAdapter.SNIPPET_START_MATCH &&
                                c != ScDefaultContactListAdapter.SNIPPET_END_MATCH) {
                            sb.append(c);
                        }
                    }
                    snippet = sb.toString();
                }
            }
        }

        setSnippet(snippet);
    }

    /**
     * Used for deferred snippets from the database. The contents come back as large strings which
     * need to be extracted for display.
     *
     * @param snippet The snippet from the database.
     * @param query The search query substring.
     * @param displayName The contact display name.
     * @return The proper snippet to display.
     */
    private String updateSnippet(String snippet, String query, String displayName) {

        if (TextUtils.isEmpty(snippet) || TextUtils.isEmpty(query)) {
            return null;
        }
        query = SearchUtil.cleanStartAndEndOfSearchQuery(query.toLowerCase());

        // If the display name already contains the query term, return empty - snippets should
        // not be needed in that case.
        if (!TextUtils.isEmpty(displayName)) {
            final String lowerDisplayName = displayName.toLowerCase();
            final List<String> nameTokens = split(lowerDisplayName);
            for (String nameToken : nameTokens) {
                if (nameToken.startsWith(query)) {
                    return null;
                }
            }
        }

        // The snippet may contain multiple data lines.
        // Show the first line that matches the query.
        final SearchUtil.MatchedLine matched = SearchUtil.findMatchingLine(snippet, query);

        if (matched != null && matched.line != null) {
            // Tokenize for long strings since the match may be at the end of it.
            // Skip this part for short strings since the whole string will be displayed.
            // Most contact strings are short so the snippetize method will be called infrequently.
            final int lengthThreshold = getResources().getInteger(
                    R.integer.snippet_length_before_tokenize);
            if (matched.line.length() > lengthThreshold) {
                return snippetize(matched.line, matched.startIndex, lengthThreshold);
            } else {
                return matched.line;
            }
        }

        // No match found.
        return null;
    }

    private String snippetize(String line, int matchIndex, int maxLength) {
        // Show up to maxLength characters. But we only show full tokens so show the last full token
        // up to maxLength characters. So as many starting tokens as possible before trying ending
        // tokens.
        int remainingLength = maxLength;
        int tempRemainingLength = remainingLength;

        // Start the end token after the matched query.
        int index = matchIndex;
        int endTokenIndex = index;

        // Find the match token first.
        while (index < line.length()) {
            if (!Character.isLetterOrDigit(line.charAt(index))) {
                endTokenIndex = index;
                remainingLength = tempRemainingLength;
                break;
            }
            tempRemainingLength--;
            index++;
        }

        // Find as much content before the match.
        index = matchIndex - 1;
        tempRemainingLength = remainingLength;
        int startTokenIndex = matchIndex;
        while (index > -1 && tempRemainingLength > 0) {
            if (!Character.isLetterOrDigit(line.charAt(index))) {
                startTokenIndex = index;
                remainingLength = tempRemainingLength;
            }
            tempRemainingLength--;
            index--;
        }

        index = endTokenIndex;
        tempRemainingLength = remainingLength;
        // Find remaining content at after match.
        while (index < line.length() && tempRemainingLength > 0) {
            if (!Character.isLetterOrDigit(line.charAt(index))) {
                endTokenIndex = index;
            }
            tempRemainingLength--;
            index++;
        }
        // Append ellipse if there is content before or after.
        final StringBuilder sb = new StringBuilder();
        if (startTokenIndex > 0) {
            sb.append("...");
        }
        sb.append(line.substring(startTokenIndex, endTokenIndex));
        if (endTokenIndex < line.length()) {
            sb.append("...");
        }
        return sb.toString();
    }

    private static final Pattern SPLIT_PATTERN = Pattern.compile(
            "([\\w-\\.]+)@((?:[\\w]+\\.)+)([a-zA-Z]{2,4})|[\\w]+");

    /**
     * Helper method for splitting a string into tokens.  The lists passed in are populated with
     * the
     * tokens and offsets into the content of each token.  The tokenization function parses e-mail
     * addresses as a single token; otherwise it splits on any non-alphanumeric character.
     *
     * @param content Content to split.
     * @return List of token strings.
     */
    private static List<String> split(String content) {
        final Matcher matcher = SPLIT_PATTERN.matcher(content);
        final ArrayList<String> tokens = Lists.newArrayList();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * Shows data element.
     */
    public void showData(Cursor cursor, int dataColumnIndex) {
        cursor.copyStringToBuffer(dataColumnIndex, mDataBuffer);
        setData(mDataBuffer.data, mDataBuffer.sizeCopied);
    }

    public void setActivatedStateSupported(boolean flag) {
        this.mActivatedStateSupported = flag;
    }

    public void setAdjustSelectionBoundsEnabled(boolean enabled) {
        mAdjustSelectionBoundsEnabled = enabled;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        // We will assume that once measured this will not need to resize
        // itself, so there is no need to pass the layout request to the parent
        // view (ListView).
        forceLayout();
    }

    public void setPhotoPosition(PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
    }

    public PhotoPosition getPhotoPosition() {
        return mPhotoPosition;
    }

    /**
     * Set drawable resources directly for both the background and the drawable resource
     * of the photo view
     *
     * @param backgroundId Id of background resource
     * @param drawableId Id of drawable resource
     */
    public void setDrawableResource(int backgroundId, int drawableId) {
        final ImageView photo = getPhotoView();
        photo.setScaleType(ScaleType.CENTER);
        photo.setBackgroundResource(backgroundId);
        photo.setImageResource(drawableId);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        // If the touch event's coordinates are not within the view's header, then delegate
        // to super.onTouchEvent so that regular view behavior is preserved. Otherwise, consume
        // and ignore the touch event.
        if (mBoundsWithoutHeader.contains((int) x, (int) y) || !pointIsInView(x, y)) {
            return super.onTouchEvent(event);
        } else {
            return true;
        }
    }

    private boolean pointIsInView(float localX, float localY) {
        return localX >= mLeftOffset && localX < mRightOffset
                && localY >= 0 && localY < (getBottom() - getTop());
    }
}
