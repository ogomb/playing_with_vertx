package io.vertx.intro;


import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class FirstVerticle extends AbstractVerticle {

    /*
    When packaging the app and running it do this
    java -jar target/my-first-app-1.0-SNAPSHOT.jar -conf src/main/conf/application-conf.json

    to include the configurations if you have them.

    But this method is not that right what of secret configurations?

    vertx-config to the rescue

     */
    @Override
    public void start(Future future) throws Exception {
        ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(config -> {
            if (config.failed()) {
                future.fail(config.cause());
            } else {
                vertx.createHttpServer()
                        .requestHandler(r ->
                                r.response().end("<h1>Hi, Vert.x application</h1>"))
                        .listen(config.result().getInteger("HTTP_PORT", 8080),
                                result -> {
                                    if (result.succeeded()) {
                                        future.complete();
                                    } else {
                                        future.fail(result.cause());
                                    }
                                });

            }
        });
    }
}

