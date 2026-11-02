package com.firefly.tv.ui.custom;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import java.util.LinkedHashSet;
import java.util.Set;

public class CustomSelector extends PresenterSelector {

    /**
     * T-186 双层保险（A 裁定 §一.3）。
     * 库侧 ItemBridgeAdapter.onCreateViewHolder 对 getPresenter() 的返回值不做判空，
     * 只要有一个类型没注册，整页在 onLayoutChildren 直接 NPE 崩溃（C 在 beta24 抓到 3 次同栈）。
     * 根因由各自的 addPresenter 补齐；本类只保证「即使漏注册，也只是那一条不显示」。
     */
    private static final Presenter SKIP = new SkipPresenter();

    private final Set<Presenter> presenters = new LinkedHashSet<>();
    private final ArrayMap<Key, Presenter> cacheMap = new ArrayMap<>();
    private final ArrayMap<Class<?>, Presenter> singleMap = new ArrayMap<>();
    private final ArrayMap<Class<?>, ArrayMap<Class<? extends Presenter>, Presenter>> nestedMap = new ArrayMap<>();

    public void addPresenter(Class<?> cls, Presenter presenter) {
        singleMap.put(cls, presenter);
        presenters.add(presenter);
        cacheMap.clear();
    }

    public void addPresenter(Class<?> cls, Presenter presenter, Class<? extends Presenter> childType) {
        nestedMap.computeIfAbsent(cls, k -> new ArrayMap<>()).put(childType, presenter);
        presenters.add(presenter);
        cacheMap.clear();
    }

    @Override
    public Presenter getPresenter(Object item) {
        Presenter presenter = lookup(item);
        if (presenter != null) return presenter;
        Log.d("FireFlyFav", "err=NoPresenter type=" + typeOf(item));
        return SKIP;
    }

    private Presenter lookup(Object item) {
        if (item == null) return null;
        Class<?> cls = item.getClass();
        Presenter presenter = singleMap.get(cls);
        if (presenter != null) return presenter;
        ArrayMap<Class<? extends Presenter>, Presenter> map = nestedMap.get(cls);
        if (map == null || map.isEmpty()) return null;
        if (map.size() == 1) {
            Presenter only = map.valueAt(0);
            Class<? extends Presenter> childType = map.keyAt(0);
            cacheMap.put(new Key(cls, childType), only);
            return only;
        }
        if (item instanceof ListRow row) {
            if (row.getAdapter() == null) return null;
            Presenter child = row.getAdapter().getPresenter(row);
            if (child == null) return null;
            Class<?> childCls = child.getClass();
            Key key = new Key(cls, childCls);
            Presenter cached = cacheMap.get(key);
            if (cached != null) return cached;
            for (Class<?> type = childCls; type != null && type != Object.class; type = type.getSuperclass()) {
                Presenter found = map.get(type);
                if (found != null) {
                    cacheMap.put(key, found);
                    return found;
                }
            }
        }
        return null;
    }

    /** 日志里要的是「没注册的那个东西」：行要看它的 child presenter，其余看自身类型。 */
    private String typeOf(Object item) {
        if (item == null) return "null";
        if (item instanceof ListRow row && row.getAdapter() != null) {
            Presenter child = row.getAdapter().getPresenter(row);
            if (child != null) return child.getClass().getSimpleName();
        }
        return item.getClass().getSimpleName();
    }

    @NonNull
    @Override
    public Presenter[] getPresenters() {
        presenters.add(SKIP);
        return presenters.toArray(new Presenter[0]);
    }

    private record Key(Class<?> parent, Class<?> child) {
    }

    /** 空卡：不布局任何内容，只让这一条从页面上消失；崩溃换成可见的「少一条」。 */
    private static class SkipPresenter extends Presenter {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
            return new ViewHolder(new View(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, @NonNull Object item) {
        }

        @Override
        public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        }
    }
}