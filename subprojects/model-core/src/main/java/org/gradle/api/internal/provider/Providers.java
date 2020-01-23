/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

public class Providers {
    public static final String NULL_TRANSFORMER_RESULT = "Transformer for this provider returned a null value.";

    private static final NoValueProvider NULL_PROVIDER = new NoValueProvider();

    public static final Provider<Boolean> TRUE = of(true);
    public static final Provider<Boolean> FALSE = of(false);

    public static <T> ScalarSupplier<T> noValue() {
        return Cast.uncheckedCast(NULL_PROVIDER);
    }

    public static <T> ScalarSupplier<T> fixedValue(T value) {
        return new FixedValueProvider<>(value);
    }

    public static <T> ScalarSupplier<T> fixedValue(DisplayName owner, T value, Class<T> targetType, ValueSanitizer<T> sanitizer) {
        value = sanitizer.sanitize(value);
        if (!targetType.isInstance(value)) {
            throw new IllegalArgumentException(String.format("Cannot set the value of %s of type %s using an instance of type %s.", owner.getDisplayName(), targetType.getName(), value.getClass().getName()));
        }
        return new FixedValueProvider<>(value);
    }

    public static <T> ScalarSupplier<T> nullableValue(@Nullable T value) {
        if (value == null) {
            return noValue();
        } else {
            return fixedValue(value);
        }
    }

    public static <T> ProviderInternal<T> notDefined() {
        return Cast.uncheckedCast(NULL_PROVIDER);
    }

    public static <T> ProviderInternal<T> of(T value) {
        return new FixedValueProvider<>(value);
    }

    public static <T> ProviderInternal<T> internal(final Provider<T> value) {
        return Cast.uncheckedCast(value);
    }

    public static <T> ProviderInternal<T> ofNullable(@Nullable T value) {
        if (value == null) {
            return notDefined();
        } else {
            return of(value);
        }
    }

    public static class FixedValueProvider<T> extends AbstractProviderWithValue<T> implements ScalarSupplier<T> {
        private final T value;

        FixedValueProvider(T value) {
            this.value = value;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return Cast.uncheckedCast(value.getClass());
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public ProviderInternal<T> asProvider() {
            return this;
        }

        @Override
        public ScalarSupplier<T> withFinalValue() {
            return this;
        }

        @Override
        public String toString() {
            return String.format("fixed(%s, %s)", getType(), value);
        }
    }

    private static class NoValueProvider extends AbstractMinimalProvider<Object> implements ScalarSupplier<Object> {
        @Override
        public Value<?> calculateValue() {
            return new Missing<>();
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Nullable
        @Override
        public Class<Object> getType() {
            return null;
        }

        @Override
        protected Value<?> calculateOwnValue() {
            return Value.missing();
        }

        @Override
        public <S> ProviderInternal<S> map(Transformer<? extends S, ? super Object> transformer) {
            return Cast.uncheckedCast(this);
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public ScalarSupplier<Object> asSupplier(DisplayName owner, Class<? super Object> targetType, ValueSanitizer<? super Object> sanitizer) {
            return this;
        }

        @Override
        public ProviderInternal<Object> asProvider() {
            return this;
        }

        @Override
        public ScalarSupplier<Object> withFinalValue() {
            return this;
        }

        @Override
        public Provider<Object> orElse(Object value) {
            return Providers.of(value);
        }

        @Override
        public Provider<Object> orElse(Provider<?> provider) {
            return Cast.uncheckedCast(provider);
        }

        @Override
        public String toString() {
            return "undefined";
        }
    }
}
