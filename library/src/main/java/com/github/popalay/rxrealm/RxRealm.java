package com.github.popalay.rxrealm;

import android.os.HandlerThread;
import android.os.Process;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import rx.Completable;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;

public class RxRealm {

    private RxRealm() {}

    public static <T extends RealmObject> Observable<List<T>> listenList
            (final Func1<Realm, RealmResults<T>> query) {
        final AtomicReference<Realm> realmReference = new AtomicReference<>(null);
        return listenRealmResults(query, realmReference)
                .map(result -> realmReference.get().copyFromRealm(result));
    }

    public static <T extends RealmObject> Observable<T> listenElement(final Func1<Realm, RealmResults<T>> query) {
        final AtomicReference<Realm> realmReference = new AtomicReference<>(null);
        return listenRealmResults(query, realmReference)
                .map(result -> !result.isEmpty() ? realmReference.get().copyFromRealm(result).get(0) : null);
    }

    public static <T extends RealmObject> Single<List<T>> getList(final Func1<Realm, RealmResults<T>> query) {
        return Single.fromEmitter(emitter -> {
            final Realm realm = Realm.getDefaultInstance();
            final RealmResults<T> realmResults = query.call(realm);
            emitter.onSuccess(realm.copyFromRealm(realmResults));
            emitter.setCancellation(realm::close);
        });
    }

    public static <T extends RealmObject> Single<T> getElement(final Func1<Realm, T> query) {
        return Single.fromEmitter(emitter -> {
            final Realm realm = Realm.getDefaultInstance();
            final T result = query.call(realm);
            if (result != null) {
                emitter.onSuccess(realm.copyFromRealm(result));
            } else {
                emitter.onSuccess(null);
            }
            emitter.setCancellation(realm::close);
        });
    }

    public static Completable doTransactional(final Action1<Realm> transaction) {
        return Completable.fromAction(() -> {
            try (Realm realm = Realm.getDefaultInstance()) {
                realm.executeTransaction(transaction::call);
            }
        });
    }

    private static <T extends RealmObject> Observable<RealmResults<T>> listenRealmResults(
            final Func1<Realm, RealmResults<T>> query, AtomicReference<Realm> realmReference) {
        final HandlerThread dbHandler = createDbHandler();
        final Scheduler scheduler = AndroidSchedulers.from(dbHandler.getLooper());
        return Observable.defer(() -> {
            final Realm realm = Realm.getDefaultInstance();
            realmReference.set(realm);
            return query.call(realm).asObservable();
        })
                .filter(result -> result.isLoaded() && result.isValid())
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler);
    }

    private static HandlerThread createDbHandler() {
        final HandlerThread handlerThread = new HandlerThread("RealmReadThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        return handlerThread;
    }

}