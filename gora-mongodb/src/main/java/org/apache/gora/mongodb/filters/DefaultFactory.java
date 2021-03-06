/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gora.mongodb.filters;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.gora.filter.*;
import org.apache.gora.mongodb.store.MongoMapping;
import org.apache.gora.mongodb.store.MongoStore;
import org.apache.gora.persistency.impl.PersistentBase;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

public class DefaultFactory<K, T extends PersistentBase> extends
    BaseFactory<K, T> {
  private static final Log LOG = LogFactory.getLog(DefaultFactory.class);

  @Override
  public List<String> getSupportedFilters() {
    List<String> filters = new ArrayList<String>();
    filters.add(SingleFieldValueFilter.class.getCanonicalName());
    filters.add(MapFieldValueFilter.class.getCanonicalName());
    filters.add(FilterList.class.getCanonicalName());
    return filters;
  }

  @Override
  public DBObject createFilter(final Filter<K, T> filter,
      final MongoStore<K, T> store) {

    if (filter instanceof FilterList) {
      FilterList<K, T> filterList = (FilterList<K, T>) filter;
      return transformListFilter(filterList, store);
    } else if (filter instanceof SingleFieldValueFilter) {
      SingleFieldValueFilter<K, T> fieldFilter = (SingleFieldValueFilter<K, T>) filter;
      return transformFieldFilter(fieldFilter, store);
    } else if (filter instanceof MapFieldValueFilter) {
      MapFieldValueFilter<K, T> mapFilter = (MapFieldValueFilter<K, T>) filter;
      return transformMapFilter(mapFilter, store);
    } else {
      LOG.warn("MongoDB remote filter not yet implemented for "
          + filter.getClass().getCanonicalName());
      return null;
    }
  }

  protected DBObject transformListFilter(final FilterList<K, T> filterList,
      final MongoStore<K, T> store) {
    BasicDBObject query = new BasicDBObject();
    for (Filter<K, T> filter : filterList.getFilters()) {
      boolean succeeded = getFilterUtil().setFilter(query, filter, store);
      if (!succeeded) {
        return null;
      }
    }
    return query;
  }

  protected DBObject transformFieldFilter(
      final SingleFieldValueFilter<K, T> fieldFilter,
      final MongoStore<K, T> store) {
    MongoMapping mapping = store.getMapping();
    String dbFieldName = mapping.getDocumentField(fieldFilter.getFieldName());

    FilterOp filterOp = fieldFilter.getFilterOp();
    List<Object> operands = fieldFilter.getOperands();

    QueryBuilder builder = QueryBuilder.start(dbFieldName);
    builder = appendToBuilder(builder, filterOp, operands);
    if (!fieldFilter.isFilterIfMissing()) {
      // If false, the find query will pass if the column is not found.
      DBObject notExist = QueryBuilder.start(dbFieldName).exists(false).get();
      builder = QueryBuilder.start().or(notExist, builder.get());
    }
    return builder.get();
  }

  protected DBObject transformMapFilter(
      final MapFieldValueFilter<K, T> mapFilter, final MongoStore<K, T> store) {
    MongoMapping mapping = store.getMapping();
    String dbFieldName = mapping.getDocumentField(mapFilter.getFieldName())
        + "." + store.encodeFieldKey(mapFilter.getMapKey().toString());

    FilterOp filterOp = mapFilter.getFilterOp();
    List<Object> operands = mapFilter.getOperands();

    QueryBuilder builder = QueryBuilder.start(dbFieldName);
    builder = appendToBuilder(builder, filterOp, operands);
    if (!mapFilter.isFilterIfMissing()) {
      // If false, the find query will pass if the column is not found.
      DBObject notExist = QueryBuilder.start(dbFieldName).exists(false).get();
      builder = QueryBuilder.start().or(notExist, builder.get());
    }
    return builder.get();
  }

  protected QueryBuilder appendToBuilder(final QueryBuilder builder,
      final FilterOp filterOp, final List<Object> operands) {
    switch (filterOp) {
    case EQUALS:
      if (operands.size() == 1) {
        builder.is(operands.iterator().next());
      } else {
        builder.in(operands);
      }
      break;
    case NOT_EQUALS:
      if (operands.size() == 1) {
        builder.notEquals(operands.iterator().next());
      } else {
        builder.notIn(operands);
      }
      break;
    case LESS:
      if (operands.size() == 1) {
        builder.lessThan(operands.iterator().next());
      } else {
        throw new IllegalArgumentException("Only support operands with multiple values for FilterOp EQUALS / NOT_EQUALS !");
      }
      break;
    case LESS_OR_EQUAL:
      if (operands.size() == 1) {
        builder.lessThanEquals(operands.iterator().next());
      } else {
        throw new IllegalArgumentException("Only support operands with multiple values for FilterOp EQUALS / NOT_EQUALS !");
      }
      break;
    case GREATER:
      if (operands.size() == 1) {
        builder.greaterThan(operands.iterator().next());
      } else {
        throw new IllegalArgumentException("Only support operands with multiple values for FilterOp EQUALS / NOT_EQUALS !");
      }
      break;
    case GREATER_OR_EQUAL:
      if (operands.size() == 1) {
        builder.greaterThanEquals(operands.iterator().next());
      } else {
        throw new IllegalArgumentException("Only support operands with multiple values for FilterOp EQUALS / NOT_EQUALS !");
      }
      break;
    default:
      throw new IllegalArgumentException(filterOp
          + " no MongoDB equivalent yet");
    }
    return builder;
  }

}
