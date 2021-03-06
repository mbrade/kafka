/*
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
package org.apache.beam.sdk.io.gcp.bigquery;

import com.google.api.services.bigquery.model.TableRow;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.apache.beam.sdk.coders.CoderFactories;
import org.apache.beam.sdk.coders.CoderFactory;
import org.apache.beam.sdk.coders.CoderRegistrar;

/**
 * A {@link CoderRegistrar} for standard types used with {@link BigQueryIO}.
 */
@AutoService(CoderRegistrar.class)
public class BigQueryCoderRegistrar implements CoderRegistrar {
  @Override
  public Map<Class<?>, CoderFactory> getCoderFactoriesToUseForClasses() {
    return ImmutableMap.of(
        TableRow.class, CoderFactories.forCoder(TableRowJsonCoder.of()),
        TableRowInfo.class, CoderFactories.forCoder(TableRowInfoCoder.of()));
  }
}
