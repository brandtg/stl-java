/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>
 *   http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 *
 * <p>
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * </p>
 */

package com.github.brandtg;

/**
 * The STL decomposition of a time series.
 *
 * <p>
 * getData() == getTrend() + getSeasonal() + getRemainder()
 * </p>
 */
public class StlResult {
  private final double[] times;
  private final double[] series;
  private final double[] trend;
  private final double[] seasonal;
  private final double[] remainder;

  /**
   * Constructs the result of running {@link StlDecomposition}.
   *
   * <p>
   *   Wraps the original times and series data, and adds the trend, seasonal, and remainder.
   * </p>
   */
  public StlResult(double[] times,
                   double[] series,
                   double[] trend,
                   double[] seasonal,
                   double[] remainder) {
    this.times = times;
    this.series = series;
    this.trend = trend;
    this.seasonal = seasonal;
    this.remainder = remainder;
  }

  public double[] getTimes() {
    return times;
  }

  public double[] getSeries() {
    return series;
  }

  public double[] getTrend() {
    return trend;
  }

  public double[] getSeasonal() {
    return seasonal;
  }

  public double[] getRemainder() {
    return remainder;
  }
}
