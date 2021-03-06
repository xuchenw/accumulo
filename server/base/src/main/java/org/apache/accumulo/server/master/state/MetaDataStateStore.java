/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.server.master.state;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.BatchWriterConfig;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.impl.ClientContext;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.metadata.MetadataTable;
import org.apache.accumulo.core.metadata.schema.MetadataSchema;
import org.apache.accumulo.server.AccumuloServerContext;

public class MetaDataStateStore extends TabletStateStore {
  // private static final Logger log = Logger.getLogger(MetaDataStateStore.class);

  private static final int THREADS = 4;
  private static final int LATENCY = 1000;
  private static final int MAX_MEMORY = 200 * 1024 * 1024;

  final protected ClientContext context;
  final protected CurrentState state;
  final private String targetTableName;

  protected MetaDataStateStore(ClientContext context, CurrentState state, String targetTableName) {
    this.context = context;
    this.state = state;
    this.targetTableName = targetTableName;
  }

  public MetaDataStateStore(ClientContext context, CurrentState state) {
    this(context, state, MetadataTable.NAME);
  }

  protected MetaDataStateStore(AccumuloServerContext context, String tableName) {
    this(context, null, tableName);
  }

  public MetaDataStateStore(AccumuloServerContext context) {
    this(context, MetadataTable.NAME);
  }

  @Override
  public ClosableIterator<TabletLocationState> iterator() {
    return new MetaDataTableScanner(context, MetadataSchema.TabletsSection.getRange(), state);
  }

  @Override
  public void setLocations(Collection<Assignment> assignments) throws DistributedStoreException {
    BatchWriter writer = createBatchWriter();
    try {
      for (Assignment assignment : assignments) {
        Mutation m = new Mutation(assignment.tablet.getMetadataEntry());
        assignment.server.putLocation(m);
        assignment.server.clearFutureLocation(m);
        writer.addMutation(m);
      }
    } catch (Exception ex) {
      throw new DistributedStoreException(ex);
    } finally {
      try {
        writer.close();
      } catch (MutationsRejectedException e) {
        throw new DistributedStoreException(e);
      }
    }
  }

  BatchWriter createBatchWriter() {
    try {
      return context.getConnector().createBatchWriter(targetTableName,
          new BatchWriterConfig().setMaxMemory(MAX_MEMORY).setMaxLatency(LATENCY, TimeUnit.MILLISECONDS).setMaxWriteThreads(THREADS));
    } catch (TableNotFoundException e) {
      // ya, I don't think so
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setFutureLocations(Collection<Assignment> assignments) throws DistributedStoreException {
    BatchWriter writer = createBatchWriter();
    try {
      for (Assignment assignment : assignments) {
        Mutation m = new Mutation(assignment.tablet.getMetadataEntry());
        assignment.server.putFutureLocation(m);
        writer.addMutation(m);
      }
    } catch (Exception ex) {
      throw new DistributedStoreException(ex);
    } finally {
      try {
        writer.close();
      } catch (MutationsRejectedException e) {
        throw new DistributedStoreException(e);
      }
    }
  }

  @Override
  public void unassign(Collection<TabletLocationState> tablets) throws DistributedStoreException {

    BatchWriter writer = createBatchWriter();
    try {
      for (TabletLocationState tls : tablets) {
        Mutation m = new Mutation(tls.extent.getMetadataEntry());
        if (tls.current != null) {
          tls.current.clearLocation(m);
        }
        if (tls.future != null) {
          tls.future.clearFutureLocation(m);
        }
        writer.addMutation(m);
      }
    } catch (Exception ex) {
      throw new DistributedStoreException(ex);
    } finally {
      try {
        writer.close();
      } catch (MutationsRejectedException e) {
        throw new DistributedStoreException(e);
      }
    }
  }

  @Override
  public String name() {
    return "Normal Tablets";
  }
}
