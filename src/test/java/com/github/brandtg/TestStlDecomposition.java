package com.github.brandtg;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class TestStlDecomposition {
  private int numDataPoints;
  private int numObservations;
  private StlConfig config;

  @BeforeClass
  public void beforeClass() {
    numDataPoints = 120; // i.e. 10 years
    numObservations = 12; // i.e. monthly
    config = new StlConfig();
    config.setNumberOfObservations(numObservations);
    config.setNumberOfInnerLoopPasses(10);
    config.setNumberOfRobustnessIterations(2);
    config.setNumberOfDataPoints(numDataPoints);
  }

  @DataProvider
  public Object[][] simpleFunctionDataProvider() {
    List<Object[]> data = new ArrayList<>();

    // Common times
    DateTime dateTime = new DateTime();
    double[] ts = new double[numDataPoints];
    for (int i = 0; i < ts.length; i++) {
      ts[i] = dateTime.getMillis();
      dateTime = dateTime.plusMonths(1);
    }

    // 50 * sin(x) + 10
    {
      double[] ys = new double[ts.length];
      for (int i = 0; i < ts.length; i++) {
        ys[i] = 50 * Math.sin(i) + 10;
      }

      // Here we have series oscillating around 10 with increasing amplitude
      double[] expectedTrend = new double[ts.length];
      for (int i = 0; i < ts.length; i++) {
        expectedTrend[i] = 10;
      }

      // TODO: The acceptable error bound is fairly high here, we should figure out how to lower it
      // It appears that a very large seasonal component necessitates some convergence time for the trend,
      // so if we skip two seasons here on each end, we can apply a lower error bound. (For reference,
      // if we skip one season on each end, we must increase error bound to ~0.40)
      data.add(new Object[] { "50 * sin(x) + 10", ts, ys, expectedTrend, 0.20, 2 });
    }

    // sin(x) + 10
    {
      double[] ys = new double[ts.length];
      for (int i = 0; i < ts.length; i++) {
        ys[i] = Math.sin(i) + 10;
      }

      // Here we have series oscillating around 10 with increasing amplitude
      double[] expectedTrend = new double[ts.length];
      for (int i = 0; i < ts.length; i++) {
        expectedTrend[i] = 10;
      }

      // Here we skip only one season on each end, and can apply a fairly low error bound, as the trend
      // component dominates the seasonal component
      data.add(new Object[] { "sin(x) + 10", ts, ys, expectedTrend, 0.05, 1 });
    }

    return data.toArray(new Object[][]{});
  }

  @Test(dataProvider = "simpleFunctionDataProvider")
  public void testSimpleFunctionDecomposition(String name,
                                              double[] ts,
                                              double[] ys,
                                              double[] expectedTrend,
                                              double errorBound,
                                              int seasonsToSkip) {
    StlResult result = new StlDecomposition(config).decompose(ts, ys);

    // Ensure that the expected trend is within errorBound
    int seasonPadding = numObservations * seasonsToSkip;
    double[] trend = result.getTrend();
    for (int i = seasonPadding; i < ts.length - seasonPadding; i++) {
      double error = Math.abs((trend[i] - expectedTrend[i]) / expectedTrend[i]);
      Assert.assertTrue(error < errorBound, String.format(
          "At time %s expected=%f, actual=%f, error=%f, errorBound=%f",
          new DateTime((long) ts[i]), expectedTrend[i], trend[i], error, errorBound));
    }
  }
}
