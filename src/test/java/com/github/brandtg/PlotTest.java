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

    final StlConfig config = new StlConfig();

    config.setNumberOfObservations(12);
    config.setNumberOfDataPoints(ts.length);

    final StlDecomposition stl = new StlDecomposition(config);
    final StlResult res = stl.decompose(ts, ys);
    StlPlotter.plot(res);
  }
}
