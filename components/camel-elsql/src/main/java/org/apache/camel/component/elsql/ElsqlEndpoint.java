/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.elsql;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.opengamma.elsql.ElSql;
import com.opengamma.elsql.ElSqlConfig;
import com.opengamma.elsql.SpringSqlParams;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sql.DefaultSqlEndpoint;
import org.apache.camel.component.sql.SqlPrepareStatementStrategy;
import org.apache.camel.component.sql.SqlProcessingStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * The elsql component is an extension to the existing SQL Component that uses ElSql to define the SQL queries.
 */
@UriEndpoint(scheme = "elsql", title = "ElSQL", syntax = "elsql:elsqlName:resourceUri", consumerClass = ElsqlConsumer.class, label = "database,sql")
public class ElsqlEndpoint extends DefaultSqlEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ElsqlEndpoint.class);

    private ElSql elSql;
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    private DataSource dataSource;

    @UriPath
    @Metadata(required = "true")
    private String elsqlName;
    @UriPath
    private String resourceUri;
    @UriParam
    private ElSqlDatabaseVendor databaseVendor;
    @UriParam
    private ElSqlConfig elSqlConfig;

    public ElsqlEndpoint(String uri, Component component, NamedParameterJdbcTemplate namedJdbcTemplate, DataSource dataSource,
                         String elsqlName, String resourceUri) {
        super(uri, component, null);
        this.elsqlName = elsqlName;
        this.resourceUri = resourceUri;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SqlProcessingStrategy proStrategy = new ElsqlSqlProcessingStrategy(elSql);
        SqlPrepareStatementStrategy preStategy = new ElsqlSqlPrepareStatementStrategy();

        final SqlParameterSource param = new EmptySqlParameterSource();
        final String sql = elSql.getSql(elsqlName, new SpringSqlParams(param));
        LOG.debug("ElsqlConsumer @{} using sql: {}", elsqlName, sql);

        ElsqlConsumer consumer = new ElsqlConsumer(this, processor, namedJdbcTemplate, sql, param, preStategy, proStrategy);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setOnConsumeFailed(getOnConsumeFailed());
        consumer.setOnConsumeBatchComplete(getOnConsumeBatchComplete());
        consumer.setBreakBatchOnConsumeFail(isBreakBatchOnConsumeFail());
        consumer.setExpectedUpdateCount(getExpectedUpdateCount());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        ElsqlProducer result = new ElsqlProducer(this, elSql, elsqlName, namedJdbcTemplate, dataSource);
        return result;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(resourceUri, "resourceUri", this);

        if (elSqlConfig == null && databaseVendor != null) {
            elSqlConfig = databaseVendor.asElSqlConfig();
        } else if (elSqlConfig == null) {
            elSqlConfig = ElSqlDatabaseVendor.Default.asElSqlConfig();
        }

        // there can be multiple resources
        // so we have all this lovely code to turn that into an URL[]
        List<URL> list = new ArrayList<URL>();
        Iterable it = ObjectHelper.createIterable(resourceUri);
        for (Object path : it) {
            URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), path.toString());
            list.add(url);
        }
        URL[] urls = list.toArray(new URL[list.size()]);
        elSql = ElSql.parse(elSqlConfig, urls);
    }

    /**
     * The name of the elsql to use (is @NAMED in the elsql file)
     */
    public String getElsqlName() {
        return elsqlName;
    }

    public ElSqlDatabaseVendor getDatabaseVendor() {
        return databaseVendor;
    }

    /**
     * To use a vendor specific {@link com.opengamma.elsql.ElSqlConfig}
     */
    public void setDatabaseVendor(ElSqlDatabaseVendor databaseVendor) {
        this.databaseVendor = databaseVendor;
    }

    public ElSqlConfig getElSqlConfig() {
        return elSqlConfig;
    }

    /**
     * To use a specific configured ElSqlConfig. It may be better to use the <tt>databaseVendor</tt> option instead.
     */
    public void setElSqlConfig(ElSqlConfig elSqlConfig) {
        this.elSqlConfig = elSqlConfig;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * The resource file which contains the elsql SQL statements to use. You can specify multiple resources separated by comma.
     * The resources are loaded on the classpath by default, you can prefix with <tt>file:</tt> to load from file system.
     * Notice you can set this option on the component and then you do not have to configure this on the endpoint.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }
}
