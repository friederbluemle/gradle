/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.Maps;
import org.gradle.internal.Cast;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class PropertyAccessorExtractionContext {
    private final Collection<Method> declaringMethods;
    private final Method mostSpecificDeclaration;
    private final boolean declaredInManagedType;
    private final boolean declaredAsAbstract;
    private final Map<Class<? extends Annotation>, Annotation> annotations;

    public PropertyAccessorExtractionContext(Collection<Method> declaringMethods) {
        this.declaringMethods = declaringMethods;
        this.mostSpecificDeclaration = ModelSchemaUtils.findMostSpecificMethod(declaringMethods);
        this.declaredInManagedType = ModelSchemaUtils.isMethodDeclaredInManagedType(declaringMethods);
        this.declaredAsAbstract = Modifier.isAbstract(this.mostSpecificDeclaration.getModifiers());
        this.annotations = collectAnnotations(declaringMethods);
    }

    private Map<Class<? extends Annotation>, Annotation> collectAnnotations(Collection<Method> methods) {
        Map<Class<? extends Annotation>, Annotation> annotations = Maps.newLinkedHashMap();
        for (Method method : methods) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                // Make sure more specific annotation doesn't get overwritten with less specific one
                if (!annotations.containsKey(annotation.annotationType())) {
                    annotations.put(annotation.annotationType(), annotation);
                }
            }
        }
        return Collections.unmodifiableMap(annotations);
    }

    public Collection<Method> getDeclaringMethods() {
        return declaringMethods;
    }

    public Method getMostSpecificDeclaration() {
        return mostSpecificDeclaration;
    }

    public boolean isDeclaredInManagedType() {
        return declaredInManagedType;
    }

    public boolean isDeclaredAsAbstract() {
        return declaredAsAbstract;
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotations.containsKey(annotationType);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return Cast.uncheckedCast(annotations.get(annotationType));
    }

    public Collection<Annotation> getAnnotations() {
        return annotations.values();
    }

}
