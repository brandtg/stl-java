/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.brandtg;

import java.io.File;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PlotTest {

  @BeforeClass
  public void setUp() {

  }

  @Test(enabled = false)
  public void testPlot() throws Exception {
    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode tree = objectMapper.readTree(new File(this.getClass().getResource("/sample-timeseries.json").getFile()));
    final int n = tree.get("times").size();
    final double[] ts = new double[n];
    final double[] ys = new double[n];

    for (int i = 0; i < n; i++) {
      ts[i] = tree.get("times").get(i).asDouble();
      ys[i] = tree.get("series").get(i).asDouble();
    }

    final StlConfig config = new StlConfig();

    config.setNumberOfObservations(12);
    config.setNumberOfDataPoints(ts.length);

    final StlDecomposition stl = new StlDecomposition(config);
    final StlResult res = stl.decompose(ts, ys);
    StlPlotter.plot(res);
  }
}
