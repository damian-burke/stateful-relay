# Stateful Relay

The stateful relay is based on [RxJava](https://github.com/ReactiveX/RxJava) and [RxRelay](https://github.com/JakeWharton/RxRelay).
For debug purposes it also includes [Timber](https://github.com/JakeWharton/timber), but this can easily be excluded since it has no
influence on the functionality.

The `Stateful Relay` is a test to simplify the creation of managed objects with a reactive lifecycle. Objects wrapped within may have
the following attributes:

* Initialization
  * Objects can be created with an initial state.  Initial state can be one of the following.
  * `T` - Object reference.
  * `Maybe<T>` - Maybe provides an initial state.
  * `Callable<T>` - Provide an initial state through a custom callable.

* Invalidation
  * Objects can be invalidated. Invalidation can be triggered in different ways.
  * `TTL` - A time to live can be assigned to the object to force a refresh after a certain time. (TTL is only checked in `doOnSubscribe`, i.e. when accessing the object. TTL is not checked via background timer.) 
  * `Invalidator<T>` - Custom invalidator to check for properties of the object (for example `dirty` flag).
  * `.invalidate()` - Invoking the `invalidate()` method on the relay, which either marks the object as invalidated or invokes the object's `invalidate` method if it implements the `Invalidatable` interface.

* Updating
  * Objects can be update-able. The process may be defined through one of the following.
  * `Maybe<T>`
  * `Callable<T>` 

# Example

    StatefulRelay<String> relay = new StatefulRelay.Builder<String>()
            .withInitialization("Initial Value")
            .withTTL(5, TimeUnit.SECONDS)
            .withUpdater(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return "Updated at " + new Date().toGMTString();
                }
            })
            .withInvalidator(new Invalidator<String>() {
                @Override
                public boolean isInvalidated(String s) {
                    return s != null && s.contains("invalid");
                }
            })
            .build();
            
Invalidation can be triggered by calling

    relay.invalidate();
    
    
# License

This software is released under the [Apache License v2](https://www.apache.org/licenses/LICENSE-2.0). 

[RxJava](https://github.com/ReactiveX/RxJava), [RxRelay](https://github.com/JakeWharton/RxRelay) and [Timber](https://github.com/JakeWharton/timber) are also licensed under Apache License v2.
 
# Copyright

Copyright 2017 Damian Burke
