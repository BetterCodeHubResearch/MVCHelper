package com.shizhefei.mvc.viewhandler;

import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;

import com.shizhefei.mvc.ILoadViewFactory.FootViewAdder;
import com.shizhefei.mvc.ILoadViewFactory.ILoadMoreView;
import com.shizhefei.mvc.MVCHelper.OnScrollBottomListener;
import com.shizhefei.recyclerview.HFAdapter;
import com.shizhefei.recyclerview.HFRecyclerAdapter;

public class RecyclerViewHandler implements ViewHandler {

    @Override
    public boolean handleSetAdapter(View contentView, Object adapter, ILoadMoreView loadMoreView, OnClickListener onClickLoadMoreListener) {
        final RecyclerView recyclerView = (RecyclerView) contentView;
        boolean hasInit = false;
        Adapter<?> adapter2 = (Adapter<?>) adapter;
        if (loadMoreView != null) {
            final HFAdapter hfAdapter;
            if (adapter instanceof HFAdapter) {
                hfAdapter = (HFAdapter) adapter;
            } else {
                hfAdapter = new HFRecyclerAdapter(adapter2,false);
            }
            adapter2 = hfAdapter;
            loadMoreView.init(new RecyclerViewFootViewAdder(recyclerView, hfAdapter), onClickLoadMoreListener);
            hasInit = true;
        }
        recyclerView.setAdapter(adapter2);
        return hasInit;
    }

    @Override
    public void setOnScrollBottomListener(View contentView, OnScrollBottomListener onScrollBottomListener) {
        final RecyclerView recyclerView = (RecyclerView) contentView;
        RecyclerViewOnScrollListener listener = new RecyclerViewOnScrollListener(onScrollBottomListener);
        recyclerView.addOnScrollListener(listener);
        recyclerView.addOnItemTouchListener(listener);
    }

    /**
     * 滑动监听
     */
    private static class RecyclerViewOnScrollListener extends RecyclerView.OnScrollListener implements RecyclerView.OnItemTouchListener {
        private OnScrollBottomListener onScrollBottomListener;

        public RecyclerViewOnScrollListener(OnScrollBottomListener onScrollBottomListener) {
            super();
            this.onScrollBottomListener = onScrollBottomListener;
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (onScrollBottomListener != null) {
                    //(endY < startY) 如果放开的位置比按下去的位置大，说明手势向上移动。（之所以加这个判断是因为，之前列表数据较少的时候它已经在列表底部了，向下的刷新动作也可能触发）
                    //isScollBottom是否滚动到列表底部
                    if ((endY >= 0 && endY < startY) && isScollBottom(recyclerView)) {
                        onScrollBottomListener.onScorllBootom();
                    }
                }
            }
        }

        private boolean isScollBottom(RecyclerView recyclerView) {
            return !isCanScollVertically(recyclerView);
        }

        private boolean isCanScollVertically(RecyclerView recyclerView) {
            if (android.os.Build.VERSION.SDK_INT < 14) {
                return ViewCompat.canScrollVertically(recyclerView, 1) || recyclerView.getScrollY() < recyclerView.getHeight();
            } else {
                return ViewCompat.canScrollVertically(recyclerView, 1);
            }
        }


        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {

        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    endY = -1;
                    startY = e.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    endY = e.getY();
                    break;
            }
            return false;
        }

        private float startY = -1f;
        private float endY = -1f;

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {

        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

        }

    }

    private class RecyclerViewFootViewAdder implements FootViewAdder {
        private RecyclerView recyclerView;
        private HFAdapter hfAdapter;

        public RecyclerViewFootViewAdder(RecyclerView recyclerView, HFAdapter hfAdapter) {
            super();
            this.recyclerView = recyclerView;
            this.hfAdapter = hfAdapter;
        }

        @Override
        public View addFootView(int layoutId) {
            View view = LayoutInflater.from(recyclerView.getContext()).inflate(layoutId, recyclerView, false);
            return addFootView(view);
        }

        @Override
        public View addFootView(View view) {
            hfAdapter.addFooter(view);
            return view;
        }

        @Override
        public View getContentView() {
            return recyclerView;
        }

    }
}
