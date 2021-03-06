/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeJavaAbstractTestCaseConfigurationProducer}. */
@RunWith(JUnit4.class)
public class BlazeJavaAbstractTestCaseConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testNonAbstractClassIgnored() {
    PsiFile javaFile =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/TestClass.java"),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            "public class TestClass {",
            "  @org.junit.Test",
            "  public void testMethod1() {}",
            "  @org.junit.Test",
            "  public void testMethod2() {}",
            "}");

    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];
    assertThat(javaClass).isNotNull();

    ConfigurationContext context = createContextFromPsi(javaClass);
    ConfigurationFromContext fromContext =
        new BlazeJavaAbstractTestCaseConfigurationProducer()
            .createConfigurationFromContext(context);
    assertThat(fromContext).isNull();
  }

  @Test
  public void testConfigurationCreatedFromAbstractClass() {
    workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));
    PsiFile abstractClassFile =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/AbstractTestCase.java"),
            "package com.google.test;",
            "public abstract class AbstractTestCase {}");

    createAndIndexFile(
        new WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "import com.google.test.AbstractTestCase;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass extends AbstractTestCase {",
        "  @org.junit.Test",
        "  public void testMethod1() {}",
        "  @org.junit.Test",
        "  public void testMethod2() {}",
        "}");

    PsiClass javaClass = ((PsiClassOwner) abstractClassFile).getClasses()[0];
    assertThat(javaClass).isNotNull();

    ConfigurationContext context = createContextFromPsi(abstractClassFile);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BlazeJavaAbstractTestCaseConfigurationProducer.class))
        .isTrue();
    assertThat(fromContext.getSourceElement()).isEqualTo(javaClass);

    RunConfiguration config = fromContext.getConfiguration();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);
    BlazeCommandRunConfiguration blazeConfig = (BlazeCommandRunConfiguration) config;
    assertThat(blazeConfig.getTarget()).isNull();
    assertThat(blazeConfig.getName()).isEqualTo("AbstractTestCase");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    BlazeJavaAbstractTestCaseConfigurationProducer.chooseSubclass(
        fromContext, context, EmptyRunnable.INSTANCE);

    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromString("//java/com/google/test:TestClass"));
    assertThat(getTestFilterContents(blazeConfig))
        .isEqualTo(BlazeFlags.TEST_FILTER + "=com.google.test.TestClass#");
  }

  @Test
  public void testConfigurationCreatedFromMethodInAbstractClass() {
    PsiFile abstractClassFile =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/AbstractTestCase.java"),
            "package com.google.test;",
            "public abstract class AbstractTestCase {",
            "  @org.junit.Test",
            "  public void testMethod() {}",
            "}");

    createAndIndexFile(
        new WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "import com.google.test.AbstractTestCase;",
        "import org.junit.runner.RunWith;",
        "import org.junit.runners.JUnit4;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass extends AbstractTestCase {}");

    PsiClass javaClass = ((PsiClassOwner) abstractClassFile).getClasses()[0];
    PsiMethod method = PsiUtils.findFirstChildOfClassRecursive(javaClass, PsiMethod.class);
    assertThat(method).isNotNull();

    ConfigurationContext context = createContextFromPsi(method);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BlazeJavaAbstractTestCaseConfigurationProducer.class))
        .isTrue();
    assertThat(fromContext.getSourceElement()).isEqualTo(method);

    RunConfiguration config = fromContext.getConfiguration();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);
    BlazeCommandRunConfiguration blazeConfig = (BlazeCommandRunConfiguration) config;
    assertThat(blazeConfig.getTarget()).isNull();
    assertThat(blazeConfig.getName()).isEqualTo("AbstractTestCase.testMethod");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    BlazeJavaAbstractTestCaseConfigurationProducer.chooseSubclass(
        fromContext, context, EmptyRunnable.INSTANCE);

    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromString("//java/com/google/test:TestClass"));
    assertThat(getTestFilterContents(blazeConfig))
        .isEqualTo(BlazeFlags.TEST_FILTER + "=com.google.test.TestClass#testMethod$");
  }
}
