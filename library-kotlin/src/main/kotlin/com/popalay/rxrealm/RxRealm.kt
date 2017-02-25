package com.popalay.rxrealm

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import rx.Observable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.atomic.AtomicReference

class RxRealm private constructor() {

    fun <T : RealmObject> listenList(query: (Realm) -> RealmResults<T>): Observable<List<T>> {
        val dbHandler = createDbHandler()
        val scheduler = AndroidSchedulers.from(dbHandler.looper)
        val realmReference = AtomicReference<Realm>(null)
        return Observable.defer {
            val realm = Realm.getDefaultInstance()
            realmReference.set(realm)
            query.invoke(realm).asObservable()
        }
                .filter { result -> result.isLoaded && result.isValid }
                .map { result -> realmReference.get().copyFromRealm(result) }
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler)
                .doOnUnsubscribe { unsubscribeOnDbThread(dbHandler, realmReference.get()) }
    }

    fun <T : RealmObject> listenElement(query: (Realm) -> T): Observable<T> {
        val dbHandler = createDbHandler()
        val scheduler = AndroidSchedulers.from(dbHandler.looper)
        val realmReference = AtomicReference<Realm>(null)
        return Observable.defer {
            val realm = Realm.getDefaultInstance()
            realmReference.set(realm)
            query(realm).asObservable<T>()
        }
                .filter { result -> result.isLoaded && result.isValid }
                .map { result -> realmReference.get().copyFromRealm(result) }
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler)
                .doOnUnsubscribe { unsubscribeOnDbThread(dbHandler, realmReference.get()) }
    }

    fun <T : RealmObject> getList(query: (Realm) -> RealmResults<T>): Single<List<T>> {
        return Single.fromEmitter { emitter ->
            val realm = Realm.getDefaultInstance()
            emitter.onSuccess(realm.copyFromRealm(query.invoke(realm)))
            emitter.setCancellation { realm.close() }
        }
    }

    fun <T : RealmObject?> getElement(query: (Realm) -> T): Single<T> {
        return Single.fromEmitter { emitter ->
            val realm = Realm.getDefaultInstance()
            val result = query(realm)
            if (result != null) {
                emitter.onSuccess(realm.copyFromRealm(result))
            } else {
                emitter.onSuccess(null)
            }
            emitter.setCancellation { realm.close() }
        }
    }

    fun doTransactional(transaction: (Realm) -> Unit) {
        Realm.getDefaultInstance().use { it.executeTransaction { transaction(it) } }
    }

    private fun createDbHandler(): HandlerThread {
        val handlerThread = HandlerThread("RealmReadThread", Process.THREAD_PRIORITY_BACKGROUND)
        handlerThread.start()
        return handlerThread
    }

    private fun unsubscribeOnDbThread(handlerThread: HandlerThread, realm: Realm) {
        val handler = Handler(handlerThread.looper)
        handler.post {
            realm.close()
            handlerThread.quitSafely()
        }
    }
}