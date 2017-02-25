package com.github.popalay.rxrealm;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.SingleEmitter;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Cancellable;
import rx.functions.Func0;
import rx.functions.Func1;

public final class RxRealm {

    private RxRealm() {
    }

    public static <T extends RealmObject> Observable<List<T>> listenList(final Func1<Realm, RealmResults<T>>
            query) {
        final HandlerThread dbHandler = createDbHandler();
        final Scheduler scheduler = AndroidSchedulers.from(dbHandler.getLooper());
        final AtomicReference<Realm> realmReference = new AtomicReference<>(null);
        return Observable.defer(new Func0<Observable<RealmResults<T>>>() {
            @Override
            public Observable<RealmResults<T>> call() {
                final Realm realm = Realm.getDefaultInstance();
                realmReference.set(realm);
                return query.call(realm).asObservable();
            }
        })
                .filter(new Func1<RealmResults<T>, Boolean>() {
                    @Override
                    public Boolean call(RealmResults<T> result) {
                        return result.isLoaded() && result.isValid();
                    }
                })
                .map(new Func1<RealmResults<T>, List<T>>() {
                    @Override
                    public List<T> call(RealmResults<T> result) {
                        return realmReference.get().copyFromRealm(result);
                    }
                })
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        unsubscribeOnDbThread(dbHandler, realmReference.get());
                    }
                });
    }

    public static <T extends RealmObject> Observable<T> listenElement(final Func1<Realm, T> query) {
        final HandlerThread dbHandler = createDbHandler();
        final Scheduler scheduler = AndroidSchedulers.from(dbHandler.getLooper());
        final AtomicReference<Realm> realmReference = new AtomicReference<>(null);
        return Observable.defer(new Func0<Observable<T>>() {
            @Override
            public Observable<T> call() {
                final Realm realm = Realm.getDefaultInstance();
                realmReference.set(realm);
                return query.call(realm).asObservable();
            }
        })
                .filter(new Func1<T, Boolean>() {
                    @Override
                    public Boolean call(T result) {
                        return result.isLoaded() && result.isValid();
                    }
                })
                .map(new Func1<T, T>() {
                    @Override
                    public T call(T result) {
                        return realmReference.get().copyFromRealm(result);
                    }
                })
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        unsubscribeOnDbThread(dbHandler, realmReference.get());
                    }
                });
    }

    public static <T extends RealmObject> Single<List<T>> getList(final Func1<Realm, RealmResults<T>> query) {
        return Single.fromEmitter(new Action1<SingleEmitter<List<T>>>() {
            @Override
            public void call(SingleEmitter<List<T>> emitter) {
                final Realm realm = Realm.getDefaultInstance();
                final RealmResults<T> realmResults = query.call(realm);
                emitter.onSuccess(realm.copyFromRealm(realmResults));
                emitter.setCancellation(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        realm.close();
                    }
                });
            }
        });
    }

    public static <T extends RealmObject> Single<T> getElement(final Func1<Realm, T> query) {
        return Single.fromEmitter(new Action1<SingleEmitter<T>>() {
            @Override
            public void call(SingleEmitter<T> emitter) {
                final Realm realm = Realm.getDefaultInstance();
                final T result = query.call(realm);
                if (result != null) {
                    emitter.onSuccess(realm.copyFromRealm(result));
                } else {
                    emitter.onSuccess(null);
                }
                emitter.setCancellation(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        realm.close();
                    }
                });
            }
        });
    }

    public static void doTransactional(final Action1<Realm> transaction) {
        try (Realm realm = Realm.getDefaultInstance()) {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    transaction.call(realm);
                }
            });
        }
    }

    private static HandlerThread createDbHandler() {
        final HandlerThread handlerThread = new HandlerThread("RealmReadThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        return handlerThread;
    }

    private static void unsubscribeOnDbThread(final HandlerThread handlerThread, final Realm realm) {
        final Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                realm.close();
                handlerThread.quitSafely();
            }
        });
    }
}