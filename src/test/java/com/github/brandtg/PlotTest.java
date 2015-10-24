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
    final JsonNode tree = objectMapper
        .readTree(new File(this.getClass().getResource("/sample-timeseries.json").getFile()));
    final int n = tree.get("times").size();
    final long[] tsLong = new long[n];
    final double[] ts = new double[n];
    final double[] ys = new double[n];

    for (int i = 0; i < n; i++) {
      tsLong[i] = tree.get("times").get(i).asLong();
      ts[i] = tree.get("times").get(i).asDouble();
      ys[i] = tree.get("series").get(i).asDouble();
    }

    final STLConfig config = new STLConfig();

    config.setNumberOfObservations(12);
    config.setNumberOfInnerLoopPasses(10);
    config.setNumberOfRobustnessIterations(1);
    config.setSeasonalComponentBandwidth(0.75);
    config.setLowPassFilterBandwidth(0.30);
    config.setTrendComponentBandwidth(0.10);
    config.setNumberOfDataPoints(ts.length);

    final STLDecomposition stl = new STLDecomposition(config);
    final STLResult res = stl.decompose(tsLong, ys);
    res.plot();
  }
}
