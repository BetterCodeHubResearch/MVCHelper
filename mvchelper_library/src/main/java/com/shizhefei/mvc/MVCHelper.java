/*
Copyright 2015 shizhefei（LuckyJayce）

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.shizhefei.mvc;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListView;

import com.shizhefei.mvc.ILoadViewFactory.ILoadMoreView;
import com.shizhefei.mvc.ILoadViewFactory.ILoadView;
import com.shizhefei.mvc.IRefreshView.OnRefreshListener;
import com.shizhefei.mvc.imp.DefaultLoadViewFactory;
import com.shizhefei.mvc.viewhandler.ListViewHandler;
import com.shizhefei.mvc.viewhandler.RecyclerViewHandler;
import com.shizhefei.mvc.viewhandler.ViewHandler;
import com.shizhefei.task.IAsyncTask;
import com.shizhefei.task.ITask;
import com.shizhefei.utils.NetworkUtils;

/**
 * <h1>下拉刷新，上滑加载更多的控件的辅助类</h1><br>
 * <br>
 * 刷新，加载更多规则<br>
 * 当用户下拉刷新时，会取消掉当前的刷新，以及加载更多的任务<br>
 * 当用户加载更多的时候，如果有已经正在刷新或加载更多是不会再执行加载更多的操作。<br>
 * <br>
 * 注意:记得在Activity的Ondestroy方法调用destory <br>
 * 要添加 android.permission.ACCESS_NETWORK_STATE 权限，这个用来检测是否有网络
 *
 * @param <DATA>
 * @author LuckyJayce
 */
public class MVCHelper<DATA> {
    private IDataAdapter<DATA> dataAdapter;
    private IRefreshView refreshView;
    private IDataSource<DATA> dataSource;
    private View contentView;
    private Context context;
    private MOnStateChangeListener<DATA> onStateChangeListener = new MOnStateChangeListener<DATA>();
    private MyAsyncTask<Void, Void, DATA> asyncTask;
    private RequestHandle cancle;
    private long loadDataTime = -1;
    /**
     * 是否还有更多数据。如果服务器返回的数据为空的话，就说明没有更多数据了，也就没必要自动加载更多数据
     */
    private boolean hasMoreData = true;
    /***
     * 加载更多的时候是否事先检查网络是否可用。
     */
    private boolean needCheckNetwork = true;
    private ILoadView mLoadView;
    private ILoadMoreView mLoadMoreView;
    public static ILoadViewFactory loadViewFactory = new DefaultLoadViewFactory();

    private ListViewHandler listViewHandler = new ListViewHandler();

    private RecyclerViewHandler recyclerViewHandler = new RecyclerViewHandler();
    private IAsyncDataSource<DATA> asyncDataSource;
    private Handler handler;

    public MVCHelper(IRefreshView refreshView) {
        this(refreshView, loadViewFactory.madeLoadView(), loadViewFactory.madeLoadMoreView());
    }

    public MVCHelper(IRefreshView refreshView, ILoadView loadView) {
        this(refreshView, loadView, null);
    }

    public MVCHelper(IRefreshView refreshView, ILoadView loadView, ILoadMoreView loadMoreView) {
        super();
        this.context = refreshView.getContentView().getContext().getApplicationContext();
        this.autoLoadMore = true;
        this.refreshView = refreshView;
        contentView = refreshView.getContentView();
        contentView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        refreshView.setOnRefreshListener(onRefreshListener);
        mLoadView = loadView;
        mLoadMoreView = loadMoreView;
        mLoadView.init(refreshView.getSwitchView(), onClickRefresListener);
        handler = new Handler();
    }

    /**
     * 设置LoadView的factory，用于创建使用者自定义的加载失败，加载中，加载更多等布局
     *
     * @param fractory
     */
    public static void setLoadViewFractory(ILoadViewFactory fractory) {
        loadViewFactory = fractory;
    }

    /**
     * 如果不是网络请求的业务可以把这个设置为false
     *
     * @param needCheckNetwork
     */
    public void setNeedCheckNetwork(boolean needCheckNetwork) {
        this.needCheckNetwork = needCheckNetwork;
    }

    /**
     * 设置数据源，用于加载数据
     *
     * @param dataSource
     */
    public void setDataSource(IDataSource<DATA> dataSource) {
        this.asyncDataSource = null;
        this.dataSource = dataSource;
    }

