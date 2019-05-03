package io.vertx.intro;


import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLOptions;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static io.vertx.intro.ActionHelper.*;

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
    public void start(Future fut) throws Exception {

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
        ConfigRetriever.getConfigAsFuture(retriever)
                .compose(config -> {
                    jdbc = JDBCClient.createShared(vertx, config, "read-list");

                    return connect()
                            .compose(connection -> {
                                Future<Void> future = Future.future();
                                createTableIfNeeded(connection)
                                        .compose(this::createSomeDataIfNone)
                                        .setHandler(x -> {
                                            connection.close();
                                            future.handle(x.mapEmpty());
                                        });
                                return future;
                            })
                            .compose(v -> createHttpServer(config, router));

                })
                .setHandler(fut);
    }

    private Future<Void> createHttpServer(JsonObject config, Router router) {
        Future<Void> future = Future.future();
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                        config.getInteger("HTTP_PORT", 8080),
                        res -> future.handle(res.mapEmpty())
                );
        return future;
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
       connect()
               .compose(this::query)
               .setHandler(ok(rc));
    }

    private void addOne(RoutingContext rc){
        Article article = rc.getBodyAsJson().mapTo(Article.class);
        connect()
                .compose(connection -> insert(connection, article, true))
                .setHandler(created(rc));
    }

    private void deleteOne(RoutingContext rc){
        String id = rc.request().getParam("id");
        connect()
                .compose(connection -> delete(connection, id))
                .setHandler(noContent(rc));
    }

    private void getOne(RoutingContext rc) {
        String id = rc.pathParam("id");
        connect()
                .compose(connection -> queryOne(connection, id))
                .setHandler(ok(rc));
    }
    private void updateOne(RoutingContext rc) {
        String id = rc.request().getParam("id");
        Article article = rc.getBodyAsJson().mapTo(Article.class);
        connect()
                .compose(connection ->  update(connection, id, article))
                .setHandler(noContent(rc));
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


    private Future<SQLConnection> createSomeDataIfNone(SQLConnection connection) {
        Future<SQLConnection> future = Future.future();
        connection.query("SELECT * FROM Articles", select -> {
            if (select.failed()) {
                future.fail(select.cause());
            } else {
                if (select.result().getResults().isEmpty()) {
                    Article article1 = new Article("Fallacies of distributed computing",
                            "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");
                    Article article2 = new Article("Reactive Manifesto",
                            "https://www.reactivemanifesto.org/");

                    Future<Article> insertion1 = insert(connection, article1, false);

                    Future<Article> insertion2 = insert(connection, article2, false);

                    CompositeFuture.all(insertion1, insertion2).setHandler(r -> future.handle(r.map(connection)));
                } else {
                    future.complete(connection);
                }
            }
        });
        return future;
    }
    private Future<Article> insert(SQLConnection connection, Article article, boolean closeConnection) {
        Future<Article> future = Future.future();

        String sql = "INSERT INTO Articles (title, url) VALUES (?, ?)";
        connection.updateWithParams(sql, new JsonArray().add(article.getTitle()).add(article.getUrl()),
                ar -> {
                    if (closeConnection) {
                        connection.close();
                    }
                    future.handle(
                            ar.map(res -> new Article(res.getKeys().getLong(0),
                                    article.getTitle(), article.getUrl()))
                    );
                }
        );
        return future;
    }


    private Future<List<Article>> query(SQLConnection connection) {
        Future<List<Article>> future = Future.future();
        connection.query("SELECT * FROM articles", result -> {
            //close the connection inorder for it to be reused
                    connection.close();
                    future.handle(result.map(rs ->
                                    rs.getRows().stream()
                                            .map(Article::new)
                                            .collect(Collectors.toList()))
                    );
                }
        );
        return future;
    }


    private Future<Article> queryOne(SQLConnection connection, String id) {
        Future<Article> future = Future.future();
        String sql = "SELECT * FROM articles WHERE id = ?";
        connection.queryWithParams(sql,
                new JsonArray().add(Integer.valueOf(id)),
                result -> {
                    connection.close();
                    future.handle(result.map(rs -> {
                                List<JsonObject> rows = rs.getRows();
                                if (rows.size() == 0) {
                                    throw new NoSuchElementException(
                                            "No article with id " + id);
                                } else {
                                    JsonObject row = rows.get(0);
                                    return new Article(row);
                                }
                            })
                    );
                });
        return future;
    }

    private Future<Void> update(SQLConnection connection, String id, Article article) {
        Future<Void> future = Future.future();
        String sql = "UPDATE articles SET title = ?, url = ? WHERE id = ?";
        connection.updateWithParams(sql,
                new JsonArray().add(article.getTitle())
                        .add(article.getUrl())
                        .add(Integer.valueOf(id)
                        ), ar -> {
                    connection.close();
                    if (ar.failed()) {
                        future.fail(ar.cause());
                    } else {
                        UpdateResult ur = ar.result();
                        if (ur.getUpdated() == 0) {
                            future.fail(new NoSuchElementException(
                                    "No article with id " + id));
                        } else {
                            future.complete();
                        }
                    }
                });
        return future;
    }

    private Future<Void> delete(SQLConnection connection, String id) {
        Future future = Future.future();
        String sql = "DELETE FROM Articles WHERE id = ?";
        connection.updateWithParams(sql,
                new JsonArray().add(Integer.valueOf(id)),
                ar -> {
                    connection.close();
                    if (ar.failed()) {
                        future.fail(ar.cause());
                    } else {
                        if (ar.result().getUpdated() == 0) {
                            future.fail(
                                    new NoSuchElementException(
                                            "No article with id " + id));
                        } else {
                            future.complete();
                        }
                    }
                });
        return future;
    }
}

