package so.glad.test.tomcat;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.Servlet;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import so.glad.test.Environment;

/**
 * @author Cartoon
 * on 2015/3/12.
 */
public class TomcatEnvironment implements Environment{
    private static final Logger logger = LoggerFactory.getLogger(TomcatEnvironment.class);

    private int port = 9090;
    private String context = "test";

    private Tomcat tomcat;
    private List<ServletWrapper> servletWrappers = new ArrayList<>();

    private boolean await = false;
    private boolean https = false;

    public void addServlet(Servlet servlet, String pattern) {
        servletWrappers.add(new ServletWrapper(servlet, pattern));
    }

    public TomcatEnvironment await(boolean await) {
        this.await = await;
        return this;
    }

    public TomcatEnvironment https(boolean https) {
        this.https = https;
        return this;
    }

    @Override
    public void startup() {
        logger.debug("Start tomcat with port[{}] and context [{}]", this.port, this.context);
        tomcat = new Tomcat();
        tomcat.setPort(port);

        if (https) {
            Connector httpsConnector = new Connector();
            httpsConnector.setScheme("https");
            httpsConnector.setSecure(true);
            httpsConnector.setPort(port + 1);

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

        AprLifecycleListener aprLifecycleListener = new AprLifecycleListener();
        StandardServer server = (StandardServer) tomcat.getServer();
        server.addLifecycleListener(aprLifecycleListener);

        Context context = tomcat.addContext("/" + this.context, new File("").getAbsolutePath());

        Set<String> servletNames = new HashSet<>();
        for (ServletWrapper servletWrapper : servletWrappers) {
            String servletName = generateServletName(servletWrapper.servlet);
            while (servletNames.contains(servletName)) {
                servletName = generateServletName(servletWrapper.servlet);
            }
            servletNames.add(servletName);
            tomcat.addServlet("/" + this.context, servletName, servletWrapper.servlet);
            context.addServletMapping(servletWrapper.pattern, servletName);
        }

        try {
            TimeUnit.MILLISECONDS.sleep(200L);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        try {
            tomcat.start();
            if (await) {
                tomcat.getServer().await();
            }
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }
    }

    private String generateServletName(Servlet servlet) {
        String prefix = servlet.getClass().getName();
        String suffix = "_" + new Random().nextInt(64);
        return prefix + suffix;
    }

    public void destroy() {
        if (tomcat == null) {
            return;
        }

        try {
            tomcat.stop();
        } catch (LifecycleException e) {
            throw new RuntimeException("tomcat stop failed", e);
        }
        try {
            tomcat.destroy();
        } catch (LifecycleException e) {
            throw new RuntimeException("tomcat destroy failed", e);
        }
    }

    public String getAbsoluteURL(String uri) {
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return "http://localhost:" + this.port + "/" + this.context + uri;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setContext(String context) {
        this.context = context;
    }

    private static class ServletWrapper {

        Servlet servlet;
        String pattern;

        ServletWrapper(Servlet servlet, String pattern) {
            this.servlet = servlet;
            this.pattern = pattern;
        }
    }
}
