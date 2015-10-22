package com.github.brandtg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;

public class STLRunner {
  /**
   * Accepts an input JSON file containing raw time series, e.g.
   *
   * <pre>
   *   {
   *     "times": [1,2,3,4,...],
   *     "series": [100,200,300,400,...]
   *   }
   * </pre>
   *
   * And outputs the STL de-trended data:
   *
   * <pre>
   *   {
   *     "times": [1,2,3,4,...],
   *     "series": [100,200,300,400,...],
   *     "seasonal": [...],
   *     "trend": [...],
   *     "remainder": [...]
   *   }
   * </pre>
   *
   * n.b. This isn't really meant to be used on anything besides src/test/resources/sample-timeseries.json,
   * just an example.
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("usage: input_file output_file");
      System.exit(1);
    }

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode tree = objectMapper.readTree(new File(args[0]));
    int n = tree.get("times").size();
    long[] tsLong = new long[n];
    double[] ts = new double[n];
    double[] ys = new double[n];

    for (int i = 0; i < n; i++) {
      tsLong[i] =  tree.get("times").get(i).asLong();
      ts[i] = tree.get("times").get(i).asDouble();
      ys[i] = tree.get("series").get(i).asDouble();
    }

    // This configuration was chosen to work with monthly data over 20 years
    STLConfig config = new STLConfig();
    config.setNumberOfObservations(12);
    config.setNumberOfInnerLoopPasses(10);
    config.setNumberOfRobustnessIterations(1);
    config.setSeasonalComponentBandwidth(0.75);
    config.setLowPassFilterBandwidth(0.30);
    config.setTrendComponentBandwidth(0.10);
    config.setNumberOfDataPoints(ts.length);
    STLDecomposition stl = new STLDecomposition(config);
    STLResult res = stl.decompose(tsLong, ys);

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(args[1]), res);
  }
}
