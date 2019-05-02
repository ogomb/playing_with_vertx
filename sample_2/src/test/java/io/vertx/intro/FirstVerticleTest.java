package io.vertx.intro;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class FirstVerticleTest {

    private Vertx vertx;
    private int port = 8081;

        /*
        private int port = 8081;

        picking a random port if specifying one is problematic
        but this should
        ***** NOT *******
        be done in
        production

            ServerSocket socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();



            A good option is create a json file that vert.x will read the configurations
        */

    @Before
    public void setUp(TestContext context){
        vertx = Vertx.vertx();
        //create deployment options with chosen port
        DeploymentOptions options = new DeploymentOptions()
                .setConfig(new JsonObject().put("HTTP_PORT", port));

        //deploy the verticle with the deployment options

        vertx.deployVerticle(FirstVerticle.class.getName(),
                options, context.asyncAssertSuccess());
    }


    @After
    public void tearDown(TestContext context){
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testApplication(TestContext context){
        final Async async = context.async();

        vertx.createHttpClient().getNow(port, "localhost", "/", response -> {
                    response.handler(body -> {
                        context.assertTrue(body.toString()
                                .contains("Hi"));
                        async.complete();
                    });
                });
    }
}
