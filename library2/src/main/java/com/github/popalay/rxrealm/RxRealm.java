package com.github.popalay.rxrealm;

import android.os.HandlerThread;
import android.os.Process;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.MainThreadDisposable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Cancellable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmObject;
import io.realm.RealmResults;

public final class RxRealm {

    private RxRealm() {
    }

    public static <T extends RealmObject> Flowable<List<T>> listenList(
            final Function<Realm, RealmResults<T>> query) {
        final HandlerThread dbHandler = createDbHandler();
        final Scheduler scheduler = AndroidSchedulers.from(dbHandler.getLooper());
        final AtomicReference<Realm> realmReference = new AtomicReference<>(null);
        return Flowable.create(new FlowableOnSubscribe<List<T>>() {
            @Override
            public void subscribe(final FlowableEmitter<List<T>> emitter) throws Exception {
                final Realm realm = Realm.getDefaultInstance();
                realmReference.set(realm);
                final RealmChangeListener<RealmResults<T>> listener = new RealmChangeListener<RealmResults<T>>() {
                    @Override
                    public void onChange(RealmResults<T> result) {
                        emitter.onNext(realm.copyFromRealm(result));
                    }
                };
                final RealmResults<T> result = query.apply(realm);
                result.addChangeListener(listener);
                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        result.removeChangeListener(listener);
                    }
                });
                emitter.setDisposable(new MainThreadDisposable() {
                    @Override
                    protected void onDispose() {
                        result.removeChangeListener(listener);
                    }
                });
            }
        }, BackpressureStrategy.LATEST)
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler);
    }

    public static <T extends RealmObject> Flowable<T> listenElement(
            final Function<Realm, T> query) {
        final HandlerThread dbHandler = createDbHandler();
        final Scheduler scheduler = AndroidSchedulers.from(dbHandler.getLooper());
        final AtomicReference<Realm> realmReference = new AtomicReference<>(null);
        return Flowable.create(new FlowableOnSubscribe<T>() {
            @Override
            public void subscribe(final FlowableEmitter<T> emitter) throws Exception {
                final Realm realm = Realm.getDefaultInstance();
                realmReference.set(realm);
                final RealmChangeListener<T> listener = new RealmChangeListener<T>() {
                    @Override
                    public void onChange(T result) {
                        emitter.onNext(realm.copyFromRealm(result));
                    }
                };
                final T result = query.apply(realm);
                result.addChangeListener(listener);
                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        result.removeChangeListener(listener);
                    }
                });
                emitter.setDisposable(new MainThreadDisposable() {
                    @Override
                    protected void onDispose() {
                        result.removeChangeListener(listener);
                    }
                });
            }
        }, BackpressureStrategy.LATEST)
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler);
    }

    public static <T extends RealmObject> Maybe<List<T>> getList(
            final Function<Realm, RealmResults<T>> query) {
        return Maybe.create(new MaybeOnSubscribe<List<T>>() {
            @Override
            public void subscribe(MaybeEmitter<List<T>> emitter) throws Exception {
                final Realm realm = Realm.getDefaultInstance();
                final RealmResults<T> result = query.apply(realm);
                emitter.onSuccess(realm.copyFromRealm(result));
                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        realm.close();
                    }
                });
            }
        });
    }

    public static <T extends RealmObject> Maybe<T> getElement(final Function<Realm, T> query) {
        return Maybe.create(new MaybeOnSubscribe<T>() {
            @Override
            public void subscribe(MaybeEmitter<T> emitter) throws Exception {
                final Realm realm = Realm.getDefaultInstance();
                final T result = query.apply(realm);
                emitter.onSuccess(realm.copyFromRealm(result));
                emitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        realm.close();
                    }
                });
            }
        });
    }

    public static void doTransactional(final Consumer<Realm> transaction) {
        try (Realm realm = Realm.getDefaultInstance()) {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    try {
                        transaction.accept(realm);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public void generateObjectId(final RealmObject o, final BiConsumer<Realm, Long> transaction) {
        try (Realm realm = Realm.getDefaultInstance()) {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    Number num = realm.where(o.getClass()).max("id");
                    long nextID = (num != null) ? num.longValue() + 1 : 0;
                    try {
                        transaction.accept(realm, nextID);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private static HandlerThread createDbHandler() {
        final HandlerThread handlerThread = new HandlerThread("RealmReadThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        return handlerThread;
    }
}