    /**
     * 设置数据源，用于加载数据
     *
     * @param asyncDataSource
     */
    public void setDataSource(IAsyncDataSource<DATA> asyncDataSource) {
        this.dataSource = null;
        this.asyncDataSource = asyncDataSource;
    }

    /**
     * 设置数据源，用于加载数据
     *
     * @param task
     */
    public void setDataSource(IAsyncTask<DATA> task) {
        this.dataSource = null;
        this.asyncDataSource = new ProxyAsyncDataSource<>(task);
    }

    /**
     * 设置数据源，用于加载数据
     *
     * @param task
     */
    public void setDataSource(ITask<DATA> task) {
        this.dataSource = new ProxySyncDataSource<>(task);
        this.asyncDataSource = null;
    }

    /**
     * 设置适配器，用于显示数据
     *
     * @param adapter 实现IDataAdapter，如果ContentView是ListView那么viewAdapter就要继承于ListAdapter，如果是RecyclerView，那么viewAdapter就要继承于RecyclerView.Adapter
     */
    public void setAdapter(IDataAdapter<DATA> adapter) {
        setAdapter2(adapter, adapter);
    }

    /**
     * 设置适配器，用于显示数据
     *
     * @param adapter  实现IDataAdapter，如果ContentView是ListView那么viewAdapter就要继承于ListAdapter，如果是RecyclerView，那么viewAdapter就要继承于RecyclerView.Adapter
     * @param viewHandler 用于设置view的适配器,用于添加加载更多的FootView,滑动事件触发加载更多
     */
    public void setAdapter(IDataAdapter<DATA> adapter, ViewHandler viewHandler) {
        setAdapter2(adapter, adapter, viewHandler);
    }

    /**
     * 设置适配器，用于显示数据
     *
     * @param viewAdapter 如果ContentView是ListView那么viewAdapter就要继承于ListAdapter，如果是RecyclerView，那么viewAdapter就要继承于RecyclerView.Adapter
     * @param dataAdapter 数据显示adapter
     */
    public void setAdapter2(Object viewAdapter, IDataAdapter<DATA> dataAdapter){
        if (contentView instanceof ListView) {
            setAdapter2(viewAdapter, dataAdapter, listViewHandler);
        } else if (contentView instanceof RecyclerView) {
            setAdapter2(viewAdapter, dataAdapter,  recyclerViewHandler);
        } else {
            setAdapter2(viewAdapter, dataAdapter, null);
        }
    }

    /**
     * 设置适配器，用于显示数据
     * @param viewAdapter 如果ContentView是ListView那么viewAdapter就要继承于ListAdapter，如果是RecyclerView，那么viewAdapter就要继承于RecyclerView.Adapter
     * @param dataAdapter 数据显示adapter
     * @param viewHandler 用于设置view的适配器,用于添加加载更多的FootView,滑动事件触发加载更多
     */
    public void setAdapter2(Object viewAdapter, IDataAdapter<DATA> dataAdapter, ViewHandler viewHandler) {
        hasInitLoadMoreView = false;
        if (viewHandler != null) {
            View view = getContentView();
            hasInitLoadMoreView = viewHandler.handleSetAdapter(view, viewAdapter, mLoadMoreView, onClickLoadMoreListener);
            viewHandler.setOnScrollBottomListener(view, onScrollBottomListener);
        }
        this.dataAdapter = dataAdapter;
    }

    private boolean hasInitLoadMoreView = false;

    /**
     * 设置状态监听，监听开始刷新，刷新成功，开始加载更多，加载更多成功
     *
     * @param onStateChangeListener
     */
    public void setOnStateChangeListener(OnStateChangeListener<DATA> onStateChangeListener) {
        this.onStateChangeListener.setOnStateChangeListener(onStateChangeListener);
    }

    /**
     * 设置状态监听，监听开始刷新，刷新成功
     *
     * @param onRefreshStateChangeListener
     */
    public void setOnStateChangeListener(OnRefreshStateChangeListener<DATA> onRefreshStateChangeListener) {
        this.onStateChangeListener.setOnRefreshStateChangeListener(onRefreshStateChangeListener);
    }

    /**
     * 设置状态监听，监听开始加载更多，加载更多成功
     *
     * @param onLoadMoreStateChangeListener
     */
    public void setOnStateChangeListener(OnLoadMoreStateChangeListener<DATA> onLoadMoreStateChangeListener) {
        this.onStateChangeListener.setOnLoadMoreStateChangeListener(onLoadMoreStateChangeListener);
    }

