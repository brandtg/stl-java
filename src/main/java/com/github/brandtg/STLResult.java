/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.brandtg;

/**
 * The STL decomposition of a time series.
 *
 * <p>
 *   getData() == getTrend() + getSeasonal() + getRemainder()
 * </p>
 */
public class STLResult {
  private final long[] times;
  private final double[] series;
  private final double[] trend;
  private final double[] seasonal;
  private final double[] remainder;

  public STLResult(long[] times, double[] series, double[] trend, double[] seasonal, double[] remainder) {
    this.times = times;
    this.series = series;
    this.trend = trend;
    this.seasonal = seasonal;
    this.remainder = remainder;
  }

  public long[] getTimes() {
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
