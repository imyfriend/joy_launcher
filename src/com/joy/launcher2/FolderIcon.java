/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.joy.launcher2;

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.joy.launcher2.R;
import com.joy.launcher2.DropTarget.DragObject;
import com.joy.launcher2.FolderInfo.FolderListener;
import com.joy.launcher2.preference.PreferencesProvider.Size;
import com.joy.launcher2.preference.PreferencesProvider;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends LinearLayout implements FolderListener {
	protected Launcher mLauncher;
	protected Folder mFolder;
    protected FolderInfo mInfo;
    private static boolean sStaticValuesDirty = true;

    private CheckLongPressHelper mLongPressHelper;

    // The number of icons to display in the
    private static int NUM_ITEMS_IN_PREVIEW = 3;
    private static final int CONSUMPTION_ANIMATION_DURATION = 100;
    protected static final int DROP_IN_ANIMATION_DURATION = 400;
    protected static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    // The degree to which the inner ring grows when accepting drop
    private static final float INNER_RING_GROWTH_FACTOR = 0.15f;

    // The degree to which the outer ring is scaled in its natural state
    private static final float OUTER_RING_GROWTH_FACTOR = 0.13f;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.24f;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.35f;

    public static Drawable sSharedFolderLeaveBehind = null;

    protected ImageView mPreviewBackground;
    protected BubbleTextView mFolderName;

    public FolderRingAnimator mFolderRingAnimator = null;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private int mIntrinsicIconSize;
    private float mBaselineIconScale;
    private int mBaselineIconSize;
    private int mAvailableSpaceInPreview;
    private int mTotalWidth = -1;
    private int mPreviewOffsetX;
    private int mPreviewOffsetY;
    private float mMaxPerspectiveShift;
    boolean mAnimating = false;

    private PreviewItemDrawingParams mParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private PreviewItemDrawingParams mAnimParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private ArrayList<ShortcutInfo> mHiddenItems = new ArrayList<ShortcutInfo>();

    //add by huangming for icon size.
    protected static int mPreviewSize = -1;
    //end
    //add by huangming for ios folder.
    public int mFolderMarginTop = 0;
    //end
    private static Canvas sCanvas = new Canvas();
    
    protected DeleteRect mDeleteRect;
    
    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderIcon(Context context) {
        super(context);
        init();
    }

    private void init() {
        mLongPressHelper = new CheckLongPressHelper(this);
        mDeleteRect = new DeleteRect(this);
    }

    static FolderIcon fromXml(int resId, Launcher launcher, ViewGroup group,
            FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        FolderIcon icon = (FolderIcon) LayoutInflater.from(launcher).inflate(resId, group, false);

        icon.mFolderName = (BubbleTextView) icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mPreviewBackground = (ImageView) icon.findViewById(R.id.preview_background);

        //add by huangming for icon size
        Resources res = launcher.getResources();
        if(mPreviewSize <= 0)
        {
            mPreviewSize = (int)launcher.getResources().getDimension(R.dimen.folder_preview_size);
            Size iconSize= PreferencesProvider.Interface.Homescreen.getIconSize(
            		launcher, 
            		res.getString(R.string.config_defaultSize));
            if(iconSize == Size.Small)
            {
            	mPreviewSize = (int)(mPreviewSize * Utilities.SMALL_RATIO);
            }
            else if(iconSize == Size.Large)
            {
            	mPreviewSize = (int)(mPreviewSize * Utilities.LARGE_RATIO);
            }
        }
        int previewSize = mPreviewSize;
        if(LauncherApplication.sTheme == LauncherApplication.THEME_IOS)
    	{
        	icon.mPreviewBackground.setImageResource(R.drawable.joy_folder_icon_bg);
        	NUM_ITEMS_IN_PREVIEW = 9;
    	}
        if(icon.mPreviewBackground.getLayoutParams() instanceof LinearLayout.LayoutParams)
        {
        	LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)icon.mPreviewBackground.getLayoutParams();
        	lp.width = lp.height = previewSize;
        	lp.topMargin = icon.mFolderMarginTop = icon.mFolderName.getPaddingTop();
            lp.bottomMargin = (int)res.getDimension(R.dimen.app_icon_drawable_padding) - icon.mFolderName.getPaddingTop();
        }
        //end
        
        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;
        icon.setContentDescription(String.format(launcher.getString(R.string.folder_name_format),
                folderInfo.title));
        Folder folder = Folder.fromXml(launcher);
        folder.setDragController(launcher.getDragController());
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.mFolder = folder;

        icon.mFolderRingAnimator = new FolderRingAnimator(launcher, icon);
        folderInfo.addListener(icon);

        return icon;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        sStaticValuesDirty = true;
        return super.onSaveInstanceState();
    }

    public static class FolderRingAnimator {
        public int mCellX;
        public int mCellY;
        private Launcher mLauncher;
        private CellLayout mCellLayout;
        public float mOuterRingSize;
        public float mInnerRingSize;
        public FolderIcon mFolderIcon = null;
        public Drawable mOuterRingDrawable = null;
        public Drawable mInnerRingDrawable = null;
        public static Drawable sSharedOuterRingDrawable = null;
        public static Drawable sSharedInnerRingDrawable = null;
        public static int sPreviewSize = -1;
        public static int sPreviewPadding = -1;

        private ValueAnimator mAcceptAnimator;
        private ValueAnimator mNeutralAnimator;

        public FolderRingAnimator(Launcher launcher, FolderIcon folderIcon) {
        	mLauncher = launcher;
            mFolderIcon = folderIcon;
            Resources res = launcher.getResources();
            mOuterRingDrawable = res.getDrawable(R.drawable.portal_ring_outer_holo);
            mInnerRingDrawable = res.getDrawable(R.drawable.portal_ring_inner_holo);

            // We need to reload the static values when configuration changes in case they are
            // different in another configuration
            if (sStaticValuesDirty) {
            	//modify by huangming for icon size
            	//sPreviewSize = res.getDimensionPixelSize(R.dimen.folder_preview_size);
            	sPreviewSize = launcher.getResources().getDimensionPixelSize(R.dimen.folder_preview_size);
                Size iconSize= PreferencesProvider.Interface.Homescreen.getIconSize(
                		launcher, 
                		res.getString(R.string.config_defaultSize));
                if(iconSize == Size.Small)
                {
                	sPreviewSize = (int)(sPreviewSize * Utilities.SMALL_RATIO);
                }
                else if(iconSize == Size.Large)
                {
                	sPreviewSize = (int)(sPreviewSize * Utilities.LARGE_RATIO);
                }
            	//end         
                sPreviewPadding = res.getDimensionPixelSize(R.dimen.folder_preview_padding);
                /*sSharedOuterRingDrawable = res.getDrawable(R.drawable.portal_ring_outer_holo);
                sSharedInnerRingDrawable = res.getDrawable(R.drawable.portal_ring_inner_holo);*/
                //modify by huangming for theme.
            	if(LauncherApplication.sTheme == LauncherApplication.THEME_IOS)
            	{
            		sSharedOuterRingDrawable = res.getDrawable(R.drawable.joy_folder_icon_bg);
                    sSharedInnerRingDrawable = res.getDrawable(R.drawable.portal_ring_inner_holo);
            	}
            	else
            	{
            		sSharedOuterRingDrawable = res.getDrawable(R.drawable.portal_ring_outer_holo);
                    sSharedInnerRingDrawable = res.getDrawable(R.drawable.portal_ring_inner_holo);
            	}
                //end
                sSharedFolderLeaveBehind = res.getDrawable(R.drawable.portal_ring_rest);
                sStaticValuesDirty = false;
            }
        }

        public void animateToAcceptState() {
            if (mNeutralAnimator != null) {
                mNeutralAnimator.cancel();
            }
            mAcceptAnimator = LauncherAnimUtils.ofFloat(0f, 1f);
            mAcceptAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);

            final int previewSize = sPreviewSize;
            mAcceptAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mOuterRingSize = (1 + percent * OUTER_RING_GROWTH_FACTOR) * previewSize;
                    mInnerRingSize = (1 + percent * INNER_RING_GROWTH_FACTOR) * previewSize;
                    if (mCellLayout != null) {
                        mCellLayout.invalidate();
                    }
                }
            });
            mAcceptAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mFolderIcon != null) {
                        mFolderIcon.mPreviewBackground.setVisibility(INVISIBLE);
                    }
                }
            });
            mAcceptAnimator.start();
        }

        public void animateToNaturalState() {
            if (mAcceptAnimator != null) {
                mAcceptAnimator.cancel();
            }
            mNeutralAnimator = LauncherAnimUtils.ofFloat(0f, 1f);
            mNeutralAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);

            final int previewSize = sPreviewSize;
            mNeutralAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mOuterRingSize = (1 + (1 - percent) * OUTER_RING_GROWTH_FACTOR) * previewSize;
                    mInnerRingSize = (1 + (1 - percent) * INNER_RING_GROWTH_FACTOR) * previewSize;
                    if (mCellLayout != null) {
                        mCellLayout.invalidate();
                    }
                }
            });
            mNeutralAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mCellLayout != null) {
                        mCellLayout.hideFolderAccept(FolderRingAnimator.this);
                    }
                    if (mFolderIcon != null) {
                        mFolderIcon.mPreviewBackground.setVisibility(VISIBLE);
                    }
                }
            });
            mNeutralAnimator.start();
        }

        // Location is expressed in window coordinates
        public void getCell(int[] loc) {
            loc[0] = mCellX;
            loc[1] = mCellY;
        }

        // Location is expressed in window coordinates
        public void setCell(int x, int y) {
            mCellX = x;
            mCellY = y;
        }

        public void setCellLayout(CellLayout layout) {
            mCellLayout = layout;
        }

        public float getOuterRingSize() {
            return mOuterRingSize;
        }

        public float getInnerRingSize() {
            return mInnerRingSize;
        }
        
        public int getFolderMarginTop()
        {
        	if(mFolderIcon == null && mLauncher != null)
        	{
        		Resources res = mLauncher.getResources();
        		if(res != null)
        		{
        			return (int)res.getDimension(R.dimen.app_icon_padding_top);
        		}
        	}
        	return mFolderIcon != null?mFolderIcon.mFolderMarginTop:0;
        }
    }

    Folder getFolder() {
        return mFolder;
    }

    FolderInfo getFolderInfo() {
        return mInfo;
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        boolean isJoyFolder = (itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER&&item.natureId!=-1);
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) &&
                !mFolder.isFull() && item != mInfo && !mInfo.opened&&!isJoyFolder);
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        final ItemInfo item = dragInfo;
        return !mFolder.isDestroyed() && willAcceptItem(item);
    }

    public void addItem(ShortcutInfo item) {
        mInfo.add(item);
    }

    public void onDragEnter(Object dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem((ItemInfo) dragInfo)) return;
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
        CellLayout layout = (CellLayout) getParent().getParent();
        mFolderRingAnimator.setCell(lp.cellX, lp.cellY);
        mFolderRingAnimator.setCellLayout(layout);
        mFolderRingAnimator.animateToAcceptState();
        layout.showFolderAccept(mFolderRingAnimator);
    }

    public void performCreateAnimation(final ShortcutInfo destInfo, final View destView,
            final ShortcutInfo srcInfo, final DragView srcView, Rect dstRect,
            float scaleRelativeToDragLayer, Runnable postAnimationRunnable) {

        // These correspond two the drawable and view that the icon was dropped _onto_
        Drawable animateDrawable = ((TextView) destView).getCompoundDrawables()[1];
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth());

        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        animateFirstItem(animateDrawable, INITIAL_ITEM_ANIMATION_DURATION, false, null);
        addItem(destInfo);

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, srcView, dstRect, scaleRelativeToDragLayer, 1, postAnimationRunnable);
    }

    public void performDestroyAnimation(final View finalView, Runnable onCompleteRunnable) {
        Drawable animateDrawable = ((TextView) finalView).getCompoundDrawables()[1];
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(), 
                finalView.getMeasuredWidth());

        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        animateFirstItem(animateDrawable, FINAL_ITEM_ANIMATION_DURATION, true,
                onCompleteRunnable);
    }

    public void onDragExit() {
        mFolderRingAnimator.animateToNaturalState();
    }

    private void onDrop(final ShortcutInfo item, DragView animateView, Rect finalRect,
            float scaleRelativeToDragLayer, int index, Runnable postAnimationRunnable) {
        item.cellX = -1;
        item.cellY = -1;

        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            Rect from = new Rect();
            dragLayer.getViewRectRelativeToSelf(animateView, from);
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                Workspace workspace = mLauncher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform((CellLayout) getParent().getParent());
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform((CellLayout) getParent().getParent());
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, center);
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < NUM_ITEMS_IN_PREVIEW ? 0.5f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;
            dragLayer.animateView(animateView, from, to, finalAlpha,
                    1, 1, finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    new DecelerateInterpolator(2), new AccelerateInterpolator(2),
                    postAnimationRunnable, DragLayer.ANIMATION_END_DISAPPEAR, null);
            addItem(item);
            mHiddenItems.add(item);
            postDelayed(new Runnable() {
                public void run() {
                    mHiddenItems.remove(item);
                    invalidate();
                }
            }, DROP_IN_ANIMATION_DURATION);
        } else {
            addItem(item);
        }
    }

    public void onDrop(DragObject d) {
        ShortcutInfo item;
        if (d.dragInfo instanceof ApplicationInfo) {
            // Came from all apps -- make a copy
            item = ((ApplicationInfo) d.dragInfo).makeShortcut();
        } else if (d.dragInfo instanceof FolderInfo) {
            FolderInfo folder = (FolderInfo) d.dragInfo;
            mFolder.notifyDrop();
            for (ShortcutInfo fItem : folder.contents) {
                onDrop(fItem, d.dragView, null, 1.0f, mInfo.contents.size(), d.postAnimationRunnable);
            }
            mLauncher.removeFolder(folder);
            LauncherModel.deleteItemFromDatabase(mLauncher, folder);
            return;
        } else {
            item = (ShortcutInfo) d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d.dragView, null, 1.0f, mInfo.contents.size(), d.postAnimationRunnable);
    }

    private void computePreviewDrawingParams(int drawableSize, int totalSize) {
        if (mIntrinsicIconSize != drawableSize || mTotalWidth != totalSize) {
            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalSize;

            final int previewSize = FolderRingAnimator.sPreviewSize;
            final int previewPadding = FolderRingAnimator.sPreviewPadding;

            //modify by huangming for theme
            if(LauncherApplication.sTheme == LauncherApplication.THEME_IOS)
            {
            	mAvailableSpaceInPreview = (mIntrinsicIconSize - 2 * previewPadding);
            }
            else
            {
            	mAvailableSpaceInPreview = (previewSize - 2 * previewPadding);
            }
            
            //end
            // cos(45) = 0.707  + ~= 0.1) = 0.8f
            int adjustedAvailableSpace = (int) ((mAvailableSpaceInPreview / 2) * (1 + 0.8f));

            int unscaledHeight = (int) (mIntrinsicIconSize * (1 + PERSPECTIVE_SHIFT_FACTOR));
            mBaselineIconScale = (1.0f * adjustedAvailableSpace / unscaledHeight);

            mBaselineIconSize = (int) (mIntrinsicIconSize * mBaselineIconScale);
            mMaxPerspectiveShift = mBaselineIconSize * PERSPECTIVE_SHIFT_FACTOR;

            mPreviewOffsetX = (mTotalWidth - mAvailableSpaceInPreview) / 2;
            //modify by huangming for theme
            mPreviewOffsetY = previewPadding + mFolderMarginTop;
            //end
        }
    }

    protected void computePreviewDrawingParams(Drawable d) {
        computePreviewDrawingParams(d.getIntrinsicWidth(), getMeasuredWidth());
    }

    class PreviewItemDrawingParams {
        PreviewItemDrawingParams(float transX, float transY, float scale, int overlayAlpha) {
            this.transX = transX;
            this.transY = transY;
            this.scale = scale;
            this.overlayAlpha = overlayAlpha;
        }
        float transX;
        float transY;
        float scale;
        int overlayAlpha;
        Drawable drawable;
    }

    private float getLocalCenterForIndex(int index, int[] center) {
        mParams = computePreviewItemDrawingParams(Math.min(NUM_ITEMS_IN_PREVIEW, index), mParams);

        mParams.transX += mPreviewOffsetX;
        mParams.transY += mPreviewOffsetY;
        float offsetX = mParams.transX + (mParams.scale * mIntrinsicIconSize) / 2;
        float offsetY = mParams.transY + (mParams.scale * mIntrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mParams.scale;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParams(int index,
            PreviewItemDrawingParams params) {
        index = NUM_ITEMS_IN_PREVIEW - index - 1;
        float r = (index * 1.0f) / (NUM_ITEMS_IN_PREVIEW - 1);
        float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));

        float offset = (1 - r) * mMaxPerspectiveShift;
        float scaledSize = scale * mBaselineIconSize;
        float scaleOffsetCorrection = (1 - scale) * mBaselineIconSize;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
        float transY = mAvailableSpaceInPreview - (offset + scaledSize + scaleOffsetCorrection);
        float transX = offset + scaleOffsetCorrection;
        float totalScale = mBaselineIconScale * scale;
        final int overlayAlpha = (int) (80 * (1 - r));
        //add by huangming for theme
        if(LauncherApplication.sTheme == LauncherApplication.THEME_IOS)
    	{
        	int previewPadding = FolderRingAnimator.sPreviewPadding / 2;
            int itemSpace = (mAvailableSpaceInPreview - 2 * previewPadding) / 3;
            totalScale = (mAvailableSpaceInPreview - 2 * previewPadding) / (float)(mIntrinsicIconSize * 3) ;
            //float mItemScale = 0.25f;
    		switch (index) {
    		case 0:
                transX = 2 * previewPadding + 2 * itemSpace;
                transY = 2 * previewPadding + 2 * itemSpace;
    			break;
    		case 1:
    			transX = previewPadding + itemSpace;;
                transY = 2 * previewPadding + 2 * itemSpace;;
    			break;
    		case 2:
    			transX = 0;
                transY = 2 * previewPadding + 2 * itemSpace;
    			break;
    		case 3:
    			transX = 2 * previewPadding + 2 * itemSpace;
                transY = previewPadding + itemSpace;
    			break;
    		case 4:
    			transX = previewPadding + itemSpace;
                transY = previewPadding + itemSpace;
    			break;
    		case 5:
    			transX = 0;
                transY = previewPadding + itemSpace;
    			break;
    		case 6:
    			transX = 2 * previewPadding + 2 * itemSpace;
                transY = 0;
    			break;
    		case 7:
    			transX = previewPadding + itemSpace;
                transY = 0;
    			break;
    		case 8:
    			transX = 0;
                transY = 0;
    			break;
    		}
    	
    	}
        //end

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = totalScale;
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params) {
        canvas.save();
        canvas.translate(params.transX + mPreviewOffsetX, params.transY + mPreviewOffsetY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            d.setBounds(0, 0, mIntrinsicIconSize, mIntrinsicIconSize);
            d.setFilterBitmap(true);
            d.setColorFilter(Color.argb(params.overlayAlpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
            d.draw(canvas);
            d.clearColorFilter();
            d.setFilterBitmap(false);
        }
        canvas.restore();
    }

    protected void dispatchDrawSuper(Canvas canvas){
    	 super.dispatchDraw(canvas);
    }
    @Override
    protected void dispatchDraw(Canvas canvas) {
    	dispatchDrawSuper(canvas);

        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0 && !mAnimating) return;

        ArrayList<View> items = mFolder.getItemsInReadingOrder(false);
        Drawable d;
        TextView v;

        // Update our drawing parameters if necessary
        if (mAnimating) {
            computePreviewDrawingParams(mAnimParams.drawable);
        } else {
            v = (TextView) items.get(0);
            d = v.getCompoundDrawables()[1];
            computePreviewDrawingParams(d);
        }

        int nItemsInPreview = Math.min(items.size(), NUM_ITEMS_IN_PREVIEW);
        if (!mAnimating) {
            for (int i = nItemsInPreview - 1; i >= 0; i--) {
                v = (TextView) items.get(i);
                if (!mHiddenItems.contains(v.getTag())) {
                    d = v.getCompoundDrawables()[1];
                    mParams = computePreviewItemDrawingParams(i, mParams);
                    mParams.drawable = d;
                    drawPreviewItem(canvas, mParams);
                }
            }
        } else {
            drawPreviewItem(canvas, mAnimParams);
        }
        
        if(mDeleteRect != null){
    		mDeleteRect.drawDelete(canvas, mScrollX, mScrollY);
    	}
        
        //add by huangming for ios adaptation.
        if(LauncherApplication.sTheme == LauncherApplication.THEME_IOS)
        {
			boolean isOnHotseat = Hotseat.isViewOnHotseat(this);
		    
		    if(isOnHotseat)
		    {
		    	drawIos(canvas, getResources().getDrawable(R.drawable.joy_folder_icon_bg));
		    }
        }
        //end
    }
    
    //add by huangming for ios adaptation.
	protected void drawIos(Canvas canvas, Drawable originalDrawable)
    {
    	if(originalDrawable == null)return;
    	int width = getWidth();
    	int height = getHeight();
    	int pWidth = mPreviewBackground.getWidth();
    	int pHeight = mPreviewBackground.getHeight();
    	if(width <= 0 || height <= 0 || pWidth <= 0 || pHeight <= 0)return;
    	int lastHeight = Math.min(pHeight, height - mPreviewBackground.getBottom());
    	if(lastHeight <= 0)return;
    	canvas.save();
    	Bitmap originalImage = getOriginalImage(
    			originalDrawable, 
    			pWidth, 
    			pHeight, 
    			lastHeight);
    	Bitmap reflectionImage = Hotseat.createReflectedImage(originalImage);
    	
    	canvas.translate(mPreviewBackground.getLeft(), mPreviewBackground.getBottom());
    	canvas.drawBitmap(reflectionImage, 0, 0, null);
    	
    	canvas.restore();
    }
	
	protected Bitmap getOriginalImage(Drawable originalDrawable, int width, int height, int lastHeight)
	{
		Bitmap bm = Bitmap.createBitmap(width, height, Config.ARGB_8888);
		final Canvas canvas = sCanvas;
		canvas.setBitmap(bm);
		Rect oldRect = originalDrawable.getBounds();
		originalDrawable.setBounds(0, 0, width, height);
		originalDrawable.draw(canvas);
		originalDrawable.setBounds(oldRect);
		ArrayList<View> items = mFolder.getItemsInReadingOrder(false);
		int nItemsInPreview = Math.min(items.size(), NUM_ITEMS_IN_PREVIEW);
		
		int previewPadding = FolderRingAnimator.sPreviewPadding / 2;
        float scale = (mAvailableSpaceInPreview - 2 * previewPadding) / (float)(mIntrinsicIconSize * 3) ;
		int sWidth =  (int)(scale * width);
		int sHeight = (int)(scale * height);
		int wGap = (width - 3 * sWidth) / 4;
		int hGap = (height - 3 * sHeight) / 4;
		for (int i = nItemsInPreview - 1; i >= 0; i--) {
			int h = i % 3;
		    int v = i / 3;
			TextView tv = (TextView) items.get(i);
			Drawable d = tv.getCompoundDrawables()[1];
			canvas.save();
	        canvas.translate(wGap + (wGap + sWidth) * h, hGap + (hGap + sHeight) * v);
	        canvas.scale(scale, scale);

	        if (d != null) {
	            d.setBounds(0, 0, mIntrinsicIconSize, mIntrinsicIconSize);
	            d.draw(canvas);
	        }
	        canvas.restore();
		}
		canvas.setBitmap(null);
		/*Rect oldRect = d.getBounds();
		d.setBounds(0, 0, width, height);
		canvas.setBitmap(bm);
		d.draw(canvas);
		d.setBounds(oldRect);
		canvas.setBitmap(null);*/
		if(lastHeight < height)
		{
			bm = Bitmap.createBitmap(bm, 0, height - lastHeight, width, lastHeight);
		}
		return bm;
	}
	//end

    private void animateFirstItem(final Drawable d, int duration, final boolean reverse,
            final Runnable onCompleteRunnable) {
        final PreviewItemDrawingParams finalParams = computePreviewItemDrawingParams(0, null);

        final float scale0 = 1.0f;
        final float transX0 = (mAvailableSpaceInPreview - d.getIntrinsicWidth()) / 2;
        final float transY0 = (mAvailableSpaceInPreview - d.getIntrinsicHeight()) / 2;
        mAnimParams.drawable = d;

        ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1.0f);
        va.addUpdateListener(new AnimatorUpdateListener(){
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = (Float) animation.getAnimatedValue();
                if (reverse) {
                    progress = 1 - progress;
                    mPreviewBackground.setAlpha(progress);
                }

                mAnimParams.transX = transX0 + progress * (finalParams.transX - transX0);
                mAnimParams.transY = transY0 + progress * (finalParams.transY - transY0);
                mAnimParams.scale = scale0 + progress * (finalParams.scale - scale0);
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mAnimating = true;
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimating = false;
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
            }
        });
        va.setDuration(duration);
        va.start();
    }

    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(INVISIBLE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    public int getPreviewBackgroundLeft(){
    	return mPreviewBackground.getLeft();
    }
    public int getPreviewBackgroundTop(){
    	return mPreviewBackground.getTop();
    }
    public void onItemsChanged() {
        invalidate();
        requestLayout();
    }

    public void onAdd(ShortcutInfo item) {
        invalidate();
        requestLayout();
    }

    public void onRemove(ShortcutInfo item) {
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title.toString());
        setContentDescription(String.format(getContext().getString(R.string.folder_name_format),
                title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        boolean result = super.onTouchEvent(event);

        if(mDeleteRect != null){
        	return mDeleteRect.onTouchEventDelete(result ,event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLongPressHelper.postCheckForLongPress();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mLongPressHelper.cancelLongPress();
                break;
        }
        return result;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        mLongPressHelper.cancelLongPress();
    }
}
