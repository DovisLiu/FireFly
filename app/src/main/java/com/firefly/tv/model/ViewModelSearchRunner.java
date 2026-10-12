package com.firefly.tv.model;

import com.firefly.tv.Constant;
import com.firefly.tv.bean.Result;
import com.firefly.tv.bean.Site;
import com.firefly.tv.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

final class ViewModelSearchRunner {

    private final List<Future<?>> futures;
    private final AtomicInteger epoch;

    ViewModelSearchRunner() {
        futures = new CopyOnWriteArrayList<>();
        epoch = new AtomicInteger(0);
    }

    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult, Runnable onAllFailed) {
        int current = nextEpoch();
        cancelFutures();
        Progress progress = new Progress(sites.size());
        sites.forEach(site -> execute(taskFactory.apply(site), current, onResult, progress, onAllFailed));
    }

    void stop() {
        nextEpoch();
        cancelFutures();
    }

    private int nextEpoch() {
        return epoch.incrementAndGet();
    }

    private void cancelFutures() {
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

    private void execute(Callable<Result> callable, int current, Consumer<Result> onResult, Progress progress, Runnable onAllFailed) {
        FluentFuture<Result> future = FluentFuture.from(Task.largeExecutor().submit(callable)).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.add(future);
        future.addCallback(Task.callback(
                result -> {
                    if (epoch.get() != current) return;
                    progress.success();
                    onResult.accept(result);
                    progress.finish(onAllFailed);
                },
                error -> {
                    if (epoch.get() != current) return;
                    error.printStackTrace();
                    progress.finish(onAllFailed);
                }
        ), MoreExecutors.directExecutor());
    }

    private static final class Progress {

        private final AtomicInteger finished;
        private final AtomicInteger succeeded;
        private final int total;

        private Progress(int total) {
            this.total = total;
            this.finished = new AtomicInteger(0);
            this.succeeded = new AtomicInteger(0);
        }

        private void success() {
            succeeded.incrementAndGet();
        }

        private void finish(Runnable onAllFailed) {
            if (finished.incrementAndGet() == total && succeeded.get() == 0) onAllFailed.run();
        }
    }
}