    /**
     * 刷新，开启异步线程，并且显示加载中的界面，当数据加载完成自动还原成加载完成的布局，并且刷新列表数据
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void refresh() {
        if (dataAdapter == null || (dataSource == null && asyncDataSource == null)) {
            if (refreshView != null) {
                refreshView.showRefreshComplete();
            }
            return;
        }
        if (dataSource != null) {
            if (asyncTask != null && asyncTask.getStatus() != AsyncTask.Status.FINISHED) {
                asyncTask.cancel(true);
            }
            asyncTask = new RefreshAsyncTask(dataSource, dataAdapter);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                asyncTask.execute();
            }
        } else {
            if (cancle != null) {
                cancle.cancle();
                cancle = null;
            }
            MResponseSender responseSender = new RefreshResponseSender(asyncDataSource, dataAdapter);
            responseSender.onPreExecute();
            cancle = responseSender.execute();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void loadMore() {
        if (isLoading()) {
            return;
        }
        if (dataAdapter.isEmpty()) {
            refresh();
            return;
        }
        if (dataAdapter == null || (dataSource == null && asyncDataSource == null)) {
            if (refreshView != null) {
                refreshView.showRefreshComplete();
            }
            return;
        }
        if (dataSource != null) {// 开启线程执行IDataSource
            if (asyncTask != null && asyncTask.getStatus() != AsyncTask.Status.FINISHED) {
                asyncTask.cancel(true);
            }
            asyncTask = new LoadMoreAsyncTask(dataSource, dataAdapter);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                asyncTask.execute();
            }
        } else { // 开启线程执行 IAsyncDataSource
            if (cancle != null) {
                cancle.cancle();
                cancle = null;
            }
            LoadMoreResponseSender responseSender = new LoadMoreResponseSender(asyncDataSource, dataAdapter);
            responseSender.onPreExecute();
            cancle = responseSender.execute();
        }
    }

    /**
     * 做销毁操作，比如关闭正在加载数据的异步线程等
     */
    public void destory() {
        if (asyncTask != null && asyncTask.getStatus() != AsyncTask.Status.FINISHED) {
            asyncTask.cancel(true);
            asyncTask = null;
        }
        if (cancle != null) {
            cancle.cancle();
            cancle = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    private static class MyAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
        private volatile boolean post;

        @Override
        protected Result doInBackground(Params... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);
            post = true;
        }

        private boolean isLoading() {
            if (post) {
                return false;
            }
            return getStatus() != Status.FINISHED;
        }
    }

    private abstract class MResponseSender implements ResponseSender<DATA> {

        protected abstract void onPreExecute();

