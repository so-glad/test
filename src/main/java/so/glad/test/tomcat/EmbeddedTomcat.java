package so.glad.test.tomcat;

import java.io.File;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Cartoon
 * on 2015/3/12.
 */
public class EmbeddedTomcat {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedTomcat.class);

    public static void main(String[] args) throws Exception {
        String application = args[0];
        int httpPort = 8081;
        if (args.length == 2) {
            httpPort = Integer.valueOf(args[1]);
        }
        int httpsPort = httpPort + 1;

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(httpPort);

        if (Boolean.getBoolean("https.enable")) {
            Connector httpsConnector = new Connector();
            httpsConnector.setScheme("https");
            httpsConnector.setSecure(true);
            httpsConnector.setPort(httpsPort);

            httpsConnector.setAttribute("keyAlias", "tomcat-embed");

            httpsConnector.setAttribute("keystorePass", "tomcat-embed");
            String keystoreFile;
            if (System.getProperty("keystore") != null) {
                keystoreFile = System.getProperty("keystore") + "/tomcat-embed.key";
            } else if (System.getenv("NUKE_HOME") != null) {
                keystoreFile = System.getenv("NUKE_HOME") + "/tomcat-embed.key";
            } else {
                throw new IllegalStateException("Keystore not found");
            }
            httpsConnector.setAttribute("keystoreFile", keystoreFile);

            httpsConnector.setAttribute("clientAuth", "false");
            httpsConnector.setAttribute("sslProtocol", "TLS");
            httpsConnector.setAttribute("SSLEnabled", true);
            tomcat.getService().addConnector(httpsConnector);
        }

        // Add AprLifecycleListener
        AprLifecycleListener aprLifecycleListener = new AprLifecycleListener();
        StandardServer server = (StandardServer) tomcat.getServer();
        server.addLifecycleListener(aprLifecycleListener);

        tomcat.addWebapp("/" + application, new File("web").getAbsolutePath());

        logger.info("Starting tomcat for " + application + "......");
        tomcat.start();
        server.await();
    }
}
