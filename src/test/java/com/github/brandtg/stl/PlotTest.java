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
package com.github.brandtg.stl;

import java.io.File;

import org.jfree.data.time.Hour;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PlotTest {
  @Test
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

    final StlDecomposition stl = new StlDecomposition(12);
    final StlResult res = stl.decompose(ts, ys);

    final File output = new File("seasonal.png");
    final File hourly = new File("stl-hourly.png");

    output.deleteOnExit();
    hourly.deleteOnExit();

    StlPlotter.plot(res, "New Title", Hour.class, hourly);
    StlPlotter.plot(res, output);
    StlPlotter.plot(res);

    Assert.assertTrue(output.exists());
    Assert.assertTrue(hourly.exists());

    final File exists = new File("stl-decomposition.png");
    exists.deleteOnExit();

    StlPlotter.plot(res, "Test Title");

    Assert.assertTrue(exists.exists());
  }
}
