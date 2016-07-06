/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.jdbc.impl.actions;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;

import java.sql.*;
import java.sql.ResultSet;
import java.util.Collections;

/**
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class JDBCCallable extends AbstractJDBCAction<io.vertx.ext.sql.ResultSet> {

  private final String sql;
  private final JsonArray in;
  private final JsonArray out;
  private final int timeout;

  public JDBCCallable(Vertx vertx, JDBCStatementHelper helper, Connection connection, WorkerExecutor exec, int timeout, String sql, JsonArray in, JsonArray out) {
    super(vertx, helper, connection, exec);
    this.sql = sql;
    this.in = in;
    this.out = out;
    this.timeout = timeout;
  }

  @Override
  protected io.vertx.ext.sql.ResultSet execute() throws SQLException {
    try (CallableStatement statement = conn.prepareCall(sql)) {
      if (timeout >= 0) {
        statement.setQueryTimeout(timeout);
      }

      helper.fillStatement(statement, in, out);

      boolean retResult = statement.execute();
      boolean outResult = out != null && out.size() > 0;

      if (retResult) {
        // normal return only
        try (ResultSet rs = statement.getResultSet()) {
          if (outResult) {
            // add the registered outputs
            return helper.asList(rs).setOutput(convertOutputs(statement));
          } else {
            return helper.asList(rs);
          }
        }
      } else {
        if (outResult) {
          // only outputs are available
          return new io.vertx.ext.sql.ResultSet(Collections.emptyList(), Collections.emptyList()).setOutput(convertOutputs(statement));
        }
      }

      // no return
      return null;
    }
  }

  private JsonArray convertOutputs(CallableStatement statement) throws SQLException {
    JsonArray result = new JsonArray();

    for (int i = 0; i < out.size(); i++) {
      Object var = out.getValue(i);

      if (var != null) {
        Object value = statement.getObject(i + 1);
        if (value == null) {
          result.addNull();
        } else if (value instanceof ResultSet) {
          result.add(helper.asList((ResultSet) value));
        } else {
          result.add(helper.convertSqlValue(value));
        }
      } else {
        result.addNull();
      }
    }

    return result;
  }

  @Override
  protected String name() {
    return "callable";
  }
}
