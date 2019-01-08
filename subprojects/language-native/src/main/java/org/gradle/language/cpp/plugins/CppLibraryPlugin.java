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

package org.gradle.language.cpp.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.internal.DefaultCppLibrary;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost;
import static org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine;

/**
 * <p>A plugin that produces a native library from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp`, public headers are located in `src/main/public` and implementation header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppLibrary} extension to the project to allow configuration of the library.</p>
 *
 * @since 4.1
 */
@Incubating
public class CppLibraryPlugin implements Plugin<Project> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;
    private final ImmutableAttributesFactory attributesFactory;
    private final TargetMachineFactory targetMachineFactory;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public CppLibraryPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, ImmutableAttributesFactory attributesFactory, TargetMachineFactory targetMachineFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.attributesFactory = attributesFactory;
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        final TaskContainer tasks = project.getTasks();
        final ObjectFactory objectFactory = project.getObjects();
        final ProviderFactory providers = project.getProviders();

        // Add the library and extension
        final DefaultCppLibrary library = componentFactory.newInstance(CppLibrary.class, DefaultCppLibrary.class, "main");
        project.getExtensions().add(CppLibrary.class, "library", library);
        project.getComponents().add(library);

        // Configure the component
        library.getBaseName().convention(project.getName());
        library.getTargetMachines().convention(useHostAsDefaultTargetMachine(targetMachineFactory));
        library.getDevelopmentBinary().convention(project.provider(new Callable<CppBinary>() {
            @Override
            public CppBinary call() throws Exception {
                return getDebugSharedHostStream().findFirst().orElse(
                        getDebugStaticHostStream().findFirst().orElse(
                                getDebugSharedStream().findFirst().orElse(
                                        getDebugStaticStream().findFirst().orElse(null))));
            }

            private Stream<CppBinary> getDebugStream() {
                return library.getBinaries().get().stream().filter(binary -> !binary.isOptimized());
            }

            private Stream<CppBinary> getDebugSharedStream() {
                return getDebugStream().filter(CppSharedLibrary.class::isInstance);
            }

            private Stream<CppBinary> getDebugSharedHostStream() {
                return getDebugSharedStream().filter(binary -> binary.getTargetPlatform().getArchitecture().equals(DefaultNativePlatform.host().getArchitecture()));
            }

            private Stream<CppBinary> getDebugStaticStream() {
                return getDebugStream().filter(CppStaticLibrary.class::isInstance);
            }

            private Stream<CppBinary> getDebugStaticHostStream() {
                return getDebugStaticStream().filter(binary -> binary.getTargetPlatform().getArchitecture().equals(DefaultNativePlatform.host().getArchitecture()));
            }
        }));

        library.getBinaries().whenElementKnown(binary -> {
            library.getMainPublication().addVariant(binary);
        });

        project.afterEvaluate(p -> {
            // TODO: make build type configurable for components
            Dimensions.libraryVariants(library.getBaseName(), library.getLinkage(), library.getTargetMachines(), objectFactory, attributesFactory,
                    providers.provider(() -> project.getGroup().toString()), providers.provider(() -> project.getVersion().toString()),
                    variantIdentity -> {
                        if (tryToBuildOnHost(variantIdentity)) {
                            ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class, variantIdentity.getTargetMachine());

                            if (variantIdentity.getLinkage().equals(Linkage.SHARED)) {
                                library.addSharedLibrary(variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                            } else {
                                library.addStaticLibrary(variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                            }
                        } else {
                            // Known, but not buildable
                            library.getMainPublication().addVariant(variantIdentity);
                        }
                    });

            // TODO - deal with more than one header dir, e.g. generated public headers
            final Configuration apiElements = library.getApiElements();
            Provider<File> publicHeaders = providers.provider(() -> {
                Set<File> files = library.getPublicHeaderDirs().getFiles();
                if (files.size() != 1) {
                    throw new UnsupportedOperationException(String.format("The C++ library plugin currently requires exactly one public header directory, however there are %d directories configured: %s", files.size(), files));
                }
                return files.iterator().next();
            });
            apiElements.getOutgoing().artifact(publicHeaders);

            project.getPluginManager().withPlugin("maven-publish", appliedPlugin -> {
                final TaskProvider<Zip> headersZip = tasks.register("cppHeaders", Zip.class, task -> {
                    task.from(library.getPublicHeaderFiles());
                    task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("headers"));
                    task.getArchiveClassifier().set("cpp-api-headers");
                    task.getArchiveFileName().set("cpp-api-headers.zip");
                });
                library.getMainPublication().addArtifact(new LazyPublishArtifact(headersZip));
            });

            library.getBinaries().realizeNow();
        });
    }
}
