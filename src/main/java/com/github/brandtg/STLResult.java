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

import java.awt.Color;
import java.awt.GradientPaint;
import java.lang.reflect.Constructor;
import java.util.Date;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.ClusteredXYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

/**
 * The STL decomposition of a time series.
 *
 * <p>
 * getData() == getTrend() + getSeasonal() + getRemainder()
 * </p>
 */
public class STLResult {
    private final long[] times;
    private final double[] series;
    private final double[] trend;
    private final double[] seasonal;
    private final double[] remainder;

    public STLResult(final long[] times, final double[] series, final double[] trend, final double[] seasonal, final double[] remainder) {
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

    public void plot() {
        this.plot("Seasonal Decomposition");
    }

    public void plot(final String title) {
        this.plot(title, Minute.class);
    }

    public void plot(final String title, final Class<?> timePeriod) {
        final ResultsPlot plot = new ResultsPlot(title, timePeriod);
        plot.pack();
        RefineryUtilities.centerFrameOnScreen(plot);
        plot.setVisible(true);
    }

    private class ResultsPlot extends ApplicationFrame {

        private static final long serialVersionUID = 1L;
        private final JFreeChart chart;
        private final ChartPanel chartPanel;
        private final String title;
        private final Class<?> timePeriod;

        public ResultsPlot(final String title, final Class<?> timePeriod) {
            super(title);

            this.timePeriod = timePeriod;
            this.title = title;

            this.chart = createChart();
            this.chart.removeLegend();

            this.chartPanel = new ChartPanel(chart, true, true, true, false, true);
            this.chartPanel.setPreferredSize(new java.awt.Dimension(1000, 500));

            setContentPane(this.chartPanel);
        }

        private JFreeChart createChart() {

            final CombinedDomainXYPlot plot = new CombinedDomainXYPlot(new DateAxis("Time"));
            final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            final ClusteredXYBarRenderer barRenderer = new ClusteredXYBarRenderer();
            final GradientPaint black = new GradientPaint(0.0f, 0.0f, Color.black, 0.0f, 0.0f, Color.black);

            final TimeSeries seriests = new TimeSeries("Series");
            final TimeSeries seasonalts = new TimeSeries("Seasonal");
            final TimeSeries trendts = new TimeSeries("Trend");
            final TimeSeries remainderts = new TimeSeries("Remainder");

            final TimeSeries[] tsArray = new TimeSeries[] { seriests, seasonalts, trendts };
            final String[] labels = new String[] { "Series", "Seasonal", "Trend" };

            final Constructor<?> cons;
            try {
                cons = this.timePeriod.getConstructor(Date.class);
                for (int i = 0; i < series.length; i++) {
                    final Date d = new Date(times[i]);
                    seriests.add((RegularTimePeriod) cons.newInstance(d), series[i]);
                    seasonalts.add(new Minute(d), seasonal[i]);
                    trendts.add(new Minute(d), trend[i]);
                    remainderts.add(new Minute(d), remainder[i]);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }

            plot.setGap(10.0);
            renderer.setSeriesPaint(0, black);
            barRenderer.setSeriesPaint(0, black);
            plot.setOrientation(PlotOrientation.VERTICAL);

            for (int i = 0; i < tsArray.length; i++) {
                final XYDataset ts = new TimeSeriesCollection(tsArray[i]);
                final XYPlot p = new XYPlot(ts, new DateAxis(labels[i]), new NumberAxis(labels[i]), renderer);
                plot.add(p);
            }

            final XYDataset rts = new TimeSeriesCollection(remainderts);
            final XYDataset sts = new TimeSeriesCollection(seriests);
            final XYDataset tts = new TimeSeriesCollection(trendts);
            final XYPlot rplot = new XYPlot(rts, new DateAxis(), new NumberAxis("Remainder"), barRenderer);
            final XYPlot seriesAndTrend = new XYPlot(sts, new DateAxis(), new NumberAxis("S & T"), renderer);

            seriesAndTrend.setDataset(1, tts);
            seriesAndTrend.setRenderer(1, renderer);

            plot.add(rplot);
            plot.add(seriesAndTrend);

            return new JFreeChart(this.title, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        }
    }
}
