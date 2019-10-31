/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
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

package org.jbpm.prediction.pmml;

import org.kie.api.task.model.Task;
import org.kie.internal.task.api.prediction.PredictionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class PMMLRandomForest extends AbstractPMMLBackend {

    public static final String IDENTIFIER = "PMMLRandomForest";

    private static final Logger logger = LoggerFactory.getLogger(PMMLRandomForest.class);
    /**
     * Reads a properties file to be used for service configuration.
     * @param propertiesFilename A String with the properties filename
     * @return A Properties object
     * @throws IOException
     */
    private static Properties readProperties(String propertiesFilename) throws IOException {
        Properties properties = new Properties();

        InputStream inputStream = PMMLRandomForest.class.getClassLoader().getResourceAsStream(propertiesFilename);

        if (inputStream != null) {
            properties.load(inputStream);
        } else {
            throw new FileNotFoundException("Could not find the property file '" + propertiesFilename + "' in the classpath.");
        }

        return properties;
    }
    /**
     * Reads the PMML model configuration from a properties files.
     * "inputs.properties" should contain the input attribute names as keys and (optional) attribute types as values
     * "output.properties" should contain the output attribute name and the confidence threshold
     * "model.properties" should contain the location of the PMML model
     * @return A map of input attributes with the attribute name as key and attribute type as value
     */
    private static PMMLRandomForestConfiguration readConfigurationFromFile() {

        final PMMLRandomForestConfiguration configuration = new PMMLRandomForestConfiguration();

        final List<String> inputFeatures = new ArrayList<>();

        try {

            Properties inputProperties = readProperties("inputs.properties");

            for (Object propertyName : inputProperties.keySet()) {
                inputFeatures.add((String) propertyName);
            }

            configuration.setInputFeatures(inputFeatures);

            Properties outputProperties = readProperties("output.properties");

            configuration.setOutcomeName(outputProperties.getProperty("name"));
            configuration.setConfidenceThreshold(Double.parseDouble(outputProperties.getProperty("confidence_threshold")));

            Properties modelProperties = readProperties("model.properties");
            String pmmlFilename = modelProperties.getProperty("filename");

            configuration.setModelFile(new File(PMMLRandomForest.class.getClassLoader().getResource(pmmlFilename).getFile()));

            return configuration;
        } catch (IOException ex) {
            throw new RuntimeException("Could not create service configuration.");
        }

    }

    public PMMLRandomForest() {
        this(readConfigurationFromFile());
    }

    public PMMLRandomForest(PMMLRandomForestConfiguration configuration) {
        this(configuration.getInputFeatures(), configuration.getOutcomeName(), configuration.getConfidenceThreshold(), configuration.getModelFile());
    }

    public PMMLRandomForest(List<String> inputFeatures,
                            String outputFeatureName,
                            double confidenceThreshold,
                            File pmmlFile) {
        super(inputFeatures, outputFeatureName, confidenceThreshold, pmmlFile);
    }

    /**
     * Returns the processed data (e.g. perform categorisation, etc). If no processing is needed, simply return
     * the original data.
     *
     * @param data A map containing the input data, with attribute names as key and values as values.
     * @return data A map containing the processed data, with attribute names as key and values as values.
     */
    @Override
    protected Map<String, Object> preProcess(Map<String, Object> data) {

        Map<String, Object> preProcessed = new HashMap<>();

        for (String input : data.keySet()) {

            if (input.equals("ActorId")) {
                String strValue = (String) data.get(input);

                int rawValue;

                if (strValue.equals("john")) {
                    rawValue = 0;
                } else {
                    rawValue = 1;
                }

                preProcessed.put(input, rawValue);
            } else {
                preProcessed.put(input, data.get(input));
            }

        }

        return preProcessed;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    /**
     * Returns a model prediction given the input data
     *
     * @param task Human task data
     * @param data A map containing the input attribute names as keys and the attribute values as values.
     * @return A {@link PredictionOutcome} containing the model's prediction for the input data.
     */
    @Override
    public PredictionOutcome predict(Task task, Map<String, Object> data) {
        Map<String, ?> result = evaluate(data);

        Map<String, Object> outcomes = new HashMap<>();
        String predictionStr;

        Double prediction = (Double) result.get(outcomeFeatureName);
        double confidence = Math.max(prediction, Math.abs(1.0 - prediction));
        long predictionInt = Math.round(prediction);

        if (predictionInt == 0) {
            predictionStr = "false";
        } else {
            predictionStr = "true";
        }

        outcomes.put("approved", Boolean.valueOf(predictionStr));
        outcomes.put("confidence", confidence);

        logger.debug(data + ", prediction = " + predictionStr + ", confidence = " + confidence);

        return new PredictionOutcome(confidence, this.confidenceThreshold, outcomes);
    }
}
