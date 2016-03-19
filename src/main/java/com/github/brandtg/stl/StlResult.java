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

/**
 * The STL decomposition of a time series.
 *
 * <p>
 *  getData() == getTrend() + getSeasonal() + getRemainder()
 * </p>
 */
public class StlResult {
  /** The input times array, for convenience. */
  private final double[] times;
  /** The input series, for convenience. */
  private final double[] series;
  /** The trend component of series. */
  private final double[] trend;
  /** The seasonal component of series. */
  private final double[] seasonal;
  /** The remainder component of series */
  private final double[] remainder;

  /**
   * Constructed by running {@link StlDecomposition}.
   *
   * @param times
   *  The input times array.
   * @param series
   *  The input series.
   * @param trend
   *  The output trend component of series.
   * @param seasonal
   *  The output seasonal component of series.
   * @param remainder
   *  The output remainder component of series.
   */
  protected StlResult(double[] times,
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

  /**
   * @return The input times array, for convenience.
   */
  public double[] getTimes() {
    return times;
  }

  /**
   * @return The input series array, for convenience.
   */
  public double[] getSeries() {
    return series;
  }

  /**
   * @return The trend component of series.
   */
  public double[] getTrend() {
    return trend;
  }

  /**
   * @return The seasonal component of series.
   */
  public double[] getSeasonal() {
    return seasonal;
  }

  /**
   * @return The remainder component of series.
   */
  public double[] getRemainder() {
    return remainder;
  }
}
