/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.api.Action;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.inspect.ManagedModelInitializer;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.ModelManagedImplStructSchema;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

public class ManagedImplStructStrategy extends ManagedImplStructSchemaExtractionStrategySupport {

    private static final ManagedProxyFactory PROXY_FACTORY = new ManagedProxyFactory();
    private static final ModelElementState NO_OP_MODEL_ELEMENT_STATE = new ModelElementState() {
        @Override
        public MutableModelNode getBackingNode() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        public Object get(String name) {
            return null;
        }

        public void set(String name, Object value) {
        }
    };

    public ManagedImplStructStrategy(ModelSchemaAspectExtractor aspectExtractor) {
        super(aspectExtractor, null, null);
    }

    @Override
    protected <R> ModelManagedImplStructSchema<R> createSchema(final ModelSchemaExtractionContext<R> extractionContext, final ModelSchemaStore store, ModelType<R> type, Iterable<ModelProperty<?>> properties, Iterable<ModelSchemaAspect> aspects) {
        final ModelManagedImplStructSchema<R> schema = super.createSchema(extractionContext, store, type, properties, aspects);
        extractionContext.addValidator(new Action<ModelSchemaExtractionContext<R>>() {
            @Override
            public void execute(ModelSchemaExtractionContext<R> validatorModelSchemaExtractionContext) {
                ensureCanBeInstantiated(extractionContext, schema);
            }
        });
        return schema;
    }

    @Override
    protected <R> NodeInitializer createNodeInitializer(ModelManagedImplStructSchema<R> schema, ModelSchemaStore store) {
        return new ManagedModelInitializer<R>(schema, store);
    }

    private <R> void ensureCanBeInstantiated(ModelSchemaExtractionContext<R> extractionContext, ModelManagedImplStructSchema<R> schema) {
        try {
            PROXY_FACTORY.createProxy(NO_OP_MODEL_ELEMENT_STATE, schema);
        } catch (Throwable e) {
            throw new InvalidManagedModelElementTypeException(extractionContext, "instance creation failed", e);
        }
    }
}
