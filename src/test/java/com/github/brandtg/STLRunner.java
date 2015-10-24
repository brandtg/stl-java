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

import java.io.File;
import java.io.FileOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
     * n.b. This isn't really meant to be used on anything besides src/test/resources/sample-timeseries.json, just an example.
     */

    public static void main(final String[] args) throws Exception {
        final File testInput;
        final String output;
        if (args.length != 2) {
            testInput = new File(STLRunner.class.getResource("/sample-timeseries.json").getFile());
            output = "STLRunning-Output.json";
        } else {
            testInput = new File(args[1]);
            output = args[2];
        }
        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode tree = objectMapper.readTree(testInput);
        final int n = tree.get("times").size();
        final long[] tsLong = new long[n];
        final double[] ts = new double[n];
        final double[] ys = new double[n];

        for (int i = 0; i < n; i++) {
            tsLong[i] = tree.get("times").get(i).asLong();
            ts[i] = tree.get("times").get(i).asDouble();
            ys[i] = tree.get("series").get(i).asDouble();
        }

        // This configuration was chosen to work with monthly data over 20 years
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
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new FileOutputStream(output), res);
    }
}
