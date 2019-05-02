package io.vertx.intro;


import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

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
        Router router = Router.router(vertx);

        router.route("/").handler(rc -> {
            HttpServerResponse response = rc.response();
            response
                    .putHeader("content-type", "text/html")
                    .end("</pre> <h1> Hi vert.x application</h1>");
        });

        router.route("/assets/*")
                .handler(StaticHandler.create("assets"));

        ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(
                config -> {
                    if (config.failed()) {
                        future.fail(config.cause());
                    } else {
                        vertx.createHttpServer()
                                .requestHandler(router::accept)
                                .listen(config().getInteger("HTTP_PORT", 8080),
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

