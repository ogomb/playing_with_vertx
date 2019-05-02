package io.vertx.intro;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class FirstVerticle extends AbstractVerticle {

    /*
    in vert.x a verticle is a component. We get an instance of where verticle is deployed
    through the vertx field

    A verticle can be started or stopped.

    We can override the start which takes Future argument or the one which doesn't.
    Vert.x is async in nature so it will not wait for the start to complete
    Future is used to notify vert.x of a fail or success of the start method.

    when the start() with no future argument is called vert.x will rely on the method returning.
     */

    @Override
    public void start(Future<Void> future) throws Exception {
        vertx.createHttpServer()
                .requestHandler(r -> r.response().end("<h1> First vert.x application</h1>"))
                .listen(8080, result -> {
                    if (result.succeeded()){
                        future.complete();
                    }else {
                        future.fail(result.cause());
                    }
                });
    }
}
