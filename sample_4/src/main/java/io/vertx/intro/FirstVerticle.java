package io.vertx.intro;


import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.LinkedHashMap;
import java.util.Map;

public class FirstVerticle extends AbstractVerticle {

    private Map readingList = new LinkedHashMap();
    JDBCClient jdbc;

    /*
    When packaging the app and running it do this
    java -jar target/my-first-app-1.0-SNAPSHOT.jar -conf src/main/conf/application-conf.json

    to include the configurations if you have them.

    But this method is not that right what of secret configurations?

    vertx-config to the rescue

     */
    @Override
    public void start(Future future) throws Exception {

        createSomeData();
        Router router = Router.router(vertx);

        router.route("/").handler(rc -> {
            HttpServerResponse response = rc.response();
            response
                    .putHeader("content-type", "text/html")
                    .end("</pre> <h1> Hi vert.x application</h1>");
        });


        //enable reading of the body for the post request
        router.route("/api/articles").handler(BodyHandler.create());
        router.post("/api/articles").handler(this::addOne);

        router.get("/api/articles").handler(this::getAll);
        router.delete("/api/articles/:id").handler(this::deleteOne);
        router.get("/api/articles/:id").handler(this::getOne);
        router.put("/api/articles/:id").handler(this::updateOne);
        router.route("/assets/*").handler(StaticHandler.create("assets"));

        ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(
                config -> {
                    if (config.failed()) {
                        future.fail(config.cause());
                    } else {

                        jdbc = JDBCClient.createShared(vertx, config.result(),"read_list");
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

    private void createSomeData() {
        Article article1 = new Article(
                "Fallacies of distributed computing",
                "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");
        readingList.put(article1.getId(), article1);
        Article article2 = new Article(
                "Reactive Manifesto",
                "https://www.reactivemanifesto.org/");
        readingList.put(article2.getId(), article2);
    }

    private void getAll(RoutingContext rc) {
        rc.response()
                .putHeader("content-type",
                        "application/json; charset=utf-8")
                .end(Json.encodePrettily(readingList.values()));
    }

    private void addOne(RoutingContext rc){
        Article article = rc.getBodyAsJson().mapTo(Article.class);
        readingList.put(article.getId(), article);
        rc.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(article));
    }

    private void deleteOne(RoutingContext rc){
        String id = rc.request().getParam("id");
        try {
            Integer idAsInteger = Integer.valueOf(id);
            readingList.remove(idAsInteger);
            rc.response().setStatusCode(204).end();
        } catch (NumberFormatException e){
            rc.response().setStatusCode(400).end();
        }
    }

    private void getOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        try {
            Integer idAsInteger = Integer.valueOf(id);
            Article article = (Article) readingList.get(idAsInteger);
            if (article == null) {
                routingContext.response().setStatusCode(404).end();
            } else {
                routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(article));
            }
        } catch (NumberFormatException e) {
            routingContext.response().setStatusCode(400).end();
        }
    }

    private void updateOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        try {
            Integer idAsInteger = Integer.valueOf(id);
            Article article = (Article) readingList.get(idAsInteger);
            if (article == null) {
                routingContext.response().setStatusCode(404).end();
            } else {
                JsonObject body = routingContext.getBodyAsJson();
                article.setTitle(body.getString("title")).setUrl(body.getString("url"));
                readingList.put(idAsInteger, article);
                routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(article));
            }
        } catch (NumberFormatException e) {
            routingContext.response().setStatusCode(400).end();
        }

    }

    private Future<SQLConnection> connect(){
        Future<SQLConnection> future = Future.future();
        jdbc.getConnection(ar ->
                /*

                    if (ar.failed()) {
                      future.failed(ar.cause());
                    } else {
                      future.complete(ar.result());
                    }


                    future.handle(ar.map(...
                    is the shortcut for the above code. Just shorter.... cool
                 */
                future.handle(ar.map(connection ->
                        connection.setOptions(
                                new SQLOptions().setAutoGeneratedKeys(true))
                ))
        );
        return future;
    }


    private Future<SQLConnection> createTableIfNeeded(SQLConnection connection){
        Future<SQLConnection> future = Future.future();
        vertx.fileSystem().readFile("tables.sql", ar -> {
            if (ar.failed()){
                future.fail(ar.cause());
            } else {
                connection.execute(ar.result().toString(),
                        ar2 -> future.handle(ar2.map(connection))
                );
            }
        });
        return  future;
    }

}

