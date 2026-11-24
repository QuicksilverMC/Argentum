package dev.rdh.argentum.impl.render.texture;

import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.resource.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class ParallelSpriteLoading {
    public static final Resource DECODED = new Resource() {
        @Override
        public Identifier getLocation() {
            return null;
        }

        @Override
        public InputStream asStream() {
            return null;
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T extends ResourceMetadataSection> T getMetadata(String name) {
            return null;
        }

        @Override
        public String getSourceName() {
            return null;
        }
    };

    private static final ThreadPoolExecutor EXECUTOR;

    static {
        int threads = Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 8);
        AtomicInteger count = new AtomicInteger();
        ClassLoader loader = ParallelSpriteLoading.class.getClassLoader();
        EXECUTOR = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, "Argentum Sprite Loader #" + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(loader);
            return thread;
        });
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private ParallelSpriteLoading() {
    }

    public static <T, R> List<R> map(List<T> items, Function<? super T, ? extends R> task) {
        List<CompletableFuture<R>> futures = new ArrayList<>(items.size());
        for (T item : items) {
            futures.add(CompletableFuture.supplyAsync(() -> task.apply(item), EXECUTOR));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).handle((result, error) -> null).join();

        List<R> results = new ArrayList<>(items.size());
        for (CompletableFuture<R> future : futures) {
            try {
                results.add(future.join());
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException cause) throw cause;
                if (e.getCause() instanceof Error cause) throw cause;
                throw e;
            }
        }
        return results;
    }
}
