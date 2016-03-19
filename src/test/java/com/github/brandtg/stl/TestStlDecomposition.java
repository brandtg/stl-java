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

  @BeforeClass
  public void beforeClass() {
    numDataPoints = 120; // i.e. 10 years
    numObservations = 12; // i.e. monthly
  }

  @DataProvider
  public Object[][] simpleFunctionDataProvider() {
    List<Object[]> data = new ArrayList<Object[]>();

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
    StlResult result = new StlDecomposition(numObservations).decompose(ts, ys);

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

  @Test
  public void testSetDifferentConfig() {
    StlDecomposition stl = new StlDecomposition(12);
    Assert.assertEquals(stl.getConfig().getNumberOfRobustnessIterations(), 1);
    stl.getConfig().setNumberOfRobustnessIterations(10);
    Assert.assertEquals(stl.getConfig().getNumberOfRobustnessIterations(), 10);
  }
}
