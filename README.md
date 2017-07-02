[![Download](https://api.bintray.com/packages/popalay/maven/RxRealm/images/download.svg) ](https://bintray.com/popalay/maven/RxRealm/_latestVersion)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-RxRealm-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/5323)
[![License](https://img.shields.io/badge/license-Apache--2.0-green.svg)](https://github.com/Popalay/RxRealm/blob/master/LICENSE)

# Rx Realm

Utilities for using RxJava with Realm

## Getting Started

```groovy
compile 'com.github.popalay:rx-realm:latest-version'

compile 'com.github.popalay:rx2-realm:latest-version'
```
## Usage

From Java
```java
    public Observable<List<MessageResponse>> getMessages() {
        return RxRealm.listenList(realm -> realm.where(MessageResponse.class)
                .findAllSorted(MessageResponse.CREATED_AT, Sort.DESCENDING));
    }

    public Single<List<MessageResponse>> loadMessages() {
        return mApi.messages()
                .observeOn(Schedulers.computation())
                .doOnSuccess(this::saveMessages);
    }
    
    private Completable saveMessages(List<MessageResponse> messages) {
        return RxRealm.doTransactional(realm -> {
                realm.where(MessageResponse.class).findAll().deleteAllFromRealm();
                realm.copyToRealmOrUpdate(messages);
            });
    }
```

From Kotlin
```kotlin
    fun getMessages(): Observable<List<MessageResponse>> {
        return RxRealm.listenList{it.where(MessageResponse::class.java)
                .findAllSorted(MessageResponse.CREATED_AT, Sort.DESCENDING)}
    }

    fun loadMessages(): Single<List<MessageResponse>> {
        return mApi.messages()
                .observeOn(Schedulers.computation())
                .doOnSuccess(this::saveMessages)
    }
    
    private Completable saveMessages(messages: List<MessageResponse>) {
        return RxRealm.doTransactional{
                it.where(MessageResponse::class.java).findAll().deleteAllFromRealm()
                it.copyToRealmOrUpdate(messages)
            }
    }
```

License
-----

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
