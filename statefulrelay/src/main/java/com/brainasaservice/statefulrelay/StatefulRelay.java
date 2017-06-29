package com.brainasaservice.statefulrelay;

import com.jakewharton.rxrelay2.BehaviorRelay;

import org.reactivestreams.Subscription;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Damian on 06.05.2017.
 */

public abstract class StatefulRelay<T> {
    public static class Builder<T> {

        /**
         * May or may not provide an initial value for the relay.
         */
        private Maybe<T> initialization;

        /**
         * May or may not provide updated values for the relay.
         */
        private Maybe<T> updater;

        /**
         * Invalidator instance used to invalidate the relay's value.
         */
        private Invalidator<T> invalidator;

        /**
         * Time to live in milliseconds
         */
        private long timeToLive = 0;

        /**
         * Default implementation for the update error consumer.
         */
        private Consumer<Throwable> updaterErrorConsumer = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {

            }
        };

        /**
         * Default implementation for the initialization error consumer.
         */
        private Consumer<Throwable> initializationErrorConsumer = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {

            }
        };

        /**
         * @param ttl Time to live in milliseconds, set to 0 for unlimited
         * @return
         */
        public Builder<T> withTTL(long ttl) {
            this.timeToLive = ttl;
            return this;
        }

        /**
         * @param ttl      Time to live, set to 0 for unlimited
         * @param timeUnit TimeUnit of TTL
         * @return
         */
        public Builder<T> withTTL(long ttl, TimeUnit timeUnit) {
            this.timeToLive = timeUnit.toMillis(ttl);
            return this;
        }

        /**
         * @param updater Maybe stream used to update the object.
         * @return
         */
        public Builder<T> withUpdater(Maybe<T> updater) {
            this.updater = updater;
            return this;
        }

        /**
         * @param updater Callable used to update the object. Wrapped in a Maybe stream internally.
         * @return
         */
        public Builder<T> withUpdater(Callable<T> updater) {
            this.updater = Maybe.fromCallable(updater);
            return this;
        }

        /**
         * @param updater       Callable used to update the object. Wrapped in a Maybe stream internally.
         * @param errorConsumer Error consumer to properly handle errors in the updating process.
         * @return
         */
        public Builder<T> withUpdater(Callable<T> updater, Consumer<Throwable> errorConsumer) {
            this.updater = Maybe.fromCallable(updater);
            this.updaterErrorConsumer = errorConsumer;
            return this;
        }

        /**
         * @param initialization Initial value for the relay
         * @return
         */
        public Builder<T> withInitialization(T initialization) {
            this.initialization = Maybe.just(initialization);
            return this;
        }

        /**
         * @param initialization Initial value for the relay wrapped in a Maybe stream.
         * @return
         */
        public Builder<T> withInitialization(Maybe<T> initialization) {
            this.initialization = initialization;
            return this;
        }

        /**
         * @param initialization Initial value for the relay wrapped in a Maybe stream.
         * @param errorConsumer  Error consumer to properly handle errors in the initialization process.
         * @return
         */
        public Builder<T> withInitialiation(Maybe<T> initialization, Consumer<Throwable> errorConsumer) {
            this.initialization = initialization;
            this.initializationErrorConsumer = errorConsumer;
            return this;
        }

        /**
         * @param initialization Callable providing an initial value for the relay.
         * @return
         */
        public Builder<T> withInitialization(Callable<T> initialization) {
            this.initialization = Maybe.fromCallable(initialization);
            return this;
        }

        /**
         * @param initialization Callable providing an initial value for the relay.
         * @param errorConsumer  Error consumer to properly handle errors in the initialization process.
         * @return
         */
        public Builder<T> withInitialization(Callable<T> initialization, Consumer<Throwable> errorConsumer) {
            this.initialization = Maybe.fromCallable(initialization);
            this.initializationErrorConsumer = errorConsumer;
            return this;
        }

        /**
         * @param invalidator Invalidator for the relay's value.
         * @return
         */
        public Builder<T> withInvalidator(Invalidator<T> invalidator) {
            this.invalidator = invalidator;
            return this;
        }

        /**
         * @return StatefulRelay instance
         */
        public StatefulRelay<T> build() {
            return new StatefulRelay<T>() {
                @Override
                public Maybe<T> maybeInitialValue() {
                    return initialization;
                }

                @Override
                public Maybe<T> maybeUpdate() {
                    return updater;
                }

                @Override
                public Consumer<Throwable> updateErrorConsumer() {
                    return updaterErrorConsumer;
                }

                @Override
                public Consumer<Throwable> initializationErrorConsumer() {
                    return initializationErrorConsumer;
                }

                @Override
                public Invalidator<T> getInvalidator() {
                    return invalidator;
                }

                @Override
                public long getTTL() {
                    return timeToLive;
                }
            };
        }
    }

    private BehaviorRelay<T> relay = BehaviorRelay.create();

    private Disposable updateDisposable = null;

    private Disposable initializeDisposable = null;

    private boolean isInitialized = false;

    private boolean isInvalidated = false;

    private boolean isUpdating = false;

    private boolean isInitializing = false;

    private long lastUpdateTime = 0;

    /**
     * @return True if the relay's value has to be initialized.
     */
    private boolean shouldInitialize() {
        if (!relay.hasValue()) {
            if (!isInitialized
                    && !isInitializing
                    && (initializeDisposable == null || initializeDisposable.isDisposed())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return True if the relay's value has to be updated.
     */
    private boolean shouldUpdate() {
        if ((!relay.hasValue() || isInvalidated()) && isInitialized) {
            if ((updateDisposable == null || updateDisposable.isDisposed()) && !isUpdating) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return True if the relay's value has been invalidated.
     */
    private boolean isInvalidated() {
        if (isInvalidated) {
            return true;
        }
        if (relay.hasValue() && relay.getValue() instanceof Invalidatable) {
            if (((Invalidatable) relay.getValue()).isInvalidated()) {
                return true;
            }
        }

        if (getTTL() > 0) {
            if (lastUpdateTime + getTTL() < System.currentTimeMillis()) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return Flowable with backpressure strategy LATEST
     */
    public Flowable<T> asFlowable() {
        return asFlowable(BackpressureStrategy.LATEST);
    }

    /**
     * @param backpressureStrategy BackpressureStrategy for the returned flowable
     * @return Flowable representing the relay
     */
    public Flowable<T> asFlowable(BackpressureStrategy backpressureStrategy) {
        return relay
                .toFlowable(backpressureStrategy)
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(new Consumer<Subscription>() {
                    @Override
                    public void accept(@NonNull Subscription subscription) throws Exception {
                        if (shouldInitialize()) {
                            initializeDisposable = internalInitialValue()
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(Schedulers.io())
                                    .subscribe(new Consumer<T>() {
                                        @Override
                                        public void accept(@NonNull T t) throws Exception {
                                            relay.accept(t);
                                        }
                                    }, initializationErrorConsumer());
                        }

                        if (shouldUpdate()) {
                            updateDisposable = internalUpdate()
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(Schedulers.io())
                                    .subscribe(new Consumer<T>() {
                                        @Override
                                        public void accept(@NonNull T t) throws Exception {
                                            relay.accept(t);
                                        }
                                    }, updateErrorConsumer());
                        }
                    }
                })
                .doOnTerminate(new Action() {
                    @Override
                    public void run() throws Exception {
                        if (initializeDisposable != null) {
                            initializeDisposable.dispose();
                        }
                        if (updateDisposable != null) {
                            updateDisposable.dispose();
                        }
                    }
                })
                .doOnNext(new Consumer<T>() {
                    @Override
                    public void accept(@NonNull T t) throws Exception {
                        if (getInvalidator() != null) {
                            if (getInvalidator().isInvalidated(t)) {
                                invalidate();
                            }
                        }
                    }
                });
    }

    /**
     * Invalidates the currently in the relay stored value. Will trigger update process next time
     * the relay's value is accessed.
     */
    public void invalidate() {
        if (relay.getValue() != null && relay.getValue() instanceof Invalidatable) {
            ((Invalidatable) relay.getValue()).invalidate();
        } else {
            isInvalidated = true;
        }
    }

    private Maybe<T> internalUpdate() {
        Maybe<T> value = maybeUpdate();
        if (value != null) {
            return value
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnSubscribe(new Consumer<Disposable>() {
                        @Override
                        public void accept(@NonNull Disposable disposable) throws Exception {
                            isUpdating = true;
                        }
                    })
                    .doOnSuccess(new Consumer<T>() {
                        @Override
                        public void accept(@NonNull T t) throws Exception {
                            lastUpdateTime = System.currentTimeMillis();
                        }
                    })
                    .doFinally(new Action() {
                        @Override
                        public void run() throws Exception {
                            isUpdating = false;
                            isInvalidated = false;
                        }
                    });
        }

        return Maybe.empty();
    }

    private Maybe<T> internalInitialValue() {
        Maybe<T> value = maybeInitialValue();
        if (value != null) {
            return value
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnSubscribe(new Consumer<Disposable>() {
                        @Override
                        public void accept(@NonNull Disposable disposable) throws Exception {
                            isInitializing = true;
                        }
                    })
                    .doFinally(new Action() {
                        @Override
                        public void run() throws Exception {
                            isInitializing = false;
                            isInitialized = true;
                        }
                    });
        }
        isInitialized = true;
        return Maybe.empty();
    }

    Maybe<T> maybeUpdate() {
        return null;
    }

    Consumer<Throwable> initializationErrorConsumer() {
        return null;
    }

    Consumer<Throwable> updateErrorConsumer() {
        return null;
    }

    Maybe<T> maybeInitialValue() {
        return null;
    }

    Invalidator<T> getInvalidator() {
        return null;
    }

    long getTTL() {
        return 0;
    }
}
