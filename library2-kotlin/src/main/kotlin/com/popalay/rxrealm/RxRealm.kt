package com.popalay.rxrealm

import android.os.HandlerThread
import android.os.Process
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableOnSubscribe
import io.reactivex.Maybe
import io.reactivex.android.MainThreadDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmResults
import java.util.concurrent.atomic.AtomicReference

class RxRealm private constructor() {

    fun <T : RealmObject> listenList(query: (Realm) -> RealmResults<T>): Flowable<List<T>> {
        val dbHandler = createDbHandler()
        val scheduler = AndroidSchedulers.from(dbHandler.looper)
        val realmReference = AtomicReference<Realm>(null)
        return Flowable.create(FlowableOnSubscribe<List<T>> { emitter ->
            val realm = Realm.getDefaultInstance()
            realmReference.set(realm)
            val listener = RealmChangeListener<RealmResults<T>> { result -> emitter.onNext(realm.copyFromRealm(result)) }
            val result = query(realm)
            result.addChangeListener(listener)
            emitter.setCancellable { result.removeChangeListener(listener) }
            emitter.setDisposable(object : MainThreadDisposable() {
                override fun onDispose() {
                    result.removeChangeListener(listener)
                }
            })
        }, BackpressureStrategy.LATEST)
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler)
    }

    fun <T : RealmObject> listenElement(query: (Realm) -> T): Flowable<T> {
        val dbHandler = createDbHandler()
        val scheduler = AndroidSchedulers.from(dbHandler.looper)
        val realmReference = AtomicReference<Realm>(null)
        return Flowable.create(FlowableOnSubscribe<T> { emitter ->
            val realm = Realm.getDefaultInstance()
            realmReference.set(realm)
            val listener = RealmChangeListener<T> { result -> emitter.onNext(realm.copyFromRealm(result)) }
            val result = query(realm)
            result.addChangeListener(listener)
            emitter.setCancellable { result.removeChangeListener(listener) }
            emitter.setDisposable(object : MainThreadDisposable() {
                override fun onDispose() {
                    result.removeChangeListener(listener)
                }
            })
        }, BackpressureStrategy.LATEST)
                .subscribeOn(scheduler)
                .unsubscribeOn(scheduler)
    }

    fun <T : RealmObject> getList(query: (Realm) -> RealmResults<T>): Maybe<List<T>> {
        return Maybe.create { emitter ->
            val realm = Realm.getDefaultInstance()
            val result = query(realm)
            emitter.onSuccess(realm.copyFromRealm(result))
            emitter.setCancellable { realm.close() }
        }
    }

    fun <T : RealmObject> getElement(query: (Realm) -> T): Maybe<T> {
        return Maybe.create { emitter ->
            val realm = Realm.getDefaultInstance()
            val result = query(realm)
            emitter.onSuccess(realm.copyFromRealm(result))
            emitter.setCancellable { realm.close() }
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
}