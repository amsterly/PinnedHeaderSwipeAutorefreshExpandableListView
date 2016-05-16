/**
The MIT License (MIT)

Copyright (c) 2014 singwhatiwanna
https://github.com/singwhatiwanna
http://blog.csdn.net/singwhatiwanna

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package common.ali.com.pinnedheaderswipeexpandablelistview.expandable.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ExpandableListView;
import android.widget.AbsListView.OnScrollListener;

public class PinnedHeaderExpandableListView extends ExpandableListView implements OnScrollListener {
    private static final String TAG = "PinnedHeaderExpandableListView";
    private static final boolean DEBUG = true;

    public interface OnHeaderUpdateListener {
        /**
         * 返回一个view对象即可
         * 注意：view必须要有LayoutParams
         */
        public View getPinnedHeader();

        public void updatePinnedHeader(View headerView, int firstVisibleGroupPos);
    }

    private View mHeaderView;
    private int mHeaderWidth;
    private int mHeaderHeight;

    private View mTouchTarget;

    private OnScrollListener mScrollListener;
    private OnHeaderUpdateListener mHeaderUpdateListener;

    private boolean mActionDownHappened = false;
    protected boolean mIsHeaderGroupClickable = true;

    //aly整合swipelistview变量如下：
    //
    // --
    //
    private Boolean mIsHorizontal;

    private View mPreItemView;

    private View mCurrentItemView;

    private float mFirstX;

    private float mFirstY;

    private int mRightViewWidth = 300;

    // private boolean mIsInAnimation = false;
    private final int mDuration = 100;

    private final int mDurationStep = 10;

    private boolean mIsShown;

    /**
     * 是否允许footer or Header Swipe
     */
    private boolean mIsFooterCanSwipe = false;

    private boolean mIsHeaderCanSwipe = false;


    public PinnedHeaderExpandableListView(Context context) {
        super(context);
        initView();
    }

    public PinnedHeaderExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PinnedHeaderExpandableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        setFadingEdgeLength(0);
        setOnScrollListener(this);
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        if (l != this) {
            mScrollListener = l;
        } else {
            mScrollListener = null;
        }
        super.setOnScrollListener(this);
    }

    /**
     * 给group添加点击事件监听
     * @param onGroupClickListener 监听
     * @param isHeaderGroupClickable 表示header是否可点击<br/>
     * note : 当不想group可点击的时候，需要在OnGroupClickListener#onGroupClick中返回true，
     * 并将isHeaderGroupClickable设为false即可
     */
    public void setOnGroupClickListener(OnGroupClickListener onGroupClickListener, boolean isHeaderGroupClickable) {
        mIsHeaderGroupClickable = isHeaderGroupClickable;
        super.setOnGroupClickListener(onGroupClickListener);
    }

    public void setOnHeaderUpdateListener(OnHeaderUpdateListener listener) {
        mHeaderUpdateListener = listener;
        if (listener == null) {
            mHeaderView = null;
            mHeaderWidth = mHeaderHeight = 0;
            return;
        }
        mHeaderView = listener.getPinnedHeader();
        int firstVisiblePos = getFirstVisiblePosition();
        int firstVisibleGroupPos = getPackedPositionGroup(getExpandableListPosition(firstVisiblePos));
        listener.updatePinnedHeader(mHeaderView, firstVisibleGroupPos);
        requestLayout();
        postInvalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mHeaderView == null) {
            return;
        }
        measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);
        mHeaderWidth = mHeaderView.getMeasuredWidth();
        mHeaderHeight = mHeaderView.getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mHeaderView == null) {
            return;
        }
        int delta = mHeaderView.getTop();
        mHeaderView.layout(0, delta, mHeaderWidth, mHeaderHeight + delta);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mHeaderView != null) {
            drawChild(canvas, mHeaderView, getDrawingTime());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        int pos = pointToPosition(x, y);
        if (mHeaderView != null && y >= mHeaderView.getTop() && y <= mHeaderView.getBottom()) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                mTouchTarget = getTouchTarget(mHeaderView, x, y);
                mActionDownHappened = true;
            } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                View touchTarget = getTouchTarget(mHeaderView, x, y);
                if (touchTarget == mTouchTarget && mTouchTarget.isClickable()) {
                    mTouchTarget.performClick();
                    invalidate(new Rect(0, 0, mHeaderWidth, mHeaderHeight));
                } else if (mIsHeaderGroupClickable){
                    int groupPosition = getPackedPositionGroup(getExpandableListPosition(pos));
                    if (groupPosition != INVALID_POSITION && mActionDownHappened) {
                        if (isGroupExpanded(groupPosition)) {
                            collapseGroup(groupPosition);
                        } else {
                            expandGroup(groupPosition);
                        }
                    }
                }
                mActionDownHappened = false;
            }
            return true;
        }

        return super.dispatchTouchEvent(ev);
    }

    private View getTouchTarget(View view, int x, int y) {
        if (!(view instanceof ViewGroup)) {
            return view;
        }

        ViewGroup parent = (ViewGroup)view;
        int childrenCount = parent.getChildCount();
        final boolean customOrder = isChildrenDrawingOrderEnabled();
        View target = null;
        for (int i = childrenCount - 1; i >= 0; i--) {
            final int childIndex = customOrder ? getChildDrawingOrder(childrenCount, i) : i;
            final View child = parent.getChildAt(childIndex);
            if (isTouchPointInView(child, x, y)) {
                target = child;
                break;
            }
        }
        if (target == null) {
            target = parent;
        }

        return target;
    }

    private boolean isTouchPointInView(View view, int x, int y) {
        if (view.isClickable() && y >= view.getTop() && y <= view.getBottom()
                && x >= view.getLeft() && x <= view.getRight()) {
            return true;
        }
        return false;
    }

    public void requestRefreshHeader() {
        refreshHeader();
        invalidate(new Rect(0, 0, mHeaderWidth, mHeaderHeight));
    }

    protected void refreshHeader() {
        if (mHeaderView == null) {
            return;
        }
        int firstVisiblePos = getFirstVisiblePosition();
        int pos = firstVisiblePos + 1;
        int firstVisibleGroupPos = getPackedPositionGroup(getExpandableListPosition(firstVisiblePos));
        int group = getPackedPositionGroup(getExpandableListPosition(pos));
        if (DEBUG) {
            Log.d(TAG, "refreshHeader firstVisibleGroupPos=" + firstVisibleGroupPos);
        }

        if (group == firstVisibleGroupPos + 1) {
            View view = getChildAt(1);
            if (view == null) {
                Log.w(TAG, "Warning : refreshHeader getChildAt(1)=null");
                return;
            }
            if (view.getTop() <= mHeaderHeight) {
                int delta = mHeaderHeight - view.getTop();
                mHeaderView.layout(0, -delta, mHeaderWidth, mHeaderHeight - delta);
            } else {
                //TODO : note it, when cause bug, remove it
                mHeaderView.layout(0, 0, mHeaderWidth, mHeaderHeight);
            }
        } else {
            mHeaderView.layout(0, 0, mHeaderWidth, mHeaderHeight);
        }

        if (mHeaderUpdateListener != null) {
            mHeaderUpdateListener.updatePinnedHeader(mHeaderView, firstVisibleGroupPos);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mScrollListener != null) {
            mScrollListener.onScrollStateChanged(view, scrollState);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        if (totalItemCount > 0) {
            refreshHeader();
        }
        if (mScrollListener != null) {
            mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }
    //aly整合swipelistview方法如下：
    //
    // /--------
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float lastX = ev.getX();
        float lastY = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsHorizontal = null;
                mFirstX = lastX;
                mFirstY = lastY;
                int motionPosition = pointToPosition((int)mFirstX, (int)mFirstY);

                if (motionPosition >= 0) {
                    View currentItemView = getChildAt(motionPosition - getFirstVisiblePosition());
                    mPreItemView = mCurrentItemView;
                    mCurrentItemView = currentItemView;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = lastX - mFirstX;
                float dy = lastY - mFirstY;

                if (Math.abs(dx) >= 5 && Math.abs(dy) >= 5) {
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsShown && (mPreItemView != mCurrentItemView || isHitCurItemLeft(lastX))) {
                    System.out.println("1---> hiddenRight");
                    /**
                     * 情况一：
                     * <p>
                     * 一个Item的右边布局已经显示，
                     * <p>
                     * 这时候点击任意一个item, 那么那个右边布局显示的item隐藏其右边布局
                     */
                    hiddenRight(mPreItemView);
                }
                break;
        }

        return super.onInterceptTouchEvent(ev);
    }
    /**
     * return false, can't move any direction. return true, cant't move
     * vertical, can move horizontal. return super.onTouchEvent(ev), can move
     * both.
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float lastX = ev.getX();
        float lastY = ev.getY();
        // test footer and header
        if (!judgeFooterView(mFirstX, mFirstY) || !judgeHeaderView(mFirstX, mFirstY)) {
            return super.onTouchEvent(ev);
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = lastX - mFirstX;
                float dy = lastY - mFirstY;
                // confirm is scroll direction
                if (mIsHorizontal == null) {
                    if (!judgeScrollDirection(dx, dy)) {
                        break;
                    }
                }

                if (mIsHorizontal) {
                    if (mIsShown && mPreItemView != mCurrentItemView) {
                        /**
                         * 情况二：
                         * <p>
                         * 一个Item的右边布局已经显示，
                         * <p>
                         * 这时候左右滑动另外一个item,那个右边布局显示的item隐藏其右边布局
                         * <p>
                         * 向左滑动只触发该情况，向右滑动还会触发情况五
                         */
                        hiddenRight(mPreItemView);
                    }

                    if (mIsShown && mPreItemView == mCurrentItemView) {
                        dx = dx - mRightViewWidth;
                    }

                    // can't move beyond boundary
                    if (dx < 0 && dx > -mRightViewWidth) {
                        mCurrentItemView.scrollTo((int)(-dx), 0);
                    }

                    return true;
                } else {
                    if (mIsShown) {
                        /**
                         * 情况三：
                         * <p>
                         * 一个Item的右边布局已经显示，
                         * <p>
                         * 这时候上下滚动ListView,那么那个右边布局显示的item隐藏其右边布局
                         */
                        hiddenRight(mPreItemView);
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                clearPressedState();
                if (mIsShown) {
                    /**
                     * 情况四：
                     * <p>
                     * 一个Item的右边布局已经显示，
                     * <p>
                     * 这时候左右滑动当前一个item,那个右边布局显示的item隐藏其右边布局
                     */
                    hiddenRight(mPreItemView);
                }

                if (mIsHorizontal != null && mIsHorizontal) {
                    if (mFirstX - lastX > mRightViewWidth / 2) {
                        showRight(mCurrentItemView);
                    } else {
                        /**
                         * 情况五：
                         * <p>
                         * 向右滑动一个item,且滑动的距离超过了右边View的宽度的一半，隐藏之。
                         */
                        hiddenRight(mCurrentItemView);
                    }
                    MotionEvent obtain = MotionEvent.obtain(ev);
                    obtain.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(obtain);
                    return true;
                }

                break;
        }

        return super.onTouchEvent(ev);
    }
    private boolean isHitCurItemLeft(float x) {
        return x < getWidth() - mRightViewWidth;
    }

    /**
     * @param dx
     * @param dy
     * @return judge if can judge scroll direction
     */
    private boolean judgeScrollDirection(float dx, float dy) {
        boolean canJudge = true;

        if (Math.abs(dx) > 30 && Math.abs(dx) > 2 * Math.abs(dy)) {
            mIsHorizontal = true;
        } else if (Math.abs(dy) > 30 && Math.abs(dy) > 2 * Math.abs(dx)) {
            mIsHorizontal = false;
        } else {
            canJudge = false;
        }

        return canJudge;
    }

    /**
     * @param posX
     * @param posY
     * @return judge if can footer judge
     */
    private boolean judgeFooterView(float posX, float posY) {
        // if footer can swipe
        if (mIsFooterCanSwipe) {
            return true;
        }
        // footer cannot swipe
        int selectPos = pointToPosition((int)posX, (int)posY);
        if (selectPos >= (getCount() - getFooterViewsCount())) {
            // is footer ,can not swipe
            return false;
        }
        // not footer can swipe
        return true;
    }

    /**
     * @param posX
     * @param posY
     * @return judge if can judge scroll direction
     */
    private boolean judgeHeaderView(float posX, float posY) {
        // if header can swipe
        if (mIsHeaderCanSwipe) {
            return true;
        }
        // header cannot swipe
        int selectPos = pointToPosition((int) posX, (int) posY);
        if (selectPos >= 0 && selectPos < getHeaderViewsCount()) {
            // is header ,can not swipe
            return false;
        }
        // not header can swipe
        return true;
    }
    private void clearPressedState() {
        // TODO current item is still has background, issue
        mCurrentItemView.setPressed(false);
        setPressed(false);
        refreshDrawableState();
        // invalidate();
    }

    private void showRight(View view) {
        Message msg = new MoveHandler().obtainMessage();
        msg.obj = view;
        msg.arg1 = view.getScrollX();
        msg.arg2 = mRightViewWidth;
        msg.sendToTarget();

        mIsShown = true;
    }

    private void hiddenRight(View view) {
        if (mCurrentItemView == null) {
            return;
        }
        Message msg = new MoveHandler().obtainMessage();//
        msg.obj = view;
        msg.arg1 = view.getScrollX();
        msg.arg2 = 0;

        msg.sendToTarget();

        mIsShown = false;
    }

    /**
     * show or hide right layout animation
     */
    @SuppressLint("HandlerLeak")
    class MoveHandler extends Handler {
        int stepX = 0;

        int fromX;

        int toX;

        View view;

        private boolean mIsInAnimation = false;

        private void animatioOver() {
            mIsInAnimation = false;
            stepX = 0;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (stepX == 0) {
                if (mIsInAnimation) {
                    return;
                }
                mIsInAnimation = true;
                view = (View)msg.obj;
                fromX = msg.arg1;
                toX = msg.arg2;
                stepX = (int)((toX - fromX) * mDurationStep * 1.0 / mDuration);
                if (stepX < 0 && stepX > -1) {
                    stepX = -1;
                } else if (stepX > 0 && stepX < 1) {
                    stepX = 1;
                }
                if (Math.abs(toX - fromX) < 10) {
                    view.scrollTo(toX, 0);
                    animatioOver();
                    return;
                }
            }

            fromX += stepX;
            boolean isLastStep = (stepX > 0 && fromX > toX) || (stepX < 0 && fromX < toX);
            if (isLastStep) {
                fromX = toX;
            }

            view.scrollTo(fromX, 0);
            invalidate();

            if (!isLastStep) {
                this.sendEmptyMessageDelayed(0, mDurationStep);
            } else {
                animatioOver();
            }
        }
    }

    public int getRightViewWidth() {
        return mRightViewWidth;
    }

    public void setRightViewWidth(int mRightViewWidth) {
        this.mRightViewWidth = mRightViewWidth;
    }

    /**
     * 设置list的脚是否能够swipe
     *
     * @param canSwipe
     */
    public void setFooterViewCanSwipe(boolean canSwipe) {
        mIsFooterCanSwipe = canSwipe;
    }

    /**
     * 设置list的头是否能够swipe
     *
     * @param canSwipe
     */
    public void setHeaderViewCanSwipe(boolean canSwipe) {
        mIsHeaderCanSwipe = canSwipe;
    }

    /**
     * 设置 footer and header can swipe
     *
     * @param footer
     * @param header
     */
    public void setFooterAndHeaderCanSwipe(boolean footer, boolean header) {
        mIsHeaderCanSwipe = header;
        mIsFooterCanSwipe = footer;
    }

}