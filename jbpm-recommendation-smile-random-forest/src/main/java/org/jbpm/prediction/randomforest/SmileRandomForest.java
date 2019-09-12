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

package org.jbpm.prediction.randomforest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.kie.api.task.model.Task;
import org.kie.internal.task.api.prediction.PredictionOutcome;
import org.kie.internal.task.api.prediction.PredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.classification.RandomForest;
import smile.data.*;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.*;

public class SmileRandomForest extends AbstractPredictionEngine implements PredictionService {

    public static final String IDENTIFIER = "SMILERandomForest";
    private static final Logger logger = LoggerFactory.getLogger(SmileRandomForest.class);
    private final AttributeDataset dataset;
    private final Map<String, Attribute> smileAttributes;
    private final Attribute outcomeAttribute;
    private final int numAttributes;
    private final int numberTrees;
    protected List<String> attributeNames = new ArrayList<>();
    private RandomForest model = null;
    private Set<String> outcomeSet = new HashSet<>();
    private Map<String, OrderDetails> orderDetails = new HashMap<>();

    public SmileRandomForest() {
        this(readConfigurationFromFile());
    }

    public SmileRandomForest(RandomForestConfiguration configuration) {
        this(configuration.getInputFeatures(),
                configuration.getOutcomeName(),
                configuration.getOutcomeType(),
                configuration.getConfidenceThreshold(),
                configuration.getNumTrees());
    }

    public SmileRandomForest(Map<String, AttributeType> inputFeatures,
                             String outputFeatureName,
                             AttributeType outputFeatureType,
                             double confidenceThreshold,
                             int numberTrees) {
        super(inputFeatures, outputFeatureName, outputFeatureType, confidenceThreshold);
        this.numberTrees = numberTrees;
        smileAttributes = new HashMap<>();
        for (Map.Entry<String, AttributeType> inputFeature : inputFeatures.entrySet()) {
            final String name = inputFeature.getKey();
            final AttributeType type = inputFeature.getValue();
            smileAttributes.put(name, createAttribute(name, type));
            attributeNames.add(name);
        }
        numAttributes = smileAttributes.size();
        outcomeAttribute = createAttribute(outputFeatureName, outputFeatureType);
        dataset = new AttributeDataset("dataset", smileAttributes.values().toArray(new Attribute[numAttributes]), outcomeAttribute);
    }

    /**
     * Reads the random forest configuration from properties files.
     * "inputs.properties" should contain the input attribute names as keys and attribute types as values.
     *
     * @return A map of input attributes with the attribute name as key and attribute type as value.
     */
    private static RandomForestConfiguration readConfigurationFromFile() {

        final RandomForestConfiguration configuration = new RandomForestConfiguration();

        InputStream inputStream = null;
        final Map<String, AttributeType> inputFeatures = new HashMap<>();
        try {
            Properties prop = new Properties();

            inputStream = SmileRandomForest.class.getClassLoader().getResourceAsStream("inputs.properties");

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("Could not find the property file 'inputs.properties' in the classpath.");
            }

            for (Object propertyName : prop.keySet()) {
                inputFeatures.put((String) propertyName, AttributeType.valueOf(prop.getProperty((String) propertyName)));
            }

        } catch (Exception e) {
            logger.error("Exception: " + e);
        }
        configuration.setInputFeatures(inputFeatures);

        try {
            Properties prop = new Properties();

            inputStream = SmileRandomForest.class.getClassLoader().getResourceAsStream("output.properties");

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("Could not find the property file 'output.properties' in the classpath.");
            }

