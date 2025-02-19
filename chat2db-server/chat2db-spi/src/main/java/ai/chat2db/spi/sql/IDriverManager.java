
package ai.chat2db.spi.sql;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson2.JSON;

import ai.chat2db.server.tools.common.exception.ConnectionException;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.DriverEntry;
import ai.chat2db.spi.util.JdbcJarUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ai.chat2db.spi.util.JdbcJarUtils.getFullPath;

/**
 * @author jipengfei
 * @version : IsolationDriverManager.java
 */
public class IDriverManager {
    private static final Logger log = LoggerFactory.getLogger(IDriverManager.class);
    private static final Map<String, ClassLoader> CLASS_LOADER_MAP = new ConcurrentHashMap();
    private static final Map<String, DriverEntry> DRIVER_ENTRY_MAP = new ConcurrentHashMap();

    public static Connection getConnection(String url, DriverConfig driver) throws SQLException {
        Properties info = new Properties();
        return getConnection(url, info, driver);
    }

    public static Connection getConnection(String url, String user, String password, DriverConfig driver)
        throws SQLException {
        Properties info = new Properties();
        if (user != null) {
            info.put("user", user);
        }

        if (password != null) {
            info.put("password", password);
        }

        return getConnection(url, info, driver);
    }

    public static Connection getConnection(String url, String user, String password, DriverConfig driver,
        Map<String, Object> properties)
        throws SQLException {
        Properties info = new Properties();
        if (StringUtils.isNotEmpty(user)) {
            info.put("user", user);
        }

        if (StringUtils.isNotEmpty(password)) {
            info.put("password", password);
        }
        if (properties != null && !properties.isEmpty()) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    info.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return getConnection(url, info, driver);
    }

    public static Connection getConnection(String url, Properties info, DriverConfig driver)
        throws SQLException {
        if (url == null) {
            throw new SQLException("The url cannot be null", "08001");
        }
        DriverEntry driverEntry = DRIVER_ENTRY_MAP.get(driver.getJdbcDriver());
        if (driverEntry == null) {
            driverEntry = getJDBCDriver(driver);
        }
        try {
            Connection connection = driverEntry.getDriver().connect(url, info);
            if(connection == null){
                throw new SQLException("driver.connect return null , No suitable driver found for url " +url ,"08001");
            }
            return connection;
        } catch (SQLException var7) {
            Connection con = tryConnectionAgain(driverEntry, url, info);
            if (con != null) {
                return con;
            } else {
                throw new SQLException("Cannot create connection (" + var7.getMessage() + ")", "08001",
                    var7);
            }
        }
    }

    private static Connection tryConnectionAgain(DriverEntry driverEntry, String url,
        Properties info) throws SQLException {
        if (url.contains("mysql")) {
            if (!info.containsKey("useSSL")) {
                info.put("useSSL", "false");
            }
            return driverEntry.getDriver().connect(url, info);
        }
        return null;
    }

    private static DriverEntry getJDBCDriver(DriverConfig driver)
        throws SQLException {
        synchronized (driver) {
            try {
                if (DRIVER_ENTRY_MAP.containsKey(driver.getJdbcDriver())) {
                    return DRIVER_ENTRY_MAP.get(driver.getJdbcDriver());
                }
                ClassLoader cl = getClassLoader(driver);
                Driver d = (Driver)cl.loadClass(driver.getJdbcDriverClass()).newInstance();
                DriverEntry driverEntry = DriverEntry.builder().driverConfig(driver).driver(d).build();
                DRIVER_ENTRY_MAP.put(driver.getJdbcDriver(), driverEntry);
                return driverEntry;
            } catch (Exception e) {
                throw new ConnectionException("connection.driver.load.error", null, e);
            }
        }

    }

    public static ClassLoader getClassLoader(DriverConfig driverConfig) throws MalformedURLException {
        String jarPath = driverConfig.getJdbcDriver();
        if (CLASS_LOADER_MAP.containsKey(jarPath)) {
            return CLASS_LOADER_MAP.get(jarPath);
        } else {
            synchronized (jarPath) {
                if (CLASS_LOADER_MAP.containsKey(jarPath)) {
                    return CLASS_LOADER_MAP.get(jarPath);
                }
                String[] jarPaths = jarPath.split(",");
                URL[] urls = new URL[jarPaths.length];
                for (int i = 0; i < jarPaths.length; i++) {
                    File driverFile = new File(getFullPath(jarPaths[i]));
                    urls[i] = driverFile.toURI().toURL();
                }
                //urls[jarPaths.length] = new File(JdbcJarUtils.getFullPath("HikariCP-4.0.3.jar")).toURI().toURL();

                URLClassLoader cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
                log.info("ClassLoader class:{}", cl.hashCode());
                log.info("ClassLoader URLs:{}", JSON.toJSONString(cl.getURLs()));

                try {
                    cl.loadClass(driverConfig.getJdbcDriverClass());
                } catch (Exception e) {
                    //如果报错删除目录重试一次
                    for (int i = 0; i < jarPaths.length; i++) {
                        File driverFile = new File(JdbcJarUtils.getNewFullPath(jarPaths[i]));
                        urls[i] = driverFile.toURI().toURL();
                    }
                    //urls[jarPaths.length] = new File(JdbcJarUtils.getFullPath("HikariCP-4.0.3.jar")).toURI().toURL();
                    cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());

                }
                CLASS_LOADER_MAP.put(jarPath, cl);
                return cl;
            }
        }
    }

}