/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.dmn.jpmml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.DefaultVisitorBattery;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.InputField;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.OutputField;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.event.DMNRuntimeEventManager;
import org.kie.dmn.core.api.EvaluatorResult;
import org.kie.dmn.core.api.EvaluatorResult.ResultType;
import org.kie.dmn.core.ast.EvaluatorResultImpl;
import org.kie.dmn.core.pmml.AbstractPMMLInvocationEvaluator;
import org.kie.dmn.feel.util.EvalHelper;
import org.kie.dmn.model.api.DMNElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMNjPMMLInvocationEvaluator extends AbstractPMMLInvocationEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(DMNjPMMLInvocationEvaluator.class);
    private final ModelEvaluator<?> evaluator;

    public DMNjPMMLInvocationEvaluator(String dmnNS, DMNElement node, Resource pmmlResource, String model) throws Exception {
        super(dmnNS, node, pmmlResource, model);
        LoadingModelEvaluatorBuilder builder = new LoadingModelEvaluatorBuilder();
        try (InputStream documentStream = documentResource.getInputStream()) {
            Supplier<DefaultVisitorBattery> visitors = () -> new DefaultVisitorBattery();
            evaluator = builder.setLocatable(false)
                               .setVisitors(visitors.get())
                               .load(documentStream)
                               .build();
            evaluator.verify();
        }
    }

    @Override
    public EvaluatorResult evaluate(DMNRuntimeEventManager eventManager, DMNResult dmnr) {
        List<? extends InputField> inputFields = evaluator.getInputFields();

        Map<FieldName, FieldValue> arguments = new LinkedHashMap<>();
        for (InputField inputField : inputFields) {
            FieldName inputName = inputField.getName();
            Object rawValue = getValueForPMMLInput(dmnr, inputName.getValue());
            FieldValue inputValue = inputField.prepare(rawValue);
            LOG.trace("{}", inputName);
            LOG.trace("{}", inputValue);
            arguments.put(inputName, inputValue);
            }
        Map<FieldName, ?> results = evaluator.evaluate(arguments);
        Map<String, ?> resultsRecord = EvaluatorUtil.decodeAll(results);

        Map<String, Object> result = new HashMap<>();
        if (evaluator.getOutputFields().isEmpty()) {
            for (Entry<String, ?> kv : resultsRecord.entrySet()) {
                result.put(kv.getKey(), EvalHelper.coerceNumber(kv.getValue()));
            }
        } else {
            for (OutputField of : evaluator.getOutputFields()) {
                String outputFieldName = of.getName().getValue();
                result.put(outputFieldName, EvalHelper.coerceNumber(resultsRecord.getOrDefault(outputFieldName, null)));
            }
        }

        Object coercedResult = result.size() > 1 ? result : result.values().iterator().next();
        return new EvaluatorResultImpl(coercedResult, ResultType.SUCCESS);
    }

}