            configuration.setOutcomeName(prop.getProperty("name"));
            configuration.setOutcomeType(AttributeType.valueOf(prop.getProperty("type")));
            configuration.setConfidenceThreshold(Double.parseDouble(prop.getProperty("confidence_threshold")));
            configuration.setNumTrees(Integer.parseInt(prop.getProperty("num_trees")));
        } catch (Exception e) {
            logger.error("Exception: " + e);
        }
        return configuration;
    }

    private static Attribute createAttribute(String name, AttributeType type) {
        if (type == AttributeType.NOMINAL) {
            return new NominalAttribute(name);
        } else if (type == AttributeType.NUMERIC) {
            return new NumericAttribute(name);
        } else {
            return new StringAttribute(name);
        }
    }

    /**
     * Add the data provided as a map to a Smile {@link smile.data.Dataset}.
     *
     * @param data    A map containing the input attribute names as keys and the attribute values as values.
     * @param outcome The value of the outcome (output data).
     */
    public void addData(Map<String, Object> data, Object outcome) {
        final double[] features = new double[numAttributes];
        int i = 0;
        for (String attrName : smileAttributes.keySet()) {
            try {
                features[i] = smileAttributes.get(attrName).valueOf(data.get(attrName).toString());
            } catch (ParseException e) {
                e.printStackTrace();
            }
            i++;
        }
        try {
            final String outcomeStr = outcome.toString();
            outcomeSet.add(outcomeStr);
            dataset.add(features, outcomeAttribute.valueOf(outcomeStr));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        logger.info(dataset.toString());
    }

    /**
     * Build a set of features compatible with Smile's datasets from the map of input data
     *
     * @param data A map containing the input attribute names as keys and the attribute values as values.
     * @return A feature vector as a array of doubles.
     */
    protected double[] buildFeatures(Map<String, Object> data) {
        final double[] features = new double[numAttributes];
        for (int i = 0; i < numAttributes; i++) {
            final String attrName = attributeNames.get(i);
            try {
                features[i] = smileAttributes.get(attrName).valueOf(data.get(attrName).toString());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return features;
    }

    /**
     * Returns the service's identifier
     *
     * @return The service identifier
     */
    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    /**
     * Returns a model prediction given the input data
     *
     * @param task      Human task data
     * @param inputData A map containing the input attribute names as keys and the attribute values as values.
     * @return A {@link PredictionOutcome} containing the model's prediction for the input data.
     */
    @Override
    public PredictionOutcome predict(Task task, Map<String, Object> inputData) {
        Map<String, Object> outcomes = new HashMap<>();

        // the order is initiated
        if (task.getName().equals("Prepare hardware spec")) {
            String orderNumber = (String) inputData.get("orderNumber");
            if (!orderDetails.containsKey(orderNumber)) {
                OrderDetails orderDetail = new OrderDetails();
                orderDetail.brand = task.getPeopleAssignments().getPotentialOwners().get(0).getId();
                orderDetail.requestor = (String) inputData.get("requestor");
                orderDetails.put(orderNumber, orderDetail);
                logger.info("[predict] Creating order number {} from user {} for a {} laptop", orderNumber, orderDetail.requestor, orderDetail.brand);
            }
            return new PredictionOutcome(0.0, this.confidenceThreshold, outcomes);
        }

        // specs being generated
        if (task.getName().equals("Manager approval")) {
            String orderNumber = (String) inputData.get("orderNumber");

            orderDetails.get(orderNumber).manager = task.getPeopleAssignments().getPotentialOwners().get(0).getId();
            OrderDetails o = orderDetails.get(orderNumber);
            logger.info("[predict] Sending order number {} from user {} for a {} laptop from approval with {}", orderNumber, o.requestor, o.brand, o.manager);

            Map<String, Object> processedData = new HashMap<>();

            processedData.put("brand", o.brand);
            processedData.put("manager", o.manager);
            processedData.put("requestor", o.requestor);

            if (outcomeSet.size() >= 2) {
                model = new RandomForest(dataset, this.numberTrees);
                final double[] features = buildFeatures(processedData);
                final double[] posteriori = new double[outcomeSet.size()];
                double prediction = model.predict(features, posteriori);

                String predictionStr = dataset.responseAttribute().toString(prediction);
                outcomes.put(outcomeAttribute.getName(), predictionStr);
                final double confidence = posteriori[(int) prediction];
                outcomes.put("confidence", confidence);

                logger.info(processedData + ", prediction = " + predictionStr + ", confidence = " + confidence);

                return new PredictionOutcome(confidence, this.confidenceThreshold, outcomes);
            } else {
                return new PredictionOutcome(0.0, this.confidenceThreshold, outcomes);
            }
        }

        if (task.getName().equals("Place order") || task.getName().equals("Order rejected")) { // order approved

            train(task, inputData, null);
            return new PredictionOutcome(0.0, this.confidenceThreshold, outcomes);
        } else {
            return new PredictionOutcome(0.0, this.confidenceThreshold, outcomes);
        }
    }

    /**
     * Train the random forest model using data from the human task.
     *
     * @param task       Human task data
     * @param inputData  A map containing the input attribute names as keys and the attribute values as values.
     * @param outputData A map containing the output attribute names as keys and the attribute values as values.
     */
    @Override
    public void train(Task task, Map<String, Object> inputData, Map<String, Object> outputData) {
        // the order is initiated
        if (task.getName().equals("Prepare hardware spec")) {
            String orderNumber = (String) inputData.get("orderNumber");
            if (!orderDetails.containsKey(orderNumber)) {
                OrderDetails orderDetail = new OrderDetails();
                orderDetail.brand = task.getPeopleAssignments().getPotentialOwners().get(0).getId();
                orderDetail.requestor = (String) inputData.get("requestor");
                orderDetails.put(orderNumber, orderDetail);
                logger.info("[train] Creating order number {} from user {} for a {} laptop", orderNumber, orderDetail.requestor, orderDetail.brand);
            }
            return;
        }

        // specs being generated
        if (task.getName().equals("Manager approval")) {
            String orderNumber = (String) inputData.get("orderNumber");

            orderDetails.get(orderNumber).manager = task.getPeopleAssignments().getPotentialOwners().get(0).getId();
            OrderDetails o = orderDetails.get(orderNumber);
            logger.info("[train] Sending order number {} from user {} for a {} laptop from approval with {}", orderNumber, o.requestor, o.brand, o.manager);
            return;
        }

        Map<String, Object> processedData = new HashMap<>();

        if (task.getName().equals("Place order")) { // order approved
            String orderNumber = (String) inputData.get("orderNumber");
            OrderDetails o = orderDetails.get(orderNumber);

            processedData.put("brand", o.brand);
            processedData.put("manager", o.manager);
            processedData.put("requestor", o.requestor);
            addData(processedData, true);
            logger.info("[train] Approved order number {} from user {} for a {} laptop from approval with {}", orderNumber, o.requestor, o.brand, o.manager);
        }

        if (task.getName().equals("Order rejected")) { // order rejected
            String orderNumber = (String) inputData.get("Description");
            OrderDetails o = orderDetails.get(orderNumber.substring(6, 19));
            System.out.println(orderNumber.substring(6, 19));

            processedData.put("brand", o.brand);
            processedData.put("manager", o.manager);
            processedData.put("requestor", o.requestor);
            addData(processedData, false);
            logger.info("[train] Rejected order number {} from user {} for a {} laptop from approval with {}", orderNumber, o.requestor, o.brand, o.manager);
        }

    }

    class OrderDetails {

        public String requestor;
        public String manager;
        public String brand;
        public boolean approved;

        public OrderDetails() {

        }
    }
}
