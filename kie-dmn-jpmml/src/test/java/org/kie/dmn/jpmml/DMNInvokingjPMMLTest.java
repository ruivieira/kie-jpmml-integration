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

import java.math.BigDecimal;
import java.util.Map;

import org.junit.Test;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.util.DMNRuntimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DMNInvokingjPMMLTest {

    public DMNInvokingjPMMLTest() {
        super();
    }

    public static final Logger LOG = LoggerFactory.getLogger(DMNInvokingjPMMLTest.class);

    @Test
    public void testInvokeIris() {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntimeWithAdditionalResources("Invoke Iris.dmn",
                                                                                       DMNInvokingjPMMLTest.class,
                                                                                       "iris model.pmml");
        final DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/definitions/_91c67ae0-5753-4a23-ac34-1b558a006efd", "http://www.dmg.org/PMML-4_1");
        assertThat( dmnModel, notNullValue() );
        assertThat( DMNRuntimeUtil.formatMessages( dmnModel.getMessages() ), dmnModel.hasErrors(), is( false ) );

        final DMNContext emptyContext = DMNFactory.newContext();

        checkInvokeIris(runtime, dmnModel, emptyContext);
    }

    private void checkInvokeIris(final DMNRuntime runtime, final DMNModel dmnModel, final DMNContext emptyContext) {
        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, emptyContext);
        LOG.debug("{}", dmnResult);
        assertThat(DMNRuntimeUtil.formatMessages(dmnResult.getMessages()), dmnResult.hasErrors(), is(false));

        final DMNContext result = dmnResult.getContext();
        assertThat((Map<String, Object>) result.get("Decision"), hasEntry("class", "Iris-versicolor"));
    }

    @Test
    public void testInvokeIris_in1_wrong() {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntimeWithAdditionalResources("Invoke Iris_in1.dmn",
                                                                                       DMNInvokingjPMMLTest.class,
                                                                                       "iris model.pmml");
        final DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/definitions/_91c67ae0-5753-4a23-ac34-1b558a006efd", "http://www.dmg.org/PMML-4_1");
        assertThat(dmnModel, notNullValue());
        assertThat(DMNRuntimeUtil.formatMessages(dmnModel.getMessages()), dmnModel.hasErrors(), is(false));

        final DMNContext context = DMNFactory.newContext();
        context.set("in1", 99);

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);
        LOG.debug("{}", dmnResult);
        assertThat(DMNRuntimeUtil.formatMessages(dmnResult.getMessages()), dmnResult.hasErrors(), is(true));
        assertTrue(dmnResult.getMessages().stream().anyMatch(m -> m.getSourceId().equals("in1"))); // ... 'in1': the dependency value '99' is not allowed by the declared type (DMNType{ iris : sepal_length })
    }

    @Test
    public void testInvokeIris_in1_ok() {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntimeWithAdditionalResources("Invoke Iris_in1.dmn",
                                                                                       DMNInvokingjPMMLTest.class,
                                                                                       "iris model.pmml");
        final DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/definitions/_91c67ae0-5753-4a23-ac34-1b558a006efd", "http://www.dmg.org/PMML-4_1");
        assertThat(dmnModel, notNullValue());
        assertThat(DMNRuntimeUtil.formatMessages(dmnModel.getMessages()), dmnModel.hasErrors(), is(false));

        final DMNContext context = DMNFactory.newContext();
        context.set("in1", 4.3);

        checkInvokeIris(runtime, dmnModel, context);
    }

    @Test
    public void testDummyInteger() {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntimeWithAdditionalResources("dummy_integer.dmn",
                                                                                       DMNInvokingjPMMLTest.class,
                                                                                       "dummy_integer.pmml");
        final DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/definitions/_d9065b95-bc37-41dc-8566-8191af7b7867", "Drawing 1");
        assertThat(dmnModel, notNullValue());
        assertThat(DMNRuntimeUtil.formatMessages(dmnModel.getMessages()), dmnModel.hasErrors(), is(false));

        final DMNContext emptyContext = DMNFactory.newContext();

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, emptyContext);
        LOG.debug("{}", dmnResult);
        assertThat(DMNRuntimeUtil.formatMessages(dmnResult.getMessages()), dmnResult.hasErrors(), is(false));

        final DMNContext result = dmnResult.getContext();
        assertThat((Map<String, Object>) result.get("hardcoded"), hasEntry("result", new BigDecimal(3)));
    }
}
