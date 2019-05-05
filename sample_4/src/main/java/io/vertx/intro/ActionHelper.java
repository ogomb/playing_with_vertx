package io.vertx.intro;

import io.reactivex.functions.Action;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;


import java.util.NoSuchElementException;

public class ActionHelper {

    private static BiConsumer writeJsonResponse(
            RoutingContext context, int status) {
        return (res, err) -> {
            if (err != null) {
                if (err instanceof NoSuchElementException) {
                    context.response().setStatusCode(404)
                            .end(((NoSuchElementException) err).getMessage());
                } else {
                    context.fail((Throwable) err);
                }
            } else {
                context.response().setStatusCode(status)
                        .putHeader("content-type",
                                "application/json; charset=utf-8")
                        .end(Json.encodePrettily(res));
            }
        };
    }

    static  BiConsumer ok(RoutingContext rc) {
        return writeJsonResponse(rc, 200);
    }

    static  BiConsumer created(RoutingContext rc) {
        return writeJsonResponse(rc, 201);
    }

    static Action noContent(RoutingContext rc) {
        return () -> rc.response().setStatusCode(204).end();
    }

    static Consumer onError(RoutingContext rc) {
        return err -> {
            if (err instanceof NoSuchElementException) {
                rc.response().setStatusCode(404)
                        .end(((NoSuchElementException) err).getMessage());
            } else {
                rc.fail((Throwable) err);
            }
        };
    }

}
