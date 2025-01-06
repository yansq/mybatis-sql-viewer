package io.github.linyimin.plugin.sql;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.intellij.openapi.application.ApplicationManager;
import io.github.linyimin.plugin.configuration.DatasourceConfigComponent;
import io.github.linyimin.plugin.constant.Constant;

import java.sql.Connection;
import java.util.Properties;

/**
 * @author banzhe
 * @date 2022/11/26 21:48
 **/
public class DatasourceComponent {
    private DruidDataSource dataSource;

    public Connection getConnection() throws Exception {
        if (dataSource == null || dataSource.isClosed()) {
            dataSource = createDatasource();
        }

        return dataSource.getConnection(3000);
    }

    public void updateDatasource() {
        this.close();
        try {
            dataSource = createDatasource();
        } catch (Exception ignored) {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    public void close() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
        } catch (Exception ignored) {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    private DruidDataSource createDatasource() throws Exception {
        DatasourceConfigComponent component = ApplicationManager.getApplication()
                .getComponent(DatasourceConfigComponent.class);

        String type = component.getType();
        if ("oracle".equals(type)) {
            return createOracleDatasource(component);
        } else {
            return createMysqlDatasource(component);
        }
    }

    private DruidDataSource createMysqlDatasource(DatasourceConfigComponent component) throws Exception {
        String url = String.format(
                Constant.MYSQL_DATABASE_URL_TEMPLATE, component.getHost(), component.getPort(), component.getDatabase()
        );
        Properties properties = new Properties();
        properties.put(DruidDataSourceFactory.PROP_URL, url);
        properties.put(DruidDataSourceFactory.PROP_USERNAME, component.getUser());
        properties.put(DruidDataSourceFactory.PROP_PASSWORD, component.getPassword());
        properties.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, "com.mysql.cj.jdbc.Driver");
        properties.put(DruidDataSourceFactory.PROP_MINIDLE, "5");
        properties.put(DruidDataSourceFactory.PROP_MAXACTIVE, "10");
        properties.put(DruidDataSourceFactory.PROP_MAXWAIT, "5000");

        DruidDataSource dataSource = (DruidDataSource) DruidDataSourceFactory.createDataSource(properties);
        dataSource.setBreakAfterAcquireFailure(true);
        return (DruidDataSource) DruidDataSourceFactory.createDataSource(properties);
    }

    private DruidDataSource createOracleDatasource(DatasourceConfigComponent component) throws Exception {
        String url = String.format(
                Constant.ORACLE_DATABASE_URL_TEMPLATE, component.getHost(), component.getPort(), component.getDatabase()
        );
        Properties properties = new Properties();
        properties.put(DruidDataSourceFactory.PROP_URL, url);
        properties.put(DruidDataSourceFactory.PROP_USERNAME, component.getUser());
        properties.put(DruidDataSourceFactory.PROP_PASSWORD, component.getPassword());
        properties.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, "oracle.jdbc.driver.OracleDriver");
        properties.put(DruidDataSourceFactory.PROP_MINIDLE, "5");
        properties.put(DruidDataSourceFactory.PROP_MAXACTIVE, "10");
        properties.put(DruidDataSourceFactory.PROP_MAXWAIT, "5000");

        DruidDataSource dataSource = (DruidDataSource) DruidDataSourceFactory.createDataSource(properties);
        dataSource.setBreakAfterAcquireFailure(true);
        return dataSource;
    }
}
