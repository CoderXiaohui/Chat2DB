package ai.chat2db.plugin.postgresql;

import java.sql.Connection;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;

public class PostgreSQLDBManage extends DefaultDBManage implements DBManage {
    @Override
    public void connectDatabase(Connection connection, String database) {
        //try {
        //    SQLExecutor.getInstance().execute(connection,"SELECT pg_database_size('"+database+"');");
        //} catch (SQLException e) {
        //    throw new RuntimeException(e);
        //}
    }

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        String url = connectInfo.getUrl();
        String database = connectInfo.getDatabaseName();
        if (database != null && !database.isEmpty()) {
            url = replaceDatabaseInJdbcUrl(url, database);
        }
        connectInfo.setUrl(url);

       return super.getConnection(connectInfo);
    }


    public String replaceDatabaseInJdbcUrl(String url, String newDatabase) {
        // 先在"?"字符处分割字符串，处理查询参数
        String[] urlAndParams = url.split("\\?");
        String urlWithoutParams = urlAndParams[0];

        // 在URL中的"/"字符处分割字符串
        String[] parts = urlWithoutParams.split("/");

        // 取最后一部分，即数据库名，并替换为新的数据库名
        parts[parts.length - 1] = newDatabase;

        // 将修改后的部分重新组合成URL
        String newUrlWithoutParams = String.join("/", parts);

        // 如果存在查询参数，重新添加
        String newUrl = urlAndParams.length > 1 ? newUrlWithoutParams + "?" + urlAndParams[1] : newUrlWithoutParams;

        return newUrl;
    }


    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "DROP TABLE "+ tableName;
        SQLExecutor.getInstance().executeSql(connection,sql, resultSet -> null);
    }

}