        @Override
        public final void sendError(final Exception exception) {
            if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPostExecute(null, exception);
                        cancle = null;
                    }
                });
            } else {
                onPostExecute(null, exception);
                cancle = null;
            }
        }

        @Override
        public final void sendData(final DATA data) {
            if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPostExecute(data, null);
                        cancle = null;
                    }
                });
            } else {
                onPostExecute(data, null);
                cancle = null;
            }
        }

        protected abstract void onPostExecute(DATA data, Exception exception);

        public RequestHandle execute() {
            try {
                return executeImp();
            } catch (Exception e) {
                e.printStackTrace();
                onPostExecute(null, e);
            }
            return null;
        }

        public abstract RequestHandle executeImp() throws Exception;

    }

    private class RefreshResponseSender extends MResponseSender {
        private IAsyncDataSource<DATA> tDataSource;
        private IDataAdapter<DATA> tDataAdapter;
        private Runnable showRefreshing;

        public RefreshResponseSender(IAsyncDataSource<DATA> tDataSource, IDataAdapter<DATA> tDataAdapter) {
            super();
            this.tDataSource = tDataSource;
            this.tDataAdapter = tDataAdapter;
        }

        @Override
        protected void onPreExecute() {
            if (hasInitLoadMoreView && mLoadMoreView != null) {
                mLoadMoreView.showNormal();
            }
            if (tDataSource instanceof IDataCacheLoader) {
                @SuppressWarnings("unchecked")
                IDataCacheLoader<DATA> cacheLoader = (IDataCacheLoader<DATA>) tDataSource;
                DATA data = cacheLoader.loadCache(tDataAdapter.isEmpty());
                if (data != null) {
                    tDataAdapter.notifyDataChanged(data, true);
                }
            }
            if (tDataAdapter.isEmpty()) {
                mLoadView.showLoading();
            }else{
                mLoadView.restore();
            }
            handler.post(showRefreshing = new Runnable() {

                @Override
                public void run() {
                    if (tDataAdapter.isEmpty()) {
                        refreshView.showRefreshComplete();
                    } else {
                        refreshView.showRefreshing();
                    }
                }
            });
            onStateChangeListener.onStartRefresh(tDataAdapter);
        }

        @Override
        public RequestHandle executeImp() throws Exception {
            return tDataSource.refresh(this);
        }

        @Override
        protected void onPostExecute(DATA result, Exception exception) {
            handler.removeCallbacks(showRefreshing);
            if (result == null) {
                if (tDataAdapter.isEmpty()) {
                    mLoadView.showFail(exception);
                } else {
                    mLoadView.tipFail(exception);
                }
            } else {
                loadDataTime = System.currentTimeMillis();
                tDataAdapter.notifyDataChanged(result, true);
                if (tDataAdapter.isEmpty()) {
                    mLoadView.showEmpty();
                } else {
                    mLoadView.restore();
                }
                hasMoreData = tDataSource.hasMore();
                if (hasInitLoadMoreView && mLoadMoreView != null) {
                    if (hasMoreData) {
                        mLoadMoreView.showNormal();
                    } else {
                        mLoadMoreView.showNomore();
                    }
                }
            }
            onStateChangeListener.onEndRefresh(tDataAdapter, result);
            refreshView.showRefreshComplete();
        }

        @Override
        public void sendProgress(long current, long total, Object exraData) {

        }
    }

    private class RefreshAsyncTask extends MyAsyncTask<Void, Void, DATA> {

        private IDataSource<DATA> tDataSource;
        private IDataAdapter<DATA> tDataAdapter;
        private volatile Exception tException;
        private Runnable showRefreshing;

        public RefreshAsyncTask(IDataSource<DATA> dataSource, IDataAdapter<DATA> dataAdapter) {
            super();
            this.tDataSource = dataSource;
            this.tDataAdapter = dataAdapter;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (hasInitLoadMoreView && mLoadMoreView != null) {
                mLoadMoreView.showNormal();
            }
            if (tDataSource instanceof IDataCacheLoader) {
                @SuppressWarnings("unchecked")
                IDataCacheLoader<DATA> cacheLoader = (IDataCacheLoader<DATA>) tDataSource;
                DATA data = cacheLoader.loadCache(tDataAdapter.isEmpty());
                if (data != null) {
                    tDataAdapter.notifyDataChanged(data, true);
                }
            }
            if (tDataAdapter.isEmpty()) {
                mLoadView.showLoading();
            }else{
                mLoadView.restore();
            }
            handler.post(showRefreshing = new Runnable() {

                @Override
                public void run() {
                    if (tDataAdapter.isEmpty()) {
                        refreshView.showRefreshComplete();
                    } else {
                        refreshView.showRefreshing();
                    }
                }
            });
            onStateChangeListener.onStartRefresh(tDataAdapter);
        }

        ;

        @Override
        protected DATA doInBackground(Void... params) {
            try {
                return tDataSource.refresh();
            } catch (Exception e) {
                this.tException = e;
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            handler.removeCallbacks(showRefreshing);
        }

        @Override
        protected void onPostExecute(DATA result) {
            super.onPostExecute(result);
            handler.removeCallbacks(showRefreshing);
            if (result == null) {
                if (tDataAdapter.isEmpty()) {
                    mLoadView.showFail(tException);
                } else {
                    mLoadView.tipFail(tException);
                }
            } else {
                loadDataTime = System.currentTimeMillis();
                tDataAdapter.notifyDataChanged(result, true);
                if (tDataAdapter.isEmpty()) {
                    mLoadView.showEmpty();
                } else {
                    mLoadView.restore();
                }
                hasMoreData = tDataSource.hasMore();
                if (hasInitLoadMoreView && mLoadMoreView != null) {
                    if (hasMoreData) {
                        mLoadMoreView.showNormal();
                    } else {
                        mLoadMoreView.showNomore();
                    }
                }
            }
            onStateChangeListener.onEndRefresh(tDataAdapter, result);
            refreshView.showRefreshComplete();
        }

        ;

    }

    private class LoadMoreResponseSender extends MResponseSender {
        private IAsyncDataSource<DATA> tDataSource;
        private IDataAdapter<DATA> tDataAdapter;

        public LoadMoreResponseSender(IAsyncDataSource<DATA> tDataSource, IDataAdapter<DATA> tDataAdapter) {
            super();
            this.tDataSource = tDataSource;
            this.tDataAdapter = tDataAdapter;
        }

        @Override
        protected void onPreExecute() {
            onStateChangeListener.onStartLoadMore(tDataAdapter);
            if (hasInitLoadMoreView && mLoadMoreView != null) {
                mLoadMoreView.showLoading();
            }
        }

        @Override
        public RequestHandle executeImp() throws Exception {
            return tDataSource.loadMore(this);
        }

        @Override
        protected void onPostExecute(DATA result, Exception exception) {
            if (result == null) {
                mLoadView.tipFail(exception);
                if (hasInitLoadMoreView && mLoadMoreView != null) {
                    mLoadMoreView.showFail(exception);
                }
            } else {
                tDataAdapter.notifyDataChanged(result, false);
                if (tDataAdapter.isEmpty()) {
                    mLoadView.showEmpty();
                } else {
                    mLoadView.restore();
                }
                hasMoreData = tDataSource.hasMore();
                if (hasInitLoadMoreView && mLoadMoreView != null) {
                    if (hasMoreData) {
                        mLoadMoreView.showNormal();
                    } else {
                        mLoadMoreView.showNomore();
                    }
                }
            }
            onStateChangeListener.onEndLoadMore(tDataAdapter, result);
        }

        @Override
        public void sendProgress(long current, long total, Object exraData) {

        }
    }

    private class LoadMoreAsyncTask extends MyAsyncTask<Void, Void, DATA> {

        private IDataSource<DATA> tDataSource;
        private IDataAdapter<DATA> tDataAdapter;
        private volatile Exception tException;

        public LoadMoreAsyncTask(IDataSource<DATA> dataSource, IDataAdapter<DATA> dataAdapter) {
            super();
            this.tDataSource = dataSource;
            this.tDataAdapter = dataAdapter;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            onStateChangeListener.onStartLoadMore(tDataAdapter);
            if (hasInitLoadMoreView && mLoadMoreView != null) {
                mLoadMoreView.showLoading();
            }
        }

        @Override
        protected DATA doInBackground(Void... params) {
            try {
                return tDataSource.loadMore();
            } catch (Exception e) {
                this.tException = e;
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(DATA result) {
            super.onPostExecute(result);
            if (result == null) {
                mLoadView.tipFail(tException);
                if (hasInitLoadMoreView && mLoadMoreView != null) {
                    mLoadMoreView.showFail(tException);
                }
            } else {
                tDataAdapter.notifyDataChanged(result, false);
                if (tDataAdapter.isEmpty()) {
                    mLoadView.showEmpty();
                } else {
                    mLoadView.restore();
                }
                hasMoreData = tDataSource.hasMore();
                if (hasInitLoadMoreView && mLoadMoreView != null) {
                    if (hasMoreData) {
                        mLoadMoreView.showNormal();
                    } else {
                        mLoadMoreView.showNomore();
                    }
                }
            }
            onStateChangeListener.onEndLoadMore(tDataAdapter, result);
        }

        ;
    }

    /**
     * 是否正在加载中
     *
     * @return
     */
    public boolean isLoading() {
        if (asyncDataSource != null) {
            return cancle != null;
        }
        return asyncTask != null && asyncTask.isLoading();
    }

    private OnRefreshListener onRefreshListener = new OnRefreshListener() {

        @Override
        public void onRefresh() {
            refresh();
        }
    };

    @SuppressWarnings("unchecked")
    public <T extends View> T getContentView() {
        return (T) refreshView.getContentView();
    }

    /**
     * 获取上次刷新数据的时间（数据成功的加载），如果数据没有加载成功过，那么返回-1
     *
     * @return
     */
    public long getLoadDataTime() {
        return loadDataTime;
    }

    public IDataAdapter<DATA> getAdapter() {
        return dataAdapter;
    }

    public IDataSource<DATA> getDataSource() {
        return dataSource;
    }

    public ILoadView getLoadView() {
        return mLoadView;
    }

    public ILoadMoreView getLoadMoreView() {
        return mLoadMoreView;
    }

    public void setAutoLoadMore(boolean autoLoadMore) {
        this.autoLoadMore = autoLoadMore;
    }

    private boolean autoLoadMore = true;

    public boolean isAutoLoadMore() {
        return autoLoadMore;
    }

    private OnClickListener onClickLoadMoreListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            loadMore();
        }
    };

    private OnClickListener onClickRefresListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            refresh();
        }
    };

    protected IRefreshView getRefreshView() {
        return refreshView;
    }

    /**
     * 加载监听
     *
     * @param <DATA>
     * @author zsy
     */
    private static class MOnStateChangeListener<DATA> implements OnStateChangeListener<DATA> {
        private OnStateChangeListener<DATA> onStateChangeListener;
        private OnRefreshStateChangeListener<DATA> onRefreshStateChangeListener;
        private OnLoadMoreStateChangeListener<DATA> onLoadMoreStateChangeListener;

        public void setOnStateChangeListener(OnStateChangeListener<DATA> onStateChangeListener) {
            this.onStateChangeListener = onStateChangeListener;
        }

        public void setOnRefreshStateChangeListener(OnRefreshStateChangeListener<DATA> onRefreshStateChangeListener) {
            this.onRefreshStateChangeListener = onRefreshStateChangeListener;
        }

        public void setOnLoadMoreStateChangeListener(OnLoadMoreStateChangeListener<DATA> onLoadMoreStateChangeListener) {
            this.onLoadMoreStateChangeListener = onLoadMoreStateChangeListener;
        }

        @Override
        public void onStartRefresh(IDataAdapter<DATA> adapter) {
            if (onStateChangeListener != null) {
                onStateChangeListener.onStartRefresh(adapter);
            } else if (onRefreshStateChangeListener != null) {
                onRefreshStateChangeListener.onStartRefresh(adapter);
            }
        }

        @Override
        public void onEndRefresh(IDataAdapter<DATA> adapter, DATA result) {
            if (onStateChangeListener != null) {
                onStateChangeListener.onEndRefresh(adapter, result);
            } else if (onRefreshStateChangeListener != null) {
                onRefreshStateChangeListener.onEndRefresh(adapter, result);
            }
        }

        @Override
        public void onStartLoadMore(IDataAdapter<DATA> adapter) {
            if (onStateChangeListener != null) {
                onStateChangeListener.onStartLoadMore(adapter);
            } else if (onLoadMoreStateChangeListener != null) {
                onLoadMoreStateChangeListener.onStartLoadMore(adapter);
            }
        }

        @Override
        public void onEndLoadMore(IDataAdapter<DATA> adapter, DATA result) {
            if (onStateChangeListener != null) {
                onStateChangeListener.onEndLoadMore(adapter, result);
            } else if (onLoadMoreStateChangeListener != null) {
                onLoadMoreStateChangeListener.onEndLoadMore(adapter, result);
            }
        }

    }

    private OnScrollBottomListener onScrollBottomListener = new OnScrollBottomListener() {

        @Override
        public void onScorllBootom() {
            if (autoLoadMore && hasMoreData && !isLoading()) {
                // 如果网络可以用
                if (needCheckNetwork && !NetworkUtils.hasNetwork(context)) {
                    mLoadMoreView.showFail(new Exception("网络不可用"));
                } else {
                    loadMore();
                }
            }
        }
    };

    public static interface OnScrollBottomListener {
        public void onScorllBootom();
    }

    private class ProxyAsyncDataSource<DATA> implements IAsyncDataSource<DATA> {
        private IAsyncTask<DATA> task;

        public ProxyAsyncDataSource(IAsyncTask<DATA> task) {
            this.task = task;
        }

        @Override
        public RequestHandle refresh(ResponseSender<DATA> sender) throws Exception {
            return task.execute(sender);
        }

        @Override
        public RequestHandle loadMore(ResponseSender<DATA> sender) throws Exception {
            return null;
        }

        @Override
        public boolean hasMore() {
            return false;
        }
    }

    private class ProxySyncDataSource<DATA> implements IDataSource<DATA> {
        private ITask<DATA> task;

        public ProxySyncDataSource(ITask<DATA> task) {
            this.task = task;
        }

        @Override
        public DATA refresh() throws Exception {
            return task.execute(new ProgressSender() {
                @Override
                public void sendProgress(long current, long total, Object exraData) {

                }
            });
        }

        @Override
        public DATA loadMore() throws Exception {
            return null;
        }

        @Override
        public boolean hasMore() {
            return false;
        }
    }
}